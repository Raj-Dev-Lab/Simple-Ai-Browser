package com.simpleaibrowser.sab.data

import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.TabIsland
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val database: SabDatabase,
    private val preferences: BrowserPreferences
) {
    val speedDials: Flow<List<SpeedDialEntity>> = database.speedDialDao().observeAll()
    val bookmarks: Flow<List<BookmarkEntity>> = database.bookmarkDao().observeAll()
    val history: Flow<List<HistoryEntity>> = database.historyDao().observeRecent()
    val downloads: Flow<List<DownloadEntity>> = database.downloadDao().observeAll()
    val extensionRecords: Flow<List<ExtensionRecordEntity>> = database.extensionRecordDao().observeAll()
    val settings: Flow<BrowserSettings> = preferences.settingsFlow

    suspend fun seedDefaultSpeedDialsIfEmpty() {
        if (database.speedDialDao().count() == 0) {
            val defaults = listOf(
                SpeedDialEntity(title = "DuckDuckGo", url = "https://duckduckgo.com", accentHex = 0xFFDE5833, position = 0),
                SpeedDialEntity(title = "Wikipedia", url = "https://www.wikipedia.org", accentHex = 0xFF6366F1, position = 1),
                SpeedDialEntity(title = "GitHub", url = "https://github.com", accentHex = 0xFF2EA043, position = 2),
                SpeedDialEntity(title = "YouTube", url = "https://m.youtube.com", accentHex = 0xFFFF2E54, position = 3),
                SpeedDialEntity(title = "Reddit", url = "https://www.reddit.com", accentHex = 0xFFFF4500, position = 4),
                SpeedDialEntity(title = "Hacker News", url = "https://news.ycombinator.com", accentHex = 0xFFF59E0B, position = 5),
                SpeedDialEntity(title = "MDN Docs", url = "https://developer.mozilla.org", accentHex = 0xFF00D4FF, position = 6),
                SpeedDialEntity(title = "Mozilla", url = "https://www.mozilla.org", accentHex = 0xFF8B5CF6, position = 7)
            )
            database.speedDialDao().insertAll(defaults)
        }
    }

    suspend fun addSpeedDial(title: String, url: String) {
        val palette = listOf(0xFFFF2E54L, 0xFF00D4FFL, 0xFF10B981L, 0xFF8B5CF6L, 0xFFF59E0BL, 0xFFEC4899L)
        val accent = palette[(title.hashCode().and(0x7FFFFFFF)) % palette.size]
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        database.speedDialDao().insert(
            SpeedDialEntity(
                title = title.trim().ifBlank { normalizedUrl },
                url = normalizedUrl,
                accentHex = accent,
                position = database.speedDialDao().count()
            )
        )
    }

    suspend fun removeSpeedDial(id: Long) {
        database.speedDialDao().deleteById(id)
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        if (url == SAB_START_URL || url.isBlank()) return false
        val existing = database.bookmarkDao().findByUrl(url)
        return if (existing != null) {
            database.bookmarkDao().deleteById(existing.id)
            false
        } else {
            database.bookmarkDao().insert(
                BookmarkEntity(
                    title = title.ifBlank { url },
                    url = url
                )
            )
            true
        }
    }

    suspend fun deleteBookmark(id: Long) {
        database.bookmarkDao().deleteById(id)
    }

    suspend fun recordHistory(url: String, title: String) {
        if (url == SAB_START_URL || url.isBlank() || url.startsWith("about:")) return
        val normalizedTitle = title.ifBlank { url }
        val now = System.currentTimeMillis()
        val dao = database.historyDao()
        val existing = dao.findLatestByUrl(url)
        if (existing != null) {
            dao.updateVisit(id = existing.id, title = normalizedTitle, visitedAt = now)
            dao.deleteOlderDuplicates(url = url, keepId = existing.id)
        } else {
            dao.insert(
                HistoryEntity(
                    title = normalizedTitle,
                    url = url,
                    visitedAt = now
                )
            )
        }
    }

    suspend fun deleteHistoryItem(id: Long) {
        database.historyDao().deleteById(id)
    }

    suspend fun clearHistory() {
        database.historyDao().clearAll()
    }

    suspend fun recordDownload(
        systemDownloadId: Long,
        fileName: String,
        url: String,
        mimeType: String?,
        sizeBytes: Long,
        localUri: String? = null
    ) {
        database.downloadDao().insert(
            DownloadEntity(
                systemDownloadId = systemDownloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType ?: "application/octet-stream",
                sizeBytes = sizeBytes,
                downloadedBytes = sizeBytes.coerceAtLeast(0L),
                createdAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                localUri = localUri,
                publicUri = localUri,
                status = "completed"
            )
        )
    }

    suspend fun upsertDownloadTask(entity: DownloadEntity): Long =
        database.downloadDao().insert(entity)

    suspend fun getAllDownloads(): List<DownloadEntity> =
        database.downloadDao().getAll()

    suspend fun findDownloadByTaskId(taskId: String): DownloadEntity? =
        database.downloadDao().findByTaskId(taskId)

    suspend fun findDownloadByInfoHash(infoHash: String): DownloadEntity? =
        database.downloadDao().findByInfoHash(infoHash)

    suspend fun updateDownloadRuntimeState(
        taskId: String,
        fileName: String,
        sizeBytes: Long,
        downloadedBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        status: String,
        supportsRange: Boolean,
        etag: String?,
        segmentsJson: String?,
        torrentPeers: Int,
        torrentSeeds: Int,
        torrentFilesJson: String?,
        torrentInfoHash: String?,
        errorMessage: String?
    ) = database.downloadDao().updateRuntimeState(
        taskId = taskId,
        fileName = fileName,
        sizeBytes = sizeBytes,
        downloadedBytes = downloadedBytes,
        speedBytesPerSec = speedBytesPerSec,
        etaSeconds = etaSeconds,
        status = status,
        supportsRange = supportsRange,
        etag = etag,
        segmentsJson = segmentsJson,
        torrentPeers = torrentPeers,
        torrentSeeds = torrentSeeds,
        torrentFilesJson = torrentFilesJson,
        torrentInfoHash = torrentInfoHash,
        errorMessage = errorMessage
    )

    suspend fun updateDownloadStatus(taskId: String, status: String, errorMessage: String? = null) =
        database.downloadDao().updateStatus(taskId, status, errorMessage)

    suspend fun markDownloadCompleted(
        taskId: String,
        totalBytes: Long,
        localUri: String?,
        publicUri: String?,
        stagingPath: String,
        sha256: String?
    ) = database.downloadDao().markCompleted(
        taskId = taskId,
        totalBytes = totalBytes,
        localUri = localUri,
        publicUri = publicUri,
        stagingPath = stagingPath,
        sha256 = sha256,
        completedAt = System.currentTimeMillis()
    )

    suspend fun setTorrentSequential(taskId: String, sequential: Boolean) =
        database.downloadDao().setTorrentSequential(taskId, sequential)

    suspend fun updateTorrentFilesJson(taskId: String, filesJson: String) =
        database.downloadDao().updateTorrentFilesJson(taskId, filesJson)

    suspend fun updateDownloadLocations(
        taskId: String,
        fileName: String,
        stagingPath: String,
        localUri: String?,
        publicUri: String?
    ) = database.downloadDao().updateFileLocations(taskId, fileName, stagingPath, localUri, publicUri)

    suspend fun deleteDownloadRecord(id: Long) {
        database.downloadDao().deleteById(id)
    }

    suspend fun clearFinishedDownloads() {
        database.downloadDao().clearFinished()
    }

    suspend fun clearDownloads() {
        database.downloadDao().clearAll()
    }

    suspend fun saveSessionSnapshot(
        regularTabs: List<TabUiState>,
        activeTabId: String,
        islands: List<TabIsland>
    ) {
        val dao = database.sessionSnapshotDao()
        dao.clearSavedTabs()
        dao.clearSavedIslands()
        if (islands.isNotEmpty()) {
            dao.insertIslands(
                islands.mapIndexed { idx, island ->
                    SavedIslandEntity(
                        islandId = island.id,
                        name = island.name,
                        colorId = island.color.id,
                        isCollapsed = island.isCollapsed,
                        sortOrder = idx
                    )
                }
            )
        }
        if (regularTabs.isNotEmpty()) {
            dao.insertTabs(
                regularTabs.mapIndexed { idx, tab ->
                    SavedTabEntity(
                        tabId = tab.id,
                        title = tab.title,
                        url = tab.url,
                        islandId = tab.islandId,
                        isActive = tab.id == activeTabId,
                        sortOrder = idx
                    )
                }
            )
        }
    }

    suspend fun loadSessionSnapshot(): Pair<List<SavedTabEntity>, List<SavedIslandEntity>> {
        val dao = database.sessionSnapshotDao()
        return dao.getSavedTabs() to dao.getSavedIslands()
    }

    suspend fun clearSessionSnapshot() {
        val dao = database.sessionSnapshotDao()
        dao.clearSavedTabs()
        dao.clearSavedIslands()
    }

    suspend fun upsertExtensionRecord(
        extensionId: String,
        name: String,
        source: ExtensionSource,
        sourceRef: String,
        installedVersion: String,
        homepageUrl: String? = null,
        iconUrl: String? = null
    ) {
        val dao = database.extensionRecordDao()
        val existing = dao.findById(extensionId)
        dao.upsert(
            ExtensionRecordEntity(
                extensionId = extensionId,
                name = name,
                sourceType = source.id,
                sourceRef = sourceRef,
                installedVersion = installedVersion,
                latestVersion = existing?.latestVersion,
                latestDownloadUrl = existing?.latestDownloadUrl,
                homepageUrl = homepageUrl ?: existing?.homepageUrl,
                iconUrl = iconUrl ?: existing?.iconUrl,
                autoUpdateEnabled = existing?.autoUpdateEnabled ?: true,
                lastCheckedAt = existing?.lastCheckedAt ?: 0L,
                lastUpdatedAt = System.currentTimeMillis(),
                lastError = null
            )
        )
    }

    suspend fun getAllExtensionRecords(): List<ExtensionRecordEntity> = database.extensionRecordDao().getAll()

    suspend fun findExtensionRecord(extensionId: String): ExtensionRecordEntity? =
        database.extensionRecordDao().findById(extensionId)

    suspend fun setExtensionAutoUpdate(extensionId: String, enabled: Boolean) =
        database.extensionRecordDao().setAutoUpdate(extensionId, enabled)

    suspend fun updateExtensionCheckMetadata(
        extensionId: String,
        latestVersion: String?,
        downloadUrl: String?,
        error: String? = null
    ) = database.extensionRecordDao().updateCheckMetadata(
        extensionId = extensionId,
        latestVersion = latestVersion,
        downloadUrl = downloadUrl,
        checkedAt = System.currentTimeMillis(),
        error = error
    )

    suspend fun deleteExtensionRecord(extensionId: String) = database.extensionRecordDao().deleteById(extensionId)

    suspend fun setAdBlockEnabled(enabled: Boolean) = preferences.setAdBlockEnabled(enabled)
    suspend fun setTrackingProtectionLevel(level: TrackingProtectionLevel) =
        preferences.setTrackingProtectionLevel(level)
    suspend fun setDoNotTrackEnabled(enabled: Boolean) = preferences.setDoNotTrackEnabled(enabled)
    suspend fun setGlobalPrivacyControlEnabled(enabled: Boolean) = preferences.setGlobalPrivacyControlEnabled(enabled)
    suspend fun setCookieIsolationMode(mode: CookieIsolationMode) = preferences.setCookieIsolationMode(mode)
    suspend fun setCookiePurgingEnabled(enabled: Boolean) = preferences.setCookiePurgingEnabled(enabled)
    suspend fun setStripTrackingParams(enabled: Boolean) = preferences.setStripTrackingParams(enabled)
    suspend fun setBounceTrackingProtection(enabled: Boolean) = preferences.setBounceTrackingProtection(enabled)
    suspend fun setFingerprintingDefense(enabled: Boolean) = preferences.setFingerprintingDefense(enabled)
    suspend fun setLocalNetworkProtection(enabled: Boolean) = preferences.setLocalNetworkProtection(enabled)
    suspend fun setSafeBrowsingEnabled(enabled: Boolean) = preferences.setSafeBrowsingEnabled(enabled)
    suspend fun setDnsOverHttpsConfig(mode: DnsOverHttpsMode, customUrl: String, strict: Boolean) =
        preferences.setDnsOverHttpsConfig(mode, customUrl, strict)
    suspend fun setClearPrivateDataOnExit(enabled: Boolean) = preferences.setClearPrivateDataOnExit(enabled)
    suspend fun setHttpsOnlyEnabled(enabled: Boolean) = preferences.setHttpsOnlyEnabled(enabled)
    suspend fun setForceDarkDefault(enabled: Boolean) = preferences.setForceDarkDefault(enabled)
    suspend fun setSearchEngine(engine: SearchEngine) = preferences.setSearchEngine(engine)
    suspend fun setThemePreset(preset: ThemePreset) = preferences.setThemePreset(preset)
    suspend fun setAddressBarPosition(position: AddressBarPosition) = preferences.setAddressBarPosition(position)
    suspend fun setRestoreTabsOnStart(enabled: Boolean) = preferences.setRestoreTabsOnStart(enabled)
    suspend fun setDefaultTextZoom(percent: Int) = preferences.setDefaultTextZoom(percent)
    suspend fun setShowWallpaperOnStart(enabled: Boolean) = preferences.setShowWallpaperOnStart(enabled)
    suspend fun setAutoUpdateExtensions(enabled: Boolean) = preferences.setAutoUpdateExtensions(enabled)
    suspend fun setLastExtensionUpdateCheckAt(timestamp: Long) = preferences.setLastExtensionUpdateCheckAt(timestamp)
    suspend fun updateDownloadSettings(transform: (DownloadManagerSettings) -> DownloadManagerSettings) =
        preferences.updateDownloadSettings(transform)
    suspend fun incrementBlockedTrackers(delta: Long = 1L) = preferences.incrementBlockedTrackers(delta)
    suspend fun resetBlockedCounter() = preferences.resetBlockedCounter()
}
