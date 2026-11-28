package com.simpleaibrowser.sab.ui.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.DetectedPageMedia
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.DownloadRequest
import com.simpleaibrowser.sab.core.browser.DownloadSegmentState
import com.simpleaibrowser.sab.core.browser.DownloadSourceType
import com.simpleaibrowser.sab.core.browser.DownloadTaskStatus
import com.simpleaibrowser.sab.core.browser.SabFileEntry
import com.simpleaibrowser.sab.core.browser.SabViewerRequest
import com.simpleaibrowser.sab.core.browser.TorrentFileEntry
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BrowserRepository
import com.simpleaibrowser.sab.data.DownloadEntity
import com.simpleaibrowser.sab.downloads.DownloadFileSystem
import com.simpleaibrowser.sab.downloads.HttpTransferOutcome
import com.simpleaibrowser.sab.downloads.LibTorrentEngine
import com.simpleaibrowser.sab.downloads.SabDownloadService
import com.simpleaibrowser.sab.downloads.SegmentedHttpDownloader
import com.simpleaibrowser.sab.downloads.TorrentProgressUpdate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val DL_COORD_TAG = "SabDownloadCoord"

data class DownloadAddDraft(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long = -1L,
    val sourceType: DownloadSourceType = DownloadSourceType.HTTP,
    val referrerUrl: String? = null,
    val stagedCacheFilePath: String? = null,
    val subfolder: String = "",
    val connectionsCount: Int = 4,
    val wifiOnly: Boolean = false,
    val sequentialTorrent: Boolean = false
)

class DownloadManagerCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: BrowserRepository,
    private val onToast: (String) -> Unit
) {
    /**
     * Every background download/torrent/explorer task runs here. A crash inside any task must
     * never take the app down (this was the "app crashes when downloading a torrent" bug:
     * an export/DB/IO failure inside a bare scope.launch hit the default uncaught-exception
     * handler and killed the process). Failures are logged, surfaced as a toast, and the
     * affected task stays resumable in the manager.
     */
    private val managedScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, err ->
            Log.e(DL_COORD_TAG, "Unhandled download task failure", err)
            onToast("Download error: ${err.message ?: err.javaClass.simpleName}")
        }
    )
    val fileSystem = DownloadFileSystem(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val segmentedDownloader = SegmentedHttpDownloader(httpClient)

    private val torrentEngine = LibTorrentEngine(
        fileSystem = fileSystem,
        scope = scope,
        onUpdate = { update -> handleTorrentUpdate(update) },
        onFinished = { update -> handleTorrentFinished(update) }
    )

    private val _currentSettings = MutableStateFlow(DownloadManagerSettings())
    private val _pendingDraft = MutableStateFlow<DownloadAddDraft?>(null)
    val pendingDraft: StateFlow<DownloadAddDraft?> = _pendingDraft.asStateFlow()

    private val _detectedPageMedia = MutableStateFlow<List<DetectedPageMedia>>(emptyList())
    val detectedPageMedia: StateFlow<List<DetectedPageMedia>> = _detectedPageMedia.asStateFlow()

    private val _isScanningMedia = MutableStateFlow(false)
    val isScanningMedia: StateFlow<Boolean> = _isScanningMedia.asStateFlow()

    private val _currentExplorerPath = MutableStateFlow("")
    val currentExplorerPath: StateFlow<String> = _currentExplorerPath.asStateFlow()

    private val _explorerEntries = MutableStateFlow<List<SabFileEntry>>(emptyList())
    val explorerEntries: StateFlow<List<SabFileEntry>> = _explorerEntries.asStateFlow()

    private val _storageSummary = MutableStateFlow(0L to 0L)
    val storageSummary: StateFlow<Pair<Long, Long>> = _storageSummary.asStateFlow()

    private val _verifyingSha256TaskId = MutableStateFlow<String?>(null)
    val verifyingSha256TaskId: StateFlow<String?> = _verifyingSha256TaskId.asStateFlow()

    private val activeHttpJobs = ConcurrentHashMap<String, Job>()
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()
    private val lastDbPersistMs = ConcurrentHashMap<String, Long>()
    private val lastServiceSyncMs = ConcurrentHashMap<String, Long>()
    private val torrentFinishedHandled = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var concurrencySemaphore = Semaphore(3)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        managedScope.launch {
            var previousMaxConcurrent = _currentSettings.value.maxConcurrentDownloads
            repository.settings.collect { browserSettings ->
                val dl = browserSettings.downloadSettings
                _currentSettings.value = dl
                if (dl.maxConcurrentDownloads != previousMaxConcurrent) {
                    previousMaxConcurrent = dl.maxConcurrentDownloads
                    concurrencySemaphore = Semaphore(dl.maxConcurrentDownloads.coerceIn(1, 6))
                }
                torrentEngine.applySettings(dl)
            }
        }
        registerConnectivityWatcher()
        managedScope.launch {
            recoverInterruptedTasksOnBoot()
            refreshExplorer()
        }
    }

    private fun registerConnectivityWatcher() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                maybeAutoResumeWaitingTasks()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                maybeAutoResumeWaitingTasks()
            }
        }
        runCatching {
            cm.registerNetworkCallback(req, cb)
            networkCallback = cb
        }
    }

    private fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
    }

    private fun maybeAutoResumeWaitingTasks() {
        val dlSettings = _currentSettings.value
        if (!dlSettings.autoResumeOnReconnect) return
        val metered = isNetworkMetered()
        managedScope.launch {
            val all = repository.getAllDownloads()
            all.forEach { entity ->
                val st = DownloadTaskStatus.fromId(entity.status)
                if (st == DownloadTaskStatus.WAITING_WIFI && !metered) {
                    resumeTask(entity)
                }
            }
        }
    }

    private suspend fun recoverInterruptedTasksOnBoot() {
        val all = repository.getAllDownloads()
        val dlSettings = runCatching { repository.settings.first().downloadSettings }
            .getOrDefault(_currentSettings.value)
        all.forEach { entity ->
            val st = DownloadTaskStatus.fromId(entity.status)
            if (st == DownloadTaskStatus.QUEUED ||
                st == DownloadTaskStatus.CONNECTING ||
                st == DownloadTaskStatus.FETCHING_METADATA ||
                st == DownloadTaskStatus.DOWNLOADING ||
                st == DownloadTaskStatus.SEEDING
            ) {
                val kind = DownloadKind.fromId(entity.kind)
                if (dlSettings.autoResumeOnReconnect && !(entity.wifiOnly && isNetworkMetered())) {
                    if (kind.isTorrent) {
                        resumeTask(entity)
                    } else {
                        repository.updateDownloadStatus(
                            entity.taskId,
                            DownloadTaskStatus.PAUSED.id,
                            "Interrupted on last exit — tap Resume"
                        )
                    }
                } else {
                    val pausedStatus = if (entity.wifiOnly && isNetworkMetered()) {
                        DownloadTaskStatus.WAITING_WIFI
                    } else {
                        DownloadTaskStatus.PAUSED
                    }
                    repository.updateDownloadStatus(entity.taskId, pausedStatus.id)
                }
            }
        }
    }

    fun dismissDraft() {
        _pendingDraft.value = null
    }

    fun handleBrowserDownloadRequest(request: DownloadRequest) {
        val s = _currentSettings.value
        val inferredKind = DownloadKind.infer(request.fileName, request.mimeType, request.url)
        val defaultSubfolder = if (s.autoOrganizeByType) inferredKind.folderName else ""
        val draft = DownloadAddDraft(
            url = request.url,
            fileName = DownloadFileSystem.sanitizeFileName(
                request.fileName,
                if (inferredKind == DownloadKind.MAGNET) "magnet.torrent" else "download.bin"
            ),
            mimeType = request.mimeType,
            contentLength = request.contentLength,
            sourceType = when {
                inferredKind == DownloadKind.MAGNET -> DownloadSourceType.MAGNET
                inferredKind == DownloadKind.TORRENT && !request.stagedCacheFilePath.isNullOrBlank() ->
                    DownloadSourceType.TORRENT_FILE
                else -> request.sourceType
            },
            referrerUrl = request.referrerUrl,
            stagedCacheFilePath = request.stagedCacheFilePath,
            subfolder = defaultSubfolder,
            connectionsCount = s.defaultConnectionsPerDownload,
            wifiOnly = s.wifiOnlyDownloads,
            sequentialTorrent = s.torrentSequentialDefault
        )

        if (s.askBeforeDownloading && request.localUri.isNullOrBlank()) {
            _pendingDraft.value = draft
        } else {
            startFromDraft(draft, legacyLocalUri = request.localUri)
        }
    }

    fun openAddDownloadDialog(prefillUrl: String = "") {
        val s = _currentSettings.value
        val clean = prefillUrl.trim()
        val kind = DownloadKind.infer(clean, null, clean)
        _pendingDraft.value = DownloadAddDraft(
            url = clean,
            fileName = if (clean.isNotBlank()) UrlResolver.guessFileName(clean, null) else "",
            mimeType = null,
            sourceType = if (kind == DownloadKind.MAGNET) DownloadSourceType.MAGNET else DownloadSourceType.HTTP,
            subfolder = if (s.autoOrganizeByType) kind.folderName else "",
            connectionsCount = s.defaultConnectionsPerDownload,
            wifiOnly = s.wifiOnlyDownloads,
            sequentialTorrent = s.torrentSequentialDefault
        )
    }

    fun startFromDraft(draft: DownloadAddDraft, legacyLocalUri: String? = null) {
        _pendingDraft.value = null
        managedScope.launch {
            val s = _currentSettings.value
            val cleanUrl = draft.url.trim()
            val inferredKind = DownloadKind.infer(draft.fileName, draft.mimeType, cleanUrl)
            val resolvedName = DownloadFileSystem.sanitizeFileName(
                draft.fileName.ifBlank { UrlResolver.guessFileName(cleanUrl, draft.mimeType) },
                if (inferredKind == DownloadKind.MAGNET) "magnet_${System.currentTimeMillis()}" else "download.bin"
            )
            val cleanSubfolder = DownloadFileSystem.sanitizeRelativeSubpath(
                draft.subfolder.ifBlank { if (s.autoOrganizeByType) inferredKind.folderName else "" }
            )

            // 1. Pre-saved local URI (legacy/compat completion path)
            if (!legacyLocalUri.isNullOrBlank()) {
                val entity = DownloadEntity(
                    fileName = resolvedName,
                    url = cleanUrl,
                    mimeType = fileSystem.mimeTypeFor(resolvedName, draft.mimeType),
                    sizeBytes = draft.contentLength,
                    downloadedBytes = draft.contentLength.coerceAtLeast(0L),
                    localUri = legacyLocalUri,
                    publicUri = legacyLocalUri,
                    kind = inferredKind.id,
                    status = DownloadTaskStatus.COMPLETED.id,
                    subfolder = cleanSubfolder,
                    completedAt = System.currentTimeMillis()
                )
                repository.upsertDownloadTask(entity)
                onToast("Saved $resolvedName")
                return@launch
            }

            // 2. Magnet URI -> BitTorrent
            if (draft.sourceType == DownloadSourceType.MAGNET ||
                cleanUrl.startsWith("magnet:", ignoreCase = true)
            ) {
                enqueueMagnetTask(
                    magnetUri = cleanUrl,
                    preferredName = draft.fileName,
                    subfolder = cleanSubfolder,
                    wifiOnly = draft.wifiOnly,
                    sequential = draft.sequentialTorrent
                )
                return@launch
            }

            // 3. Staged .torrent file (or local .torrent path)
            if (draft.sourceType == DownloadSourceType.TORRENT_FILE ||
                (!draft.stagedCacheFilePath.isNullOrBlank() && inferredKind == DownloadKind.TORRENT)
            ) {
                val staged = draft.stagedCacheFilePath?.let { File(it) }
                if (staged != null && staged.exists()) {
                    enqueueTorrentFileTask(
                        torrentFile = staged,
                        sourceUrl = cleanUrl.ifBlank { staged.name },
                        subfolder = cleanSubfolder,
                        wifiOnly = draft.wifiOnly,
                        sequential = draft.sequentialTorrent
                    )
                    return@launch
                }
            }

            // 4. Staged open WebResponse stream (blob:/POST/authenticated download from GeckoView)
            if (draft.sourceType == DownloadSourceType.OPEN_STREAM && !draft.stagedCacheFilePath.isNullOrBlank()) {
                val staged = File(draft.stagedCacheFilePath)
                if (staged.exists()) {
                    finalizeStagedStreamDownload(
                        stagedFile = staged,
                        fileName = resolvedName,
                        url = cleanUrl,
                        mimeType = draft.mimeType,
                        subfolder = cleanSubfolder
                    )
                    return@launch
                }
            }

            // 5. Standard HTTP / HTTPS (supports remote .torrent links too)
            val parsedUri = runCatching { Uri.parse(cleanUrl) }.getOrNull()
            val scheme = parsedUri?.scheme?.lowercase(Locale.US)
            if (parsedUri == null || (scheme != "http" && scheme != "https")) {
                onToast("Enter a valid http://, https://, or magnet: link")
                return@launch
            }

            // If user entered/clicked a .torrent HTTP link, fetch the .torrent metadata & start torrent transfer
            if (inferredKind == DownloadKind.TORRENT) {
                fetchRemoteTorrentAndStart(
                    url = cleanUrl,
                    fileName = resolvedName,
                    referrer = draft.referrerUrl,
                    subfolder = cleanSubfolder,
                    wifiOnly = draft.wifiOnly,
                    sequential = draft.sequentialTorrent
                )
                return@launch
            }

            val destFile = fileSystem.resolveTargetFile(
                fileName = resolvedName,
                subfolder = cleanSubfolder,
                ensureUnique = true
            )
            val entity = DownloadEntity(
                fileName = destFile.name,
                url = cleanUrl,
                mimeType = fileSystem.mimeTypeFor(destFile.name, draft.mimeType),
                sizeBytes = draft.contentLength,
                downloadedBytes = 0L,
                kind = inferredKind.id,
                status = if (draft.wifiOnly && isNetworkMetered()) {
                    DownloadTaskStatus.WAITING_WIFI.id
                } else {
                    DownloadTaskStatus.QUEUED.id
                },
                connectionsCount = draft.connectionsCount.coerceIn(1, 16),
                stagingPath = destFile.absolutePath,
                subfolder = cleanSubfolder,
                wifiOnly = draft.wifiOnly
            )
            repository.upsertDownloadTask(entity)
            if (DownloadTaskStatus.fromId(entity.status) == DownloadTaskStatus.WAITING_WIFI) {
                onToast("${entity.fileName} waiting for Wi-Fi")
            } else {
                onToast("Added ${entity.fileName}")
                launchHttpTransfer(entity)
            }
        }
    }

    fun startTorrentFromPickedUri(contentUri: Uri) {
        managedScope.launch {
            runCatching {
                val displayName = fileSystem.resolveDisplayName(contentUri, "picked.torrent")
                val tmp = File(fileSystem.metadataDir(), "picked_${System.currentTimeMillis()}_$displayName")
                fileSystem.copyContentUriToFile(contentUri, tmp)
                val s = _currentSettings.value
                val subfolder = if (s.autoOrganizeByType) DownloadKind.TORRENT.folderName else ""
                enqueueTorrentFileTask(
                    torrentFile = tmp,
                    sourceUrl = contentUri.toString(),
                    subfolder = subfolder,
                    wifiOnly = s.wifiOnlyDownloads,
                    sequential = s.torrentSequentialDefault
                )
            }.onFailure { err ->
                onToast("Could not open .torrent file: ${err.message}")
            }
        }
    }

    private suspend fun finalizeStagedStreamDownload(
        stagedFile: File,
        fileName: String,
        url: String,
        mimeType: String?,
        subfolder: String
    ) {
        val taskId = UUID.randomUUID().toString()
        val destFile = fileSystem.resolveTargetFile(fileName, subfolder, ensureUnique = true)
        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()
            if (!stagedFile.renameTo(destFile)) {
                stagedFile.copyTo(destFile, overwrite = true)
                stagedFile.delete()
            }
        }
        val total = destFile.length()
        val resolvedMime = fileSystem.mimeTypeFor(destFile.name, mimeType)
        val kind = DownloadKind.infer(destFile.name, resolvedMime, url)
        val sha = fileSystem.computeSha256(destFile)
        val export = fileSystem.exportToConfiguredDestination(
            source = destFile,
            subfolder = subfolder,
            mimeType = resolvedMime,
            settings = _currentSettings.value
        )
        val entity = DownloadEntity(
            taskId = taskId,
            fileName = destFile.name,
            url = url,
            mimeType = resolvedMime,
            sizeBytes = total,
            downloadedBytes = total,
            kind = kind.id,
            status = DownloadTaskStatus.COMPLETED.id,
            stagingPath = destFile.absolutePath,
            localUri = export.localUri,
            publicUri = export.publicUri,
            subfolder = subfolder,
            sha256 = sha,
            completedAt = System.currentTimeMillis()
        )
        repository.upsertDownloadTask(entity)
        refreshExplorer()
        notifyTaskFinished(entity, success = true, detail = "Saved to ${destinationLabel()}")
        onToast("Downloaded ${destFile.name}")
    }

    private suspend fun fetchRemoteTorrentAndStart(
        url: String,
        fileName: String,
        referrer: String?,
        subfolder: String,
        wifiOnly: Boolean,
        sequential: Boolean
    ) {
        onToast("Fetching torrent metadata...")
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                val reqBuilder = Request.Builder().url(url).get()
                val ua = _currentSettings.value.customUserAgent
                if (ua.isNotBlank()) reqBuilder.header("User-Agent", ua)
                if (!referrer.isNullOrBlank()) reqBuilder.header("Referer", referrer)
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val bytes = resp.body?.bytes() ?: error("Empty torrent response")
                    val target = File(fileSystem.metadataDir(), "remote_${System.currentTimeMillis()}_$fileName")
                    target.writeBytes(bytes)
                    target
                }
            }
        }
        fetched.onSuccess { torrentFile ->
            enqueueTorrentFileTask(
                torrentFile = torrentFile,
                sourceUrl = url,
                subfolder = subfolder,
                wifiOnly = wifiOnly,
                sequential = sequential
            )
        }.onFailure { err ->
            onToast("Failed to fetch .torrent: ${err.message}")
        }
    }

    private suspend fun enqueueMagnetTask(
        magnetUri: String,
        preferredName: String,
        subfolder: String,
        wifiOnly: Boolean,
        sequential: Boolean
    ) {
        val (infoHash, parsedName) = torrentEngine.parseMagnetQuickInfo(magnetUri)
        if (!infoHash.isNullOrBlank()) {
            val existing = repository.findDownloadByInfoHash(infoHash)
            if (existing != null) {
                resumeTask(existing)
                onToast("Resuming existing torrent: ${existing.fileName}")
                return
            }
        }

        val taskId = UUID.randomUUID().toString()
        val displayName = DownloadFileSystem.sanitizeFileName(
            preferredName.ifBlank { parsedName.orEmpty() },
            infoHash?.let { "magnet_${it.take(8)}" } ?: "Magnet Download"
        )
        val saveDir = fileSystem.resolveFolder(
            listOf(subfolder, displayName).filter { it.isNotBlank() }.joinToString("/")
        )
        val waitingWifi = wifiOnly && isNetworkMetered()

        val entity = DownloadEntity(
            taskId = taskId,
            fileName = displayName,
            url = magnetUri,
            mimeType = "application/x-bittorrent",
            sizeBytes = -1L,
            kind = DownloadKind.MAGNET.id,
            status = if (waitingWifi) DownloadTaskStatus.WAITING_WIFI.id else DownloadTaskStatus.FETCHING_METADATA.id,
            stagingPath = saveDir.absolutePath,
            subfolder = subfolder,
            torrentInfoHash = infoHash,
            sequentialTorrent = sequential,
            wifiOnly = wifiOnly
        )
        repository.upsertDownloadTask(entity)

        if (waitingWifi) {
            onToast("Magnet queued until Wi-Fi is available")
            return
        }

        val res = withContext(Dispatchers.IO) {
            torrentEngine.startMagnet(magnetUri, saveDir, sequential, _currentSettings.value)
        }
        res.onSuccess { hash ->
            if (hash != infoHash) {
                repository.updateDownloadRuntimeState(
                    taskId = taskId,
                    fileName = displayName,
                    sizeBytes = -1L,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    status = DownloadTaskStatus.FETCHING_METADATA.id,
                    supportsRange = true,
                    etag = null,
                    segmentsJson = null,
                    torrentPeers = 0,
                    torrentSeeds = 0,
                    torrentFilesJson = null,
                    torrentInfoHash = hash,
                    errorMessage = null
                )
            }
            onToast("Fetching magnet metadata...")
            syncForegroundServiceSummary()
        }.onFailure { err ->
            repository.updateDownloadStatus(taskId, DownloadTaskStatus.FAILED.id, err.message ?: "Magnet failed")
            onToast("Torrent error: ${err.message}")
        }
    }

    private suspend fun enqueueTorrentFileTask(
        torrentFile: File,
        sourceUrl: String,
        subfolder: String,
        wifiOnly: Boolean,
        sequential: Boolean
    ) {
        val taskId = UUID.randomUUID().toString()
        val storedTorrent = File(fileSystem.metadataDir(), "task_$taskId.torrent")
        withContext(Dispatchers.IO) {
            torrentFile.copyTo(storedTorrent, overwrite = true)
            if (torrentFile.name.startsWith("picked_") || torrentFile.name.startsWith("remote_")) {
                torrentFile.delete()
            }
        }

        val parseInfo = withContext(Dispatchers.IO) {
            runCatching { org.libtorrent4j.TorrentInfo(storedTorrent) }
        }
        val info = parseInfo.getOrNull()
        val hash = info?.infoHash()?.toHex()?.lowercase(Locale.US)
        if (!hash.isNullOrBlank()) {
            val existing = repository.findDownloadByInfoHash(hash)
            if (existing != null) {
                resumeTask(existing)
                onToast("Resuming existing torrent: ${existing.fileName}")
                return
            }
        }

        val name = DownloadFileSystem.sanitizeFileName(
            info?.name()?.takeIf { it.isNotBlank() } ?: storedTorrent.nameWithoutExtension,
            "torrent_${System.currentTimeMillis()}"
        )
        val saveDir = fileSystem.resolveFolder(
            listOf(subfolder, name).filter { it.isNotBlank() }.joinToString("/")
        )
        val fileEntries = if (info != null) torrentEngine.extractFileEntries(info) else emptyList()
        val waitingWifi = wifiOnly && isNetworkMetered()

        val entity = DownloadEntity(
            taskId = taskId,
            fileName = name,
            url = sourceUrl,
            mimeType = "application/x-bittorrent",
            sizeBytes = info?.totalSize() ?: -1L,
            kind = DownloadKind.TORRENT.id,
            status = if (waitingWifi) DownloadTaskStatus.WAITING_WIFI.id else DownloadTaskStatus.CONNECTING.id,
            stagingPath = saveDir.absolutePath,
            subfolder = subfolder,
            torrentInfoHash = hash,
            torrentFilesJson = if (fileEntries.isNotEmpty()) TorrentFileEntry.toJson(fileEntries) else null,
            sequentialTorrent = sequential,
            wifiOnly = wifiOnly
        )
        repository.upsertDownloadTask(entity)

        if (waitingWifi) {
            onToast("$name queued until Wi-Fi")
            return
        }

        val started = withContext(Dispatchers.IO) {
            torrentEngine.startTorrentFile(
                torrentFile = storedTorrent,
                taskId = taskId,
                saveDir = saveDir,
                sequential = sequential,
                existingEntries = fileEntries,
                settings = _currentSettings.value
            )
        }
        started.onSuccess {
            onToast("Torrent started: $name")
            syncForegroundServiceSummary()
        }.onFailure { err ->
            repository.updateDownloadStatus(taskId, DownloadTaskStatus.FAILED.id, err.message ?: "Torrent start failed")
            onToast("Torrent error: ${err.message}")
        }
    }

    private fun launchHttpTransfer(entity: DownloadEntity) {
        if (activeHttpJobs[entity.taskId]?.isActive == true) return
        pauseFlags[entity.taskId] = false
        cancelFlags[entity.taskId] = false

        val job = managedScope.launch {
            concurrencySemaphore.withPermit {
                val fresh = repository.findDownloadByTaskId(entity.taskId) ?: return@withPermit
                if (pauseFlags[fresh.taskId] == true || cancelFlags[fresh.taskId] == true) return@withPermit

                repository.updateDownloadStatus(fresh.taskId, DownloadTaskStatus.CONNECTING.id)
                syncForegroundServiceSummary()

                val targetFile = File(fresh.stagingPath.ifBlank {
                    fileSystem.resolveTargetFile(fresh.fileName, fresh.subfolder, ensureUnique = true).absolutePath
                })
                val existingSegs = DownloadSegmentState.fromJson(fresh.segmentsJson)
                val dlSettings = _currentSettings.value

                val outcome = segmentedDownloader.download(
                    url = fresh.url,
                    targetFile = targetFile,
                    desiredConnections = fresh.connectionsCount.coerceIn(1, 16),
                    existingSegments = existingSegs,
                    knownTotalBytes = fresh.sizeBytes,
                    knownEtag = fresh.etag,
                    userAgent = dlSettings.customUserAgent.takeIf { it.isNotBlank() },
                    referrer = null,
                    shouldPause = { pauseFlags[fresh.taskId] == true },
                    shouldCancel = { cancelFlags[fresh.taskId] == true },
                    isMeteredBlocked = { fresh.wifiOnly && isNetworkMetered() },
                    speedLimitKbpsProvider = { _currentSettings.value.speedLimitKbps },
                    onProgress = { progress ->
                        val now = System.currentTimeMillis()
                        val lastPersist = lastDbPersistMs[fresh.taskId] ?: 0L
                        if (now - lastPersist >= 650L) {
                            lastDbPersistMs[fresh.taskId] = now
                            repository.updateDownloadRuntimeState(
                                taskId = fresh.taskId,
                                fileName = progress.fileName,
                                sizeBytes = progress.totalBytes,
                                downloadedBytes = progress.downloadedBytes,
                                speedBytesPerSec = progress.speedBytesPerSec,
                                etaSeconds = progress.etaSeconds,
                                status = DownloadTaskStatus.DOWNLOADING.id,
                                supportsRange = progress.supportsRange,
                                etag = progress.etag,
                                segmentsJson = DownloadSegmentState.toJson(progress.segments).takeIf { progress.segments.isNotEmpty() },
                                torrentPeers = 0,
                                torrentSeeds = 0,
                                torrentFilesJson = null,
                                torrentInfoHash = null,
                                errorMessage = null
                            )
                            syncForegroundServiceSummary()
                        }
                    }
                )

                when (outcome) {
                    is HttpTransferOutcome.Completed -> {
                        val mime = fileSystem.mimeTypeFor(outcome.file.name, outcome.mimeType ?: fresh.mimeType)
                        val sha = fileSystem.computeSha256(outcome.file)
                        val export = fileSystem.exportToConfiguredDestination(
                            source = outcome.file,
                            subfolder = fresh.subfolder,
                            mimeType = mime,
                            settings = _currentSettings.value
                        )
                        repository.markDownloadCompleted(
                            taskId = fresh.taskId,
                            totalBytes = outcome.totalBytes,
                            localUri = export.localUri,
                            publicUri = export.publicUri,
                            stagingPath = outcome.file.absolutePath,
                            sha256 = sha
                        )
                        refreshExplorer()
                        notifyTaskFinished(
                            fresh.copy(fileName = outcome.file.name),
                            success = true,
                            detail = "Saved to ${destinationLabel()}"
                        )
                        onToast("Downloaded ${outcome.file.name}")
                    }
                    is HttpTransferOutcome.Paused -> {
                        val nextStatus = if (outcome.waitingWifi) {
                            DownloadTaskStatus.WAITING_WIFI
                        } else {
                            DownloadTaskStatus.PAUSED
                        }
                        repository.updateDownloadRuntimeState(
                            taskId = fresh.taskId,
                            fileName = outcome.progress.fileName,
                            sizeBytes = outcome.progress.totalBytes,
                            downloadedBytes = outcome.progress.downloadedBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = -1L,
                            status = nextStatus.id,
                            supportsRange = outcome.progress.supportsRange,
                            etag = outcome.progress.etag,
                            segmentsJson = DownloadSegmentState.toJson(outcome.progress.segments)
                                .takeIf { outcome.progress.segments.isNotEmpty() },
                            torrentPeers = 0,
                            torrentSeeds = 0,
                            torrentFilesJson = null,
                            torrentInfoHash = null,
                            errorMessage = if (outcome.waitingWifi) "Waiting for Wi-Fi" else null
                        )
                    }
                    HttpTransferOutcome.Cancelled -> {
                        repository.updateDownloadStatus(fresh.taskId, DownloadTaskStatus.CANCELLED.id, "Cancelled by user")
                    }
                    is HttpTransferOutcome.Failed -> {
                        val p = outcome.progress
                        if (p != null) {
                            repository.updateDownloadRuntimeState(
                                taskId = fresh.taskId,
                                fileName = p.fileName,
                                sizeBytes = p.totalBytes,
                                downloadedBytes = p.downloadedBytes,
                                speedBytesPerSec = 0L,
                                etaSeconds = -1L,
                                status = DownloadTaskStatus.FAILED.id,
                                supportsRange = p.supportsRange,
                                etag = p.etag,
                                segmentsJson = DownloadSegmentState.toJson(p.segments).takeIf { p.segments.isNotEmpty() },
                                torrentPeers = 0,
                                torrentSeeds = 0,
                                torrentFilesJson = null,
                                torrentInfoHash = null,
                                errorMessage = outcome.message
                            )
                        } else {
                            repository.updateDownloadStatus(fresh.taskId, DownloadTaskStatus.FAILED.id, outcome.message)
                        }
                        notifyTaskFinished(fresh, success = false, detail = outcome.message)
                    }
                }
                syncForegroundServiceSummary()
            }
        }
        activeHttpJobs[entity.taskId] = job
        job.invokeOnCompletion { activeHttpJobs.remove(entity.taskId) }
    }

    private fun handleTorrentUpdate(update: TorrentProgressUpdate) {
        managedScope.launch {
            val entity = repository.findDownloadByInfoHash(update.infoHash) ?: return@launch
            if (entity.status == DownloadTaskStatus.CANCELLED.id) return@launch
            val currentStatus = DownloadTaskStatus.fromId(entity.status)
            if (currentStatus == DownloadTaskStatus.COMPLETED && update.status != DownloadTaskStatus.SEEDING) {
                return@launch
            }
            val now = System.currentTimeMillis()
            val last = lastDbPersistMs[entity.taskId] ?: 0L
            val statusChanged = entity.status != update.status.id
            if (!statusChanged && now - last < 800L) return@launch
            lastDbPersistMs[entity.taskId] = now

            val eta = if (update.downloadRateBytesPerSec > 0L && update.totalBytes > update.downloadedBytes) {
                (update.totalBytes - update.downloadedBytes) / update.downloadRateBytesPerSec
            } else -1L

            repository.updateDownloadRuntimeState(
                taskId = entity.taskId,
                fileName = update.name.ifBlank { entity.fileName },
                sizeBytes = update.totalBytes,
                downloadedBytes = update.downloadedBytes,
                speedBytesPerSec = update.downloadRateBytesPerSec,
                etaSeconds = eta,
                status = update.status.id,
                supportsRange = true,
                etag = null,
                segmentsJson = null,
                torrentPeers = update.peers,
                torrentSeeds = update.seeds,
                torrentFilesJson = if (update.files.isNotEmpty()) TorrentFileEntry.toJson(update.files) else entity.torrentFilesJson,
                torrentInfoHash = update.infoHash,
                errorMessage = update.errorMessage
            )
            syncForegroundServiceSummary()
        }
    }

    private fun handleTorrentFinished(update: TorrentProgressUpdate) {
        if (!torrentFinishedHandled.add(update.infoHash)) return
        managedScope.launch {
            val entity = repository.findDownloadByInfoHash(update.infoHash) ?: return@launch
            val saveRoot = File(update.savePath.ifBlank { entity.stagingPath })
            val mime = if (saveRoot.isDirectory) "inode/directory" else fileSystem.mimeTypeFor(saveRoot.name)
            val export = fileSystem.exportToConfiguredDestination(
                source = saveRoot,
                subfolder = entity.subfolder,
                mimeType = mime,
                settings = _currentSettings.value
            )
            val finalStatus = if (_currentSettings.value.torrentSeedAfterComplete) {
                DownloadTaskStatus.SEEDING.id
            } else {
                DownloadTaskStatus.COMPLETED.id
            }
            repository.markDownloadCompleted(
                taskId = entity.taskId,
                totalBytes = update.totalBytes.coerceAtLeast(update.downloadedBytes),
                localUri = export.localUri,
                publicUri = export.publicUri,
                stagingPath = saveRoot.absolutePath,
                sha256 = if (saveRoot.isFile) fileSystem.computeSha256(saveRoot) else null
            )
            if (finalStatus == DownloadTaskStatus.SEEDING.id) {
                repository.updateDownloadStatus(entity.taskId, DownloadTaskStatus.SEEDING.id, null)
            }
            refreshExplorer()
            notifyTaskFinished(
                entity.copy(fileName = update.name.ifBlank { entity.fileName }),
                success = true,
                detail = "Torrent completed • ${destinationLabel()}"
            )
            onToast("Torrent finished: ${update.name.ifBlank { entity.fileName }}")
            syncForegroundServiceSummary()
        }
    }

    fun pauseTask(entity: DownloadEntity) {
        val kind = DownloadKind.fromId(entity.kind)
        managedScope.launch {
            if (kind.isTorrent) {
                entity.torrentInfoHash?.let { torrentEngine.pause(it) }
                repository.updateDownloadStatus(entity.taskId, DownloadTaskStatus.PAUSED.id)
            } else {
                pauseFlags[entity.taskId] = true
                repository.updateDownloadStatus(entity.taskId, DownloadTaskStatus.PAUSED.id)
            }
            syncForegroundServiceSummary()
        }
    }

    fun resumeTask(entity: DownloadEntity) {
        val kind = DownloadKind.fromId(entity.kind)
        managedScope.launch {
            if (entity.wifiOnly && isNetworkMetered()) {
                repository.updateDownloadStatus(
                    entity.taskId,
                    DownloadTaskStatus.WAITING_WIFI.id,
                    "Waiting for Wi-Fi connection"
                )
                onToast("${entity.fileName} waiting for Wi-Fi")
                return@launch
            }
            if (kind.isTorrent) {
                val hash = entity.torrentInfoHash
                val storedTorrent = File(fileSystem.metadataDir(), "task_${entity.taskId}.torrent")
                val saveDir = File(entity.stagingPath.ifBlank { fileSystem.vaultRoot().absolutePath })
                if (storedTorrent.exists()) {
                    val entries = TorrentFileEntry.fromJson(entity.torrentFilesJson)
                    torrentEngine.startTorrentFile(
                        torrentFile = storedTorrent,
                        taskId = entity.taskId,
                        saveDir = saveDir,
                        sequential = entity.sequentialTorrent,
                        existingEntries = entries,
                        settings = _currentSettings.value
                    )
                } else if (entity.url.startsWith("magnet:", ignoreCase = true)) {
                    torrentEngine.startMagnet(
                        magnetUri = entity.url,
                        saveDir = saveDir,
                        sequential = entity.sequentialTorrent,
                        settings = _currentSettings.value
                    )
                } else if (!hash.isNullOrBlank()) {
                    torrentEngine.resume(hash, entity.sequentialTorrent)
                }
                repository.updateDownloadStatus(entity.taskId, DownloadTaskStatus.CONNECTING.id, null)
                syncForegroundServiceSummary()
            } else {
                cancelFlags[entity.taskId] = false
                pauseFlags[entity.taskId] = false
                launchHttpTransfer(entity)
            }
        }
    }

    fun cancelTask(entity: DownloadEntity) {
        val kind = DownloadKind.fromId(entity.kind)
        managedScope.launch {
            if (kind.isTorrent) {
                entity.torrentInfoHash?.let { torrentEngine.remove(it) }
            } else {
                cancelFlags[entity.taskId] = true
                activeHttpJobs[entity.taskId]?.cancel()
            }
            repository.updateDownloadStatus(entity.taskId, DownloadTaskStatus.CANCELLED.id, "Cancelled")
            syncForegroundServiceSummary()
        }
    }

    fun pauseAllActive() {
        managedScope.launch {
            val all = repository.getAllDownloads()
            all.filter { DownloadTaskStatus.fromId(it.status).canPause }.forEach { pauseTask(it) }
            onToast("Paused active downloads")
        }
    }

    fun resumeAllPaused() {
        managedScope.launch {
            val all = repository.getAllDownloads()
            all.filter { DownloadTaskStatus.fromId(it.status).canResume }.forEach { resumeTask(it) }
        }
    }

    fun toggleTorrentSequential(entity: DownloadEntity) {
        val next = !entity.sequentialTorrent
        managedScope.launch {
            repository.setTorrentSequential(entity.taskId, next)
            entity.torrentInfoHash?.let { torrentEngine.setSequential(it, next) }
            onToast(if (next) "Sequential streaming order enabled" else "Standard piece order restored")
        }
    }

    fun toggleTorrentFileSelected(entity: DownloadEntity, fileIndex: Int) {
        val list = TorrentFileEntry.fromJson(entity.torrentFilesJson).toMutableList()
        val idx = list.indexOfFirst { it.index == fileIndex }
        if (idx == -1) return
        val current = list[idx]
        val nextPrio = if (current.isSelected) 0 else 4
        list[idx] = current.copy(priority = nextPrio)
        managedScope.launch {
            repository.updateTorrentFilesJson(entity.taskId, TorrentFileEntry.toJson(list))
            entity.torrentInfoHash?.let { torrentEngine.applyFilePriorities(it, list) }
        }
    }

    fun deleteDownloadRecord(entity: DownloadEntity, deleteFiles: Boolean) {
        managedScope.launch {
            val kind = DownloadKind.fromId(entity.kind)
            if (kind.isTorrent) {
                entity.torrentInfoHash?.let { torrentEngine.remove(it) }
            } else {
                cancelFlags[entity.taskId] = true
                activeHttpJobs[entity.taskId]?.cancel()
            }
            if (deleteFiles && entity.stagingPath.isNotBlank()) {
                fileSystem.deleteEntry(entity.stagingPath)
                runCatching { File("${entity.stagingPath}.sabpart").delete() }
                runCatching { File(fileSystem.metadataDir(), "task_${entity.taskId}.torrent").delete() }
            }
            repository.deleteDownloadRecord(entity.id)
            refreshExplorer()
            syncForegroundServiceSummary()
        }
    }

    fun clearFinished() {
        managedScope.launch {
            repository.clearFinishedDownloads()
            onToast("Cleared finished download history")
        }
    }

    fun verifySha256(entity: DownloadEntity) {
        val path = entity.stagingPath
        if (path.isBlank()) {
            onToast("No local staged file to hash")
            return
        }
        managedScope.launch {
            _verifyingSha256TaskId.value = entity.taskId
            val file = File(path)
            val hash = if (file.isFile) fileSystem.computeSha256(file) else null
            _verifyingSha256TaskId.value = null
            if (hash != null) {
                repository.markDownloadCompleted(
                    taskId = entity.taskId,
                    totalBytes = entity.sizeBytes.coerceAtLeast(file.length()),
                    localUri = entity.localUri,
                    publicUri = entity.publicUri,
                    stagingPath = path,
                    sha256 = hash
                )
                onToast("SHA-256 verified")
            } else {
                onToast("Folder checksum is computed per file")
            }
        }
    }

    fun exportDownloadToPublicDownloads(entity: DownloadEntity) {
        val path = entity.stagingPath
        if (path.isBlank() || !File(path).exists()) {
            onToast("File not found in SAB vault")
            return
        }
        managedScope.launch {
            val src = File(path)
            val uri = withContext(Dispatchers.IO) {
                fileSystem.exportToPublicDownloads(src, entity.subfolder, entity.mimeType)
            }
            if (uri != null) {
                repository.updateDownloadLocations(
                    taskId = entity.taskId,
                    fileName = entity.fileName,
                    stagingPath = entity.stagingPath,
                    localUri = uri.toString(),
                    publicUri = uri.toString()
                )
                onToast("Exported to Downloads/Simple AI Browser")
            } else {
                onToast("Could not export file")
            }
        }
    }

    fun openDownloadedFile(
        context: Context,
        entity: DownloadEntity,
        onOpenBuiltIn: (SabViewerRequest) -> Unit = {}
    ) {
        val stageFile = entity.stagingPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (stageFile != null && stageFile.exists() && stageFile.isDirectory) {
            val rootPath = fileSystem.vaultRoot().absolutePath
            val rel = stageFile.absolutePath.removePrefix(rootPath).trimStart('/')
            navigateExplorerTo(rel)
            onToast("Opened torrent folder in Files tab")
            return
        }

        val mime = fileSystem.mimeTypeFor(entity.fileName, entity.mimeType)
        val stagePath = stageFile?.takeIf { it.exists() && it.isFile }?.absolutePath
        val providerUri = stageFile?.takeIf { it.exists() && it.isFile }?.let {
            runCatching { fileSystem.fileProviderUri(it) }.getOrNull()
        }
        val uri = entity.publicUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: entity.localUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: providerUri
            ?: runCatching {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (entity.systemDownloadId > 0L) dm?.getUriForDownloadedFile(entity.systemDownloadId) else null
            }.getOrNull()

        if (UrlResolver.isPdfTarget(entity.fileName, mime) && (stagePath != null || uri != null)) {
            onOpenBuiltIn(
                SabViewerRequest.Pdf(
                    title = entity.fileName,
                    localFilePath = stagePath,
                    contentUri = uri?.toString(),
                    sourceUrl = entity.url.takeIf { it.startsWith("http", ignoreCase = true) },
                    shareUri = (providerUri ?: uri)?.toString()
                )
            )
            return
        }

        val (isMedia, isVideo) = UrlResolver.classifyPlayableMedia(entity.fileName, mime)
        if (isMedia) {
            val playUri = stageFile?.takeIf { it.exists() && it.isFile }?.let { Uri.fromFile(it).toString() }
                ?: uri?.toString()
                ?: entity.url.takeIf { it.startsWith("http", ignoreCase = true) }
            if (!playUri.isNullOrBlank()) {
                onOpenBuiltIn(
                    SabViewerRequest.Media(
                        title = entity.fileName,
                        uri = playUri,
                        isVideo = isVideo,
                        mimeType = mime,
                        shareUri = (providerUri ?: uri)?.toString(),
                        fallbackExternalUri = (providerUri ?: uri)?.toString()
                    )
                )
                return
            }
        }

        if (uri != null) {
            openUriExternally(context, uri.toString(), mime, entity.fileName)
            return
        }
        val fallback = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(fallback) }.isFailure) {
            onToast("No app installed to open ${entity.fileName}")
        }
    }

    fun shareDownloadedFile(context: Context, entity: DownloadEntity) {
        val stageFile = entity.stagingPath.takeIf { it.isNotBlank() }?.let { File(it) }
        val shareUri = stageFile?.takeIf { it.exists() && it.isFile }?.let {
            runCatching { fileSystem.fileProviderUri(it) }.getOrNull()
        } ?: entity.publicUri?.let { runCatching { Uri.parse(it) }.getOrNull() }

        if (shareUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = fileSystem.mimeTypeFor(entity.fileName, entity.mimeType)
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${entity.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entity.url)
            }
            context.startActivity(Intent.createChooser(textIntent, "Share Link").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // --- Page Media Grabber ---

    fun scanCurrentPageMedia(session: BrowserTabSession?) {
        if (session == null || session.state.value.isStartPage) {
            _detectedPageMedia.value = emptyList()
            return
        }
        managedScope.launch {
            _isScanningMedia.value = true
            val found = runCatching { session.detectPageMedia() }.getOrDefault(emptyList())
            _detectedPageMedia.value = found
            _isScanningMedia.value = false
        }
    }

    fun clearDetectedMedia() {
        _detectedPageMedia.value = emptyList()
    }

    fun downloadDetectedMedia(media: DetectedPageMedia) {
        val guessed = UrlResolver.guessFileName(media.url, media.mimeType)
        handleBrowserDownloadRequest(
            DownloadRequest(
                url = media.url,
                fileName = guessed,
                mimeType = media.mimeType,
                sourceType = if (media.kind == DownloadKind.MAGNET) DownloadSourceType.MAGNET else DownloadSourceType.HTTP
            )
        )
    }

    // --- In-App File Explorer ---

    fun refreshExplorer() {
        managedScope.launch {
            val entries = fileSystem.listDirectory(_currentExplorerPath.value)
            _explorerEntries.value = entries
            _storageSummary.value = fileSystem.storageSummary()
        }
    }

    fun navigateExplorerTo(relativePath: String) {
        _currentExplorerPath.value = DownloadFileSystem.sanitizeRelativeSubpath(relativePath)
        refreshExplorer()
    }

    fun navigateExplorerUp() {
        val current = _currentExplorerPath.value
        if (current.isBlank()) return
        val parent = current.substringBeforeLast('/', "")
        _currentExplorerPath.value = parent
        refreshExplorer()
    }

    fun createExplorerFolder(name: String) {
        managedScope.launch {
            fileSystem.createFolder(_currentExplorerPath.value, name)
                .onSuccess {
                    refreshExplorer()
                    onToast("Created folder ${it.name}")
                }
                .onFailure {
                    onToast(it.message ?: "Could not create folder")
                }
        }
    }

    fun renameExplorerEntry(entry: SabFileEntry, newName: String) {
        managedScope.launch {
            fileSystem.renameEntry(entry.absolutePath, newName)
                .onSuccess { renamed ->
                    refreshExplorer()
                    onToast("Renamed to ${renamed.name}")
                }
                .onFailure {
                    onToast(it.message ?: "Rename failed")
                }
        }
    }

    fun deleteExplorerEntry(entry: SabFileEntry) {
        managedScope.launch {
            val deleted = fileSystem.deleteEntry(entry.absolutePath)
            if (deleted) {
                refreshExplorer()
                onToast("Deleted ${entry.name}")
            }
        }
    }

    fun exportVaultFileToPublicDownloads(entry: SabFileEntry) {
        managedScope.launch {
            val uri = withContext(Dispatchers.IO) {
                fileSystem.exportToPublicDownloads(File(entry.absolutePath), "", entry.mimeType)
            }
            onToast(
                if (uri != null) "Exported ${entry.name} to Downloads/Simple AI Browser"
                else "Export failed"
            )
        }
    }

    fun openVaultFile(
        context: Context,
        entry: SabFileEntry,
        onOpenBuiltIn: (SabViewerRequest) -> Unit = {}
    ) {
        val file = File(entry.absolutePath)
        if (entry.isDirectory) {
            val root = fileSystem.vaultRoot().absolutePath
            val rel = file.absolutePath.removePrefix(root).trimStart('/')
            navigateExplorerTo(rel)
            return
        }
        val uri = runCatching { fileSystem.fileProviderUri(file) }.getOrNull()
        val mime = fileSystem.mimeTypeFor(entry.name, entry.mimeType)

        if (UrlResolver.isPdfTarget(entry.name, mime) && file.exists()) {
            onOpenBuiltIn(
                SabViewerRequest.Pdf(
                    title = entry.name,
                    localFilePath = file.absolutePath,
                    contentUri = uri?.toString(),
                    shareUri = uri?.toString()
                )
            )
            return
        }

        val (isMedia, isVideo) = UrlResolver.classifyPlayableMedia(entry.name, mime)
        if (isMedia && file.exists()) {
            onOpenBuiltIn(
                SabViewerRequest.Media(
                    title = entry.name,
                    uri = Uri.fromFile(file).toString(),
                    isVideo = isVideo,
                    mimeType = mime,
                    shareUri = uri?.toString(),
                    fallbackExternalUri = uri?.toString()
                )
            )
            return
        }

        if (uri != null) {
            openUriExternally(context, uri.toString(), mime, entry.name)
        }
    }

    fun openUriExternally(context: Context, uriString: String, mimeType: String?, title: String) {
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val resolvedUri = if (parsed.scheme == "file" && parsed.path != null) {
            runCatching { fileSystem.fileProviderUri(File(parsed.path!!)) }.getOrDefault(parsed)
        } else {
            parsed
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (!mimeType.isNullOrBlank()) {
                setDataAndType(resolvedUri, mimeType)
            } else {
                data = resolvedUri
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isFailure) {
            onToast("No app found to open $title")
        }
    }

    fun shareUriOrText(context: Context, target: String, title: String) {
        val parsed = runCatching { Uri.parse(target) }.getOrNull()
        val shareUri = when {
            parsed?.scheme == "content" -> parsed
            parsed?.scheme == "file" && parsed.path != null ->
                runCatching { fileSystem.fileProviderUri(File(parsed.path!!)) }.getOrNull()
            else -> null
        }
        if (shareUri != null) {
            val mime = context.contentResolver.getType(shareUri) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, target)
            }
            context.startActivity(Intent.createChooser(textIntent, "Share $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun shareVaultFile(context: Context, entry: SabFileEntry) {
        val file = File(entry.absolutePath)
        if (!file.isFile) return
        val uri = runCatching { fileSystem.fileProviderUri(file) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entry.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${entry.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun setCustomFolderUri(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val label = treeUri.lastPathSegment?.substringAfter(':')?.ifBlank { "Custom Folder" } ?: "Custom Folder"
        managedScope.launch {
            repository.updateDownloadSettings { current ->
                current.copy(
                    storageLocationMode = com.simpleaibrowser.sab.core.browser.StorageLocationMode.CUSTOM_TREE,
                    customTreeUri = treeUri.toString(),
                    customTreeLabel = label
                )
            }
            onToast("Download folder set: $label")
        }
    }

    private fun destinationLabel(): String {
        val s = _currentSettings.value
        return when (s.storageLocationMode) {
            com.simpleaibrowser.sab.core.browser.StorageLocationMode.PUBLIC_DOWNLOADS -> "Downloads/Simple AI Browser"
            com.simpleaibrowser.sab.core.browser.StorageLocationMode.CUSTOM_TREE -> s.customTreeLabel ?: "Custom Folder"
            com.simpleaibrowser.sab.core.browser.StorageLocationMode.APP_WORKSPACE -> "SAB File Vault"
        }
    }

    private fun notifyTaskFinished(entity: DownloadEntity, success: Boolean, detail: String) {
        if (!_currentSettings.value.notifyOnComplete && success) return
        SabDownloadService.notifyCompletion(
            context = context,
            notificationId = 5000 + abs(entity.taskId.hashCode() % 1000),
            fileName = entity.fileName,
            success = success,
            detail = detail
        )
    }

    private suspend fun syncForegroundServiceSummary() {
        val now = System.currentTimeMillis()
        val last = lastServiceSyncMs["fg"] ?: 0L
        val all = repository.getAllDownloads()
        val active = all.filter { DownloadTaskStatus.fromId(it.status).isActive }
        if (active.isEmpty()) {
            lastServiceSyncMs["fg"] = now
            SabDownloadService.syncForeground(context, 0, "", 0)
            return
        }
        if (now - last < 900L) return
        lastServiceSyncMs["fg"] = now

        val totalSize = active.filter { it.sizeBytes > 0L }.sumOf { it.sizeBytes }
        val totalDone = active.filter { it.sizeBytes > 0L }.sumOf { it.downloadedBytes }
        val pct = if (totalSize > 0L) ((totalDone * 100L) / totalSize).toInt().coerceIn(0, 100) else -1
        val summary = if (active.size == 1) {
            active.first().fileName
        } else {
            "${active.first().fileName} + ${active.size - 1} more"
        }
        SabDownloadService.syncForeground(context, active.size, summary, pct)
    }

    fun release() {
        networkCallback?.let { cb ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        activeHttpJobs.values.forEach { it.cancel() }
        activeHttpJobs.clear()
        torrentEngine.shutdown()
    }
}
