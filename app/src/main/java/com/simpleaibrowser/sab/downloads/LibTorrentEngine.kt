package com.simpleaibrowser.sab.downloads

import android.util.Log
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.DownloadTaskStatus
import com.simpleaibrowser.sab.core.browser.TorrentFileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.StateUpdateAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TORRENT_TAG = "SabTorrentEngine"

data class TorrentProgressUpdate(
    val infoHash: String,
    val name: String,
    val status: DownloadTaskStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadRateBytesPerSec: Long,
    val uploadRateBytesPerSec: Long,
    val peers: Int,
    val seeds: Int,
    val files: List<TorrentFileEntry>,
    val savePath: String,
    val errorMessage: String? = null
)

class LibTorrentEngine(
    private val fileSystem: DownloadFileSystem,
    private val scope: CoroutineScope,
    private val onUpdate: (TorrentProgressUpdate) -> Unit,
    private val onFinished: (TorrentProgressUpdate) -> Unit
) {
    private val lock = Any()
    private var sessionManager: SessionManager? = null
    private var pollJob: Job? = null
    private val finishedNotifiedHashes = ConcurrentHashMap.newKeySet<String>()
    private val lastErrorByHash = ConcurrentHashMap<String, String>()
    private var lastAppliedSettings: DownloadManagerSettings = DownloadManagerSettings()

    @Volatile
    var isNativeAvailable: Boolean = true
        private set

    fun ensureStarted(settings: DownloadManagerSettings): Boolean {
        synchronized(lock) {
            lastAppliedSettings = settings
            val existing = sessionManager
            if (existing != null && existing.isRunning) {
                applySettingsLocked(existing, settings)
                return true
            }
            return try {
                val sp = buildSettingsPack(settings)
                val sm = SessionManager(false)
                sm.addListener(alertListener)
                sm.start(SessionParams(sp))
                sessionManager = sm
                startPollingLocked()
                true
            } catch (err: UnsatisfiedLinkError) {
                isNativeAvailable = false
                Log.e(TORRENT_TAG, "libtorrent4j native library unavailable on host ABI", err)
                false
            } catch (t: Throwable) {
                Log.e(TORRENT_TAG, "Failed to start libtorrent session", t)
                false
            }
        }
    }

    fun applySettings(settings: DownloadManagerSettings) {
        synchronized(lock) {
            lastAppliedSettings = settings
            val sm = sessionManager ?: return
            if (sm.isRunning) {
                applySettingsLocked(sm, settings)
            }
        }
    }

    private fun buildSettingsPack(settings: DownloadManagerSettings): SettingsPack {
        return SettingsPack().apply {
            isEnableDht = settings.torrentEnableDht
            isEnableLsd = true
            downloadRateLimit(settings.torrentDownloadLimitKbps * 1024)
            uploadRateLimit(settings.torrentUploadLimitKbps * 1024)
            connectionsLimit(settings.torrentMaxConnections.coerceIn(20, 500))
            activeDownloads(settings.maxConcurrentDownloads.coerceIn(1, 6))
            activeSeeds(if (settings.torrentSeedAfterComplete) 3 else 0)
        }
    }

    private fun applySettingsLocked(sm: SessionManager, settings: DownloadManagerSettings) {
        runCatching {
            val sp = buildSettingsPack(settings)
            sm.applySettings(sp)
            if (settings.torrentEnableDht && !sm.isDhtRunning) {
                sm.startDht()
            } else if (!settings.torrentEnableDht && sm.isDhtRunning) {
                sm.stopDht()
            }
        }
    }

    private fun startPollingLocked() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val sm = sessionManager
                if (sm != null && sm.isRunning) {
                    runCatching { sm.postTorrentUpdates() }
                }
                delay(1_000L)
            }
        }
    }

    fun parseMagnetQuickInfo(magnetUri: String): Pair<String?, String?> {
        return runCatching {
            val params = AddTorrentParams.parseMagnetUri(magnetUri)
            val hash = params.infoHashes.best.toHex().lowercase(Locale.US)
            val name = params.name?.takeIf { it.isNotBlank() }
            hash to name
        }.getOrElse {
            val hashMatch = Regex("xt=urn:btih:([A-Fa-f0-9]{40})", RegexOption.IGNORE_CASE).find(magnetUri)
            val dnMatch = Regex("dn=([^&]+)", RegexOption.IGNORE_CASE).find(magnetUri)
            val hash = hashMatch?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
            val dn = dnMatch?.groupValues?.getOrNull(1)?.let {
                runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            }
            hash to dn
        }
    }

    fun startMagnet(
        magnetUri: String,
        saveDir: File,
        sequential: Boolean,
        settings: DownloadManagerSettings
    ): Result<String> = runCatching {
        check(ensureStarted(settings)) { "BitTorrent native engine could not start on this device" }
        val sm = sessionManager ?: error("BitTorrent session unavailable")
        saveDir.mkdirs()

        val (parsedHash, _) = parseMagnetQuickInfo(magnetUri)
        val existing = parsedHash?.let { findHandle(it) }
        if (existing != null && existing.isValid) {
            if (sequential) existing.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            else existing.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            existing.setFlags(TorrentFlags.AUTO_MANAGED)
            existing.resume()
            return@runCatching existing.infoHash().toHex().lowercase(Locale.US)
        }

        val flags: torrent_flags_t = TorrentFlags.AUTO_MANAGED.or_(
            if (sequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else torrent_flags_t()
        )
        sm.download(magnetUri, saveDir, flags)
        parsedHash ?: throw IllegalArgumentException("Invalid magnet link")
    }

    fun startTorrentFile(
        torrentFile: File,
        taskId: String,
        saveDir: File,
        sequential: Boolean,
        existingEntries: List<TorrentFileEntry>,
        settings: DownloadManagerSettings
    ): Result<Pair<String, TorrentInfo>> = runCatching {
        check(ensureStarted(settings)) { "BitTorrent native engine could not start on this device" }
        val sm = sessionManager ?: error("BitTorrent session unavailable")
        saveDir.mkdirs()
        val info = TorrentInfo(torrentFile)
        val infoHash = info.infoHash().toHex().lowercase(Locale.US)
        finishedNotifiedHashes.remove(infoHash)

        val resumeFile = File(fileSystem.metadataDir(), "resume_$infoHash.dat").takeIf { it.exists() && it.length() > 0L }
        val priorities = buildPrioritiesArray(info.numFiles(), existingEntries)

        val existing = findHandle(infoHash)
        if (existing != null && existing.isValid) {
            if (priorities != null) existing.prioritizeFiles(priorities)
            if (sequential) existing.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            else existing.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            existing.setFlags(TorrentFlags.AUTO_MANAGED)
            existing.resume()
            return@runCatching infoHash to info
        }

        val flags = TorrentFlags.AUTO_MANAGED.or_(
            if (sequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else torrent_flags_t()
        )
        sm.download(info, saveDir, resumeFile, priorities, emptyList(), flags)
        infoHash to info
    }

    fun pause(infoHash: String) {
        val handle = findHandle(infoHash) ?: return
        runCatching {
            handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
            handle.pause()
            if (handle.needSaveResumeData()) {
                handle.saveResumeData()
            }
        }
    }

    fun resume(infoHash: String, sequential: Boolean) {
        val handle = findHandle(infoHash) ?: return
        runCatching {
            if (sequential) handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            else handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            handle.setFlags(TorrentFlags.AUTO_MANAGED)
            handle.resume()
        }
    }

    fun setSequential(infoHash: String, sequential: Boolean) {
        val handle = findHandle(infoHash) ?: return
        runCatching {
            if (sequential) handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            else handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        }
    }

    fun applyFilePriorities(infoHash: String, entries: List<TorrentFileEntry>) {
        val handle = findHandle(infoHash) ?: return
        val info = handle.torrentFile() ?: return
        val arr = buildPrioritiesArray(info.numFiles(), entries) ?: return
        runCatching { handle.prioritizeFiles(arr) }
    }

    fun remove(infoHash: String) {
        val sm = sessionManager ?: return
        val handle = findHandle(infoHash) ?: return
        runCatching { sm.remove(handle) }
        finishedNotifiedHashes.remove(infoHash.lowercase(Locale.US))
    }

    fun extractFileEntries(info: TorrentInfo, handle: TorrentHandle? = null): List<TorrentFileEntry> {
        val fs = info.files()
        val count = fs.numFiles()
        val progress = runCatching { handle?.fileProgress() }.getOrNull()
        val currentPriorities = runCatching { handle?.filePriorities() }.getOrNull()
        return (0 until count).map { idx ->
            val prio = when (currentPriorities?.getOrNull(idx)) {
                Priority.IGNORE -> 0
                Priority.TOP_PRIORITY, Priority.SIX, Priority.FIVE -> 7
                else -> 4
            }
            TorrentFileEntry(
                index = idx,
                path = fs.filePath(idx) ?: fs.fileName(idx) ?: "file_$idx",
                sizeBytes = fs.fileSize(idx),
                downloadedBytes = progress?.getOrNull(idx) ?: 0L,
                priority = prio
            )
        }
    }

    private fun buildPrioritiesArray(
        numFiles: Int,
        entries: List<TorrentFileEntry>
    ): Array<Priority>? {
        if (numFiles <= 0 || entries.isEmpty()) return null
        val byIndex = entries.associateBy { it.index }
        return Array(numFiles) { idx ->
            when (byIndex[idx]?.priority ?: 4) {
                0 -> Priority.IGNORE
                in 5..7 -> Priority.TOP_PRIORITY
                else -> Priority.DEFAULT
            }
        }
    }

    private fun findHandle(infoHash: String): TorrentHandle? {
        val sm = sessionManager ?: return null
        val normalized = infoHash.trim().lowercase(Locale.US)
        if (normalized.length != 40) return null
        return runCatching {
            sm.find(Sha1Hash.parseHex(normalized))?.takeIf { it.isValid }
        }.getOrNull()
    }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.ADD_TORRENT.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.STATE_UPDATE.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.SAVE_RESUME_DATA.swig()
        )

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is AddTorrentAlert -> {
                    if (alert.error().isError) {
                        val hash = alert.handle()?.infoHash()?.toHex()?.lowercase(Locale.US) ?: return
                        val msg = alert.error().message
                        lastErrorByHash[hash] = msg
                        emitHandleSnapshot(alert.handle(), explicitError = msg)
                    } else {
                        alert.handle()?.let { emitHandleSnapshot(it) }
                    }
                }
                is MetadataReceivedAlert -> {
                    val handle = alert.handle() ?: return
                    emitHandleSnapshot(handle)
                }
                is StateUpdateAlert -> {
                    val statuses = alert.status().orEmpty()
                    statuses.forEach { st ->
                        val hash = st.infoHashes.best.toHex().lowercase(Locale.US)
                        val handle = findHandle(hash)
                        if (handle != null) {
                            emitStatusSnapshot(handle, st)
                        }
                    }
                }
                is TorrentFinishedAlert -> {
                    val handle = alert.handle() ?: return
                    runCatching { handle.saveResumeData() }
                    val update = buildUpdate(handle, handle.status(), null)
                    if (finishedNotifiedHashes.add(update.infoHash)) {
                        onFinished(update)
                        if (!lastAppliedSettings.torrentSeedAfterComplete) {
                            runCatching {
                                handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
                                handle.pause()
                            }
                        }
                    }
                    onUpdate(update)
                }
                is TorrentErrorAlert -> {
                    val handle = alert.handle() ?: return
                    val msg = alert.error().message.ifBlank { alert.message() }
                    val hash = handle.infoHash().toHex().lowercase(Locale.US)
                    lastErrorByHash[hash] = msg
                    emitHandleSnapshot(handle, explicitError = msg)
                }
                is SaveResumeDataAlert -> {
                    val handle = alert.handle() ?: return
                    val hash = handle.infoHash().toHex().lowercase(Locale.US)
                    runCatching {
                        val bytes = AddTorrentParams.writeResumeDataBuf(alert.params())
                        val resumeOut = File(fileSystem.metadataDir(), "resume_$hash.dat")
                        resumeOut.writeBytes(bytes)
                    }
                }
            }
        }
    }

    private fun emitHandleSnapshot(handle: TorrentHandle, explicitError: String? = null) {
        if (!handle.isValid) return
        emitStatusSnapshot(handle, handle.status(), explicitError)
    }

    private fun emitStatusSnapshot(
        handle: TorrentHandle,
        status: TorrentStatus,
        explicitError: String? = null
    ) {
        val update = buildUpdate(handle, status, explicitError)
        onUpdate(update)
    }

    private fun buildUpdate(
        handle: TorrentHandle,
        status: TorrentStatus,
        explicitError: String?
    ): TorrentProgressUpdate {
        val hash = handle.infoHash().toHex().lowercase(Locale.US)
        val info = runCatching { handle.torrentFile() }.getOrNull()
        val files = if (info != null) extractFileEntries(info, handle) else emptyList()
        val err = explicitError ?: lastErrorByHash[hash]
        val isPaused = (status.flags().and_(TorrentFlags.PAUSED)).non_zero()
        val mappedStatus = when {
            err != null && !isPaused -> DownloadTaskStatus.FAILED
            status.isFinished || status.state() == TorrentStatus.State.FINISHED -> DownloadTaskStatus.COMPLETED
            status.state() == TorrentStatus.State.SEEDING -> {
                if (lastAppliedSettings.torrentSeedAfterComplete) DownloadTaskStatus.SEEDING
                else DownloadTaskStatus.COMPLETED
            }
            isPaused -> DownloadTaskStatus.PAUSED
            status.state() == TorrentStatus.State.DOWNLOADING_METADATA -> DownloadTaskStatus.FETCHING_METADATA
            status.state() == TorrentStatus.State.CHECKING_FILES ||
                status.state() == TorrentStatus.State.CHECKING_RESUME_DATA -> DownloadTaskStatus.CONNECTING
            else -> DownloadTaskStatus.DOWNLOADING
        }
        val name = info?.name()?.takeIf { it.isNotBlank() }
            ?: handle.name?.takeIf { it.isNotBlank() }
            ?: "Torrent $hash"

        return TorrentProgressUpdate(
            infoHash = hash,
            name = name,
            status = mappedStatus,
            downloadedBytes = status.totalWantedDone().coerceAtLeast(status.totalDone()),
            totalBytes = status.totalWanted().coerceAtLeast(status.total()),
            downloadRateBytesPerSec = status.downloadPayloadRate().toLong().coerceAtLeast(0L),
            uploadRateBytesPerSec = status.uploadPayloadRate().toLong().coerceAtLeast(0L),
            peers = status.numPeers(),
            seeds = status.numSeeds(),
            files = files,
            savePath = handle.savePath(),
            errorMessage = err
        )
    }

    fun shutdown() {
        synchronized(lock) {
            pollJob?.cancel()
            pollJob = null
            runCatching { sessionManager?.stop() }
            sessionManager = null
        }
    }
}
