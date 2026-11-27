package com.simpleaibrowser.sab.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.SabFileEntry
import com.simpleaibrowser.sab.core.browser.StorageLocationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

data class ExportResult(
    val localUri: String?,
    val publicUri: String?
)

class DownloadFileSystem(private val context: Context) {

    companion object {
        const val META_DIR_NAME = ".sab_meta"
        const val PUBLIC_ROOT_FOLDER = "Simple AI Browser"
        private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

        fun sanitizeFileName(rawName: String, fallback: String = "download.bin"): String {
            val trimmed = rawName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .trim()
                .replace(ILLEGAL_NAME_CHARS, "_")
                .replace(Regex("\\s+"), " ")
                .trim('.', ' ')
            if (trimmed.isBlank() || trimmed == "." || trimmed == "..") {
                return fallback
            }
            if (trimmed.length <= 160) return trimmed
            val dotIdx = trimmed.lastIndexOf('.')
            return if (dotIdx in 1 until trimmed.length - 1) {
                val ext = trimmed.substring(dotIdx).take(16)
                val base = trimmed.substring(0, (160 - ext.length).coerceAtLeast(8))
                "$base$ext"
            } else {
                trimmed.take(160)
            }
        }

        fun sanitizeRelativeSubpath(raw: String): String {
            if (raw.isBlank()) return ""
            return raw.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .joinToString("/") { sanitizeFileName(it, "folder") }
        }
    }

    fun vaultRoot(): File {
        val externalDownloads = runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull()
        val root = externalDownloads ?: File(context.filesDir, "sab_downloads")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun metadataDir(): File {
        val dir = File(vaultRoot(), META_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun torrentFilesDir(taskId: String): File {
        val safeId = sanitizeFileName(taskId, "torrent")
        val dir = File(metadataDir(), "torrents/$safeId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun resolveFolder(relativeSubpath: String): File {
        val clean = sanitizeRelativeSubpath(relativeSubpath)
        val root = vaultRoot()
        val target = if (clean.isBlank()) root else File(root, clean)
        if (!target.canonicalPath.startsWith(root.canonicalPath)) {
            return root
        }
        if (!target.exists()) target.mkdirs()
        return target
    }

    fun resolveTargetFile(
        fileName: String,
        subfolder: String = "",
        ensureUnique: Boolean = true
    ): File {
        val parent = resolveFolder(subfolder)
        val cleanName = sanitizeFileName(fileName)
        return if (ensureUnique) uniqueFile(parent, cleanName) else File(parent, cleanName)
    }

    fun uniqueFile(parent: File, desiredName: String): File {
        val clean = sanitizeFileName(desiredName)
        var candidate = File(parent, clean)
        if (!candidate.exists()) return candidate
        val dot = clean.lastIndexOf('.')
        val stem = if (dot > 0) clean.substring(0, dot) else clean
        val ext = if (dot > 0) clean.substring(dot) else ""
        var counter = 1
        while (candidate.exists() && counter < 5000) {
            candidate = File(parent, "$stem ($counter)$ext")
            counter++
        }
        return candidate
    }

    fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun mimeTypeFor(fileName: String, explicitMime: String? = null): String {
        val cleanExplicit = explicitMime?.substringBefore(';')?.trim()?.takeIf {
            it.isNotBlank() && it != "application/octet-stream"
        }
        if (cleanExplicit != null) return cleanExplicit
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext == "torrent") return "application/x-bittorrent"
        if (ext == "mkv") return "video/x-matroska"
        if (ext == "webp") return "image/webp"
        if (ext == "avif") return "image/avif"
        if (ext == "apk") return "application/vnd.android.package-archive"
        val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return guessed ?: explicitMime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    }

    suspend fun computeSha256(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) return@withContext null
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    suspend fun exportToConfiguredDestination(
        source: File,
        subfolder: String,
        mimeType: String?,
        settings: DownloadManagerSettings
    ): ExportResult = withContext(Dispatchers.IO) {
        if (!source.exists()) return@withContext ExportResult(null, null)

        val localUri = if (source.isFile) {
            runCatching { fileProviderUri(source).toString() }.getOrNull()
        } else {
            null
        }

        val cleanSub = sanitizeRelativeSubpath(subfolder)
        val exportedPublicUri: String? = when (settings.storageLocationMode) {
            StorageLocationMode.APP_WORKSPACE -> null
            StorageLocationMode.CUSTOM_TREE -> {
                val treeRaw = settings.customTreeUri
                val customUri = if (!treeRaw.isNullOrBlank()) {
                    runCatching {
                        exportToCustomTree(Uri.parse(treeRaw), source, cleanSub, mimeType)?.toString()
                    }.getOrNull()
                } else null
                customUri ?: runCatching {
                    exportToPublicDownloads(source, cleanSub, mimeType)?.toString()
                }.getOrNull()
            }
            StorageLocationMode.PUBLIC_DOWNLOADS -> {
                runCatching {
                    exportToPublicDownloads(source, cleanSub, mimeType)?.toString()
                }.getOrNull()
            }
        }

        ExportResult(
            localUri = exportedPublicUri ?: localUri,
            publicUri = exportedPublicUri
        )
    }

    fun exportToPublicDownloads(
        source: File,
        relativeSubfolder: String = "",
        mimeType: String? = null
    ): Uri? {
        if (!source.exists()) return null
        if (source.isDirectory) {
            var firstUri: Uri? = null
            val cleanSub = sanitizeRelativeSubpath(
                if (relativeSubfolder.isBlank()) source.name else "$relativeSubfolder/${source.name}"
            )
            source.listFiles()?.forEach { child ->
                val childUri = if (child.isDirectory) {
                    exportToPublicDownloads(child, cleanSub, null)
                } else {
                    exportSingleFileToMediaStore(child, cleanSub, mimeTypeFor(child.name))
                }
                if (firstUri == null && childUri != null) firstUri = childUri
            }
            return firstUri
        }
        return exportSingleFileToMediaStore(source, relativeSubfolder, mimeTypeFor(source.name, mimeType))
    }

    private fun exportSingleFileToMediaStore(
        file: File,
        relativeSubfolder: String,
        resolvedMime: String
    ): Uri? {
        if (!file.exists() || !file.isFile) return null
        val resolver = context.contentResolver
        val cleanSub = sanitizeRelativeSubpath(relativeSubfolder)
        val relativePath = buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            append('/')
            append(PUBLIC_ROOT_FOLDER)
            if (cleanSub.isNotBlank()) {
                append('/')
                append(cleanSub)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(file).use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            } ?: error("Failed to open MediaStore output stream")

            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(targetUri, done, null, null)
            targetUri
        } catch (t: Throwable) {
            runCatching { resolver.delete(targetUri, null, null) }
            null
        }
    }

    fun exportToCustomTree(
        treeUri: Uri,
        source: File,
        relativeSubfolder: String = "",
        mimeType: String? = null
    ): Uri? {
        if (!source.exists()) return null
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        var targetParentDocId = ensureTreeSubfolders(treeUri, rootDocId, sanitizeRelativeSubpath(relativeSubfolder))
            ?: return null

        if (source.isDirectory) {
            targetParentDocId = ensureTreeChildDirectory(treeUri, targetParentDocId, source.name) ?: return null
            var firstUri: Uri? = null
            source.listFiles()?.forEach { child ->
                val uri = if (child.isDirectory) {
                    exportToCustomTree(
                        treeUri = treeUri,
                        source = child,
                        relativeSubfolder = sanitizeRelativeSubpath(
                            listOf(relativeSubfolder, source.name).filter { it.isNotBlank() }.joinToString("/")
                        ),
                        mimeType = null
                    )
                } else {
                    writeFileToTreeDoc(treeUri, targetParentDocId, child, mimeTypeFor(child.name))
                }
                if (firstUri == null && uri != null) firstUri = uri
            }
            return firstUri
        }
        return writeFileToTreeDoc(treeUri, targetParentDocId, source, mimeTypeFor(source.name, mimeType))
    }

    private fun ensureTreeSubfolders(treeUri: Uri, startDocId: String, subpath: String): String? {
        if (subpath.isBlank()) return startDocId
        var current = startDocId
        for (segment in subpath.split('/')) {
            if (segment.isBlank()) continue
            current = ensureTreeChildDirectory(treeUri, current, segment) ?: return null
        }
        return current
    }

    private fun ensureTreeChildDirectory(treeUri: Uri, parentDocId: String, dirName: String): String? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol)
                    if (name == dirName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return cursor.getString(idCol)
                    }
                }
            }
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = runCatching {
            DocumentsContract.createDocument(
                resolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                dirName
            )
        }.getOrNull() ?: return null
        return runCatching { DocumentsContract.getDocumentId(createdUri) }.getOrNull()
    }

    private fun writeFileToTreeDoc(
        treeUri: Uri,
        parentDocId: String,
        source: File,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = runCatching {
            DocumentsContract.createDocument(resolver, parentUri, mimeType, source.name)
        }.getOrNull() ?: return null

        return try {
            resolver.openOutputStream(createdUri)?.use { out ->
                FileInputStream(source).use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            } ?: error("Cannot open SAF document stream")
            createdUri
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            null
        }
    }

    suspend fun copyContentUriToFile(sourceUri: Uri, destFile: File): Long = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        var total = 0L
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                }
                out.flush()
            }
        } ?: error("Cannot open selected file")
        total
    }

    fun resolveDisplayName(uri: Uri, fallback: String = "file.torrent"): String {
        if (uri.scheme == "file") {
            return sanitizeFileName(uri.lastPathSegment ?: fallback, fallback)
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return sanitizeFileName(name, fallback)
                    }
                }
            }
        }
        return sanitizeFileName(uri.lastPathSegment ?: fallback, fallback)
    }

    suspend fun listDirectory(relativePath: String): List<SabFileEntry> = withContext(Dispatchers.IO) {
        val dir = resolveFolder(relativePath)
        val children = dir.listFiles().orEmpty()
            .filterNot { it.name == META_DIR_NAME || it.name.endsWith(".sabpart") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { -it.lastModified() }, { it.name.lowercase(Locale.US) }))

        children.map { file ->
            val count = if (file.isDirectory) {
                file.listFiles()?.count { !it.name.startsWith(".") } ?: 0
            } else 0
            val mime = if (file.isDirectory) "inode/directory" else mimeTypeFor(file.name)
            val kind = if (file.isDirectory) DownloadKind.FILE else DownloadKind.infer(file.name, mime, file.name)
            SabFileEntry(
                name = file.name,
                absolutePath = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) computeDirectorySize(file) else file.length(),
                modifiedAt = file.lastModified(),
                mimeType = mime,
                kind = kind,
                childCount = count
            )
        }
    }

    private fun computeDirectorySize(dir: File): Long {
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) computeDirectorySize(child) else child.length()
        }
        return total
    }

    suspend fun createFolder(relativeParent: String, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = sanitizeFileName(folderName, "")
            require(cleanName.isNotBlank()) { "Folder name cannot be empty" }
            val parent = resolveFolder(relativeParent)
            val target = File(parent, cleanName)
            require(target.canonicalPath.startsWith(vaultRoot().canonicalPath)) { "Invalid path" }
            if (!target.exists() && !target.mkdirs()) {
                error("Could not create folder $cleanName")
            }
            target
        }
    }

    suspend fun renameEntry(absolutePath: String, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = sanitizeFileName(newName, "")
            require(clean.isNotBlank()) { "Name cannot be empty" }
            val src = File(absolutePath)
            require(src.exists()) { "File not found" }
            require(src.canonicalPath.startsWith(vaultRoot().canonicalPath)) { "Access denied" }
            val dst = File(src.parentFile ?: vaultRoot(), clean)
            require(!dst.exists()) { "A file or folder named \"$clean\" already exists" }
            check(src.renameTo(dst)) { "Rename failed" }
            dst
        }
    }

    suspend fun deleteEntry(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(absolutePath)
        val root = vaultRoot()
        if (!target.exists() || !target.canonicalPath.startsWith(root.canonicalPath) || target.canonicalPath == root.canonicalPath) {
            return@withContext false
        }
        if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    suspend fun storageSummary(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val root = vaultRoot()
        val used = root.listFiles()
            ?.filterNot { it.name == META_DIR_NAME }
            ?.sumOf { if (it.isDirectory) computeDirectorySize(it) else it.length() }
            ?: 0L
        val available = runCatching {
            val stat = StatFs(root.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
        used to available
    }
}
