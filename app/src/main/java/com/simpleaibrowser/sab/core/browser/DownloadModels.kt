package com.simpleaibrowser.sab.core.browser

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DownloadSourceType(val id: String) {
    HTTP("http"),
    TORRENT_FILE("torrent_file"),
    MAGNET("magnet"),
    OPEN_STREAM("open_stream");

    companion object {
        fun fromId(id: String): DownloadSourceType = entries.firstOrNull { it.id == id } ?: HTTP
    }
}

enum class DownloadKind(val id: String, val label: String, val folderName: String) {
    FILE("file", "File", "Files"),
    VIDEO("video", "Video", "Media"),
    AUDIO("audio", "Audio", "Media"),
    IMAGE("image", "Image", "Media"),
    DOCUMENT("document", "Document", "Documents"),
    ARCHIVE("archive", "Archive", "Archives"),
    APPLICATION("app", "App", "Apps"),
    TORRENT("torrent", "Torrent", "Torrents"),
    MAGNET("magnet", "Magnet", "Torrents");

    val isTorrent: Boolean get() = this == TORRENT || this == MAGNET
    val isMedia: Boolean get() = this == VIDEO || this == AUDIO || this == IMAGE

    companion object {
        fun fromId(id: String): DownloadKind = entries.firstOrNull { it.id == id } ?: FILE

        fun infer(fileName: String, mimeType: String?, url: String): DownloadKind {
            val lowerUrl = url.trim().lowercase(Locale.US)
            if (lowerUrl.startsWith("magnet:")) return MAGNET

            val lowerName = fileName.lowercase(Locale.US)
            val ext = lowerName.substringAfterLast('.', "")
            val mime = mimeType?.lowercase(Locale.US).orEmpty()

            if (ext == "torrent" || mime == "application/x-bittorrent") return TORRENT
            if (mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "flv", "3gp")) {
                return VIDEO
            }
            if (mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma")) {
                return AUDIO
            }
            if (mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "avif")) {
                return IMAGE
            }
            if (mime.startsWith("application/pdf") || mime.startsWith("text/") || ext in setOf(
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "epub", "rtf"
                )
            ) {
                return DOCUMENT
            }
            if (ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "xpi")) {
                return ARCHIVE
            }
            if (mime == "application/vnd.android.package-archive" || ext in setOf("apk", "aab", "exe", "dmg", "deb", "msi")) {
                return APPLICATION
            }
            return FILE
        }
    }
}

enum class DownloadTaskStatus(val id: String, val label: String) {
    QUEUED("queued", "Queued"),
    CONNECTING("connecting", "Connecting"),
    FETCHING_METADATA("fetching_metadata", "Fetching Metadata"),
    DOWNLOADING("downloading", "Downloading"),
    PAUSED("paused", "Paused"),
    WAITING_WIFI("waiting_wifi", "Waiting for Wi-Fi"),
    SEEDING("seeding", "Seeding"),
    COMPLETED("completed", "Completed"),
    FAILED("failed", "Failed"),
    CANCELLED("cancelled", "Cancelled");

    val isActive: Boolean
        get() = this == QUEUED || this == CONNECTING || this == FETCHING_METADATA || this == DOWNLOADING || this == SEEDING

    val canPause: Boolean
        get() = this == QUEUED || this == CONNECTING || this == FETCHING_METADATA || this == DOWNLOADING || this == SEEDING || this == WAITING_WIFI

    val canResume: Boolean
        get() = this == PAUSED || this == FAILED || this == WAITING_WIFI || this == CANCELLED

    companion object {
        fun fromId(id: String): DownloadTaskStatus = entries.firstOrNull { it.id == id } ?: COMPLETED
    }
}

enum class StorageLocationMode(val id: String, val title: String, val subtitle: String) {
    PUBLIC_DOWNLOADS(
        id = "public_downloads",
        title = "Android Downloads (Recommended)",
        subtitle = "Automatically exports finished files into public Downloads/Simple AI Browser"
    ),
    CUSTOM_TREE(
        id = "custom_tree",
        title = "Custom Folder (Storage Picker)",
        subtitle = "Export finished files into a folder or SD card directory you choose"
    ),
    APP_WORKSPACE(
        id = "app_workspace",
        title = "App Download Vault Only",
        subtitle = "Keep files inside SAB's managed file explorer until you choose to export"
    );

    companion object {
        fun fromId(id: String): StorageLocationMode = entries.firstOrNull { it.id == id } ?: PUBLIC_DOWNLOADS
    }
}

data class DownloadSegmentState(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val completed: Boolean = false
) {
    val totalBytes: Long get() = (endByte - startByte + 1L).coerceAtLeast(0L)

    companion object {
        fun toJson(list: List<DownloadSegmentState>): String {
            val arr = JSONArray()
            list.forEach { seg ->
                arr.put(
                    JSONObject().apply {
                        put("index", seg.index)
                        put("start", seg.startByte)
                        put("end", seg.endByte)
                        put("done", seg.downloadedBytes)
                        put("completed", seg.completed)
                    }
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<DownloadSegmentState> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            DownloadSegmentState(
                                index = obj.optInt("index", i),
                                startByte = obj.optLong("start", 0L),
                                endByte = obj.optLong("end", -1L),
                                downloadedBytes = obj.optLong("done", 0L),
                                completed = obj.optBoolean("completed", false)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val downloadedBytes: Long = 0L,
    val priority: Int = 4 // 0=skip, 4=normal, 7=high
) {
    val isSelected: Boolean get() = priority > 0

    companion object {
        fun toJson(list: List<TorrentFileEntry>): String {
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("index", item.index)
                        put("path", item.path)
                        put("size", item.sizeBytes)
                        put("done", item.downloadedBytes)
                        put("prio", item.priority)
                    }
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<TorrentFileEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            TorrentFileEntry(
                                index = obj.optInt("index", i),
                                path = obj.optString("path"),
                                sizeBytes = obj.optLong("size", 0L),
                                downloadedBytes = obj.optLong("done", 0L),
                                priority = obj.optInt("prio", 4)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

data class DetectedPageMedia(
    val url: String,
    val title: String,
    val kind: DownloadKind,
    val mimeType: String? = null
)

data class SabFileEntry(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val mimeType: String,
    val kind: DownloadKind,
    val childCount: Int = 0
)

data class DownloadManagerSettings(
    val askBeforeDownloading: Boolean = false,
    val defaultConnectionsPerDownload: Int = 4,
    val maxConcurrentDownloads: Int = 3,
    val wifiOnlyDownloads: Boolean = false,
    val speedLimitKbps: Int = 0, // 0 = Unlimited
    val autoResumeOnReconnect: Boolean = true,
    val storageLocationMode: StorageLocationMode = StorageLocationMode.PUBLIC_DOWNLOADS,
    val customTreeUri: String? = null,
    val customTreeLabel: String? = null,
    val autoOrganizeByType: Boolean = false,
    val notifyOnComplete: Boolean = true,
    val torrentSequentialDefault: Boolean = false,
    val torrentSeedAfterComplete: Boolean = false,
    val torrentMaxConnections: Int = 200,
    val torrentDownloadLimitKbps: Int = 0,
    val torrentUploadLimitKbps: Int = 128,
    val torrentEnableDht: Boolean = true,
    val customUserAgent: String = ""
)
