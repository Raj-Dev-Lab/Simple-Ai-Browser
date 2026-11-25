package com.simpleaibrowser.sab.data

import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.TabIsland
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.ThemePreset
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val database: SabDatabase,
    private val preferences: BrowserPreferences
) {
    val speedDials: Flow<List<SpeedDialEntity>> = database.speedDialDao().observeAll()
    val bookmarks: Flow<List<BookmarkEntity>> = database.bookmarkDao().observeAll()
    val history: Flow<List<HistoryEntity>> = database.historyDao().observeRecent()
    val downloads: Flow<List<DownloadEntity>> = database.downloadDao().observeAll()
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
        database.historyDao().insert(
            HistoryEntity(
                title = title.ifBlank { url },
                url = url,
                visitedAt = System.currentTimeMillis()
            )
        )
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
        sizeBytes: Long
    ) {
        database.downloadDao().insert(
            DownloadEntity(
                systemDownloadId = systemDownloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType ?: "application/octet-stream",
                sizeBytes = sizeBytes,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDownloadRecord(id: Long) {
        database.downloadDao().deleteById(id)
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

    suspend fun setAdBlockEnabled(enabled: Boolean) = preferences.setAdBlockEnabled(enabled)
    suspend fun setHttpsOnlyEnabled(enabled: Boolean) = preferences.setHttpsOnlyEnabled(enabled)
    suspend fun setForceDarkDefault(enabled: Boolean) = preferences.setForceDarkDefault(enabled)
    suspend fun setSearchEngine(engine: SearchEngine) = preferences.setSearchEngine(engine)
    suspend fun setThemePreset(preset: ThemePreset) = preferences.setThemePreset(preset)
    suspend fun setAddressBarPosition(position: AddressBarPosition) = preferences.setAddressBarPosition(position)
    suspend fun setRestoreTabsOnStart(enabled: Boolean) = preferences.setRestoreTabsOnStart(enabled)
    suspend fun setDefaultTextZoom(percent: Int) = preferences.setDefaultTextZoom(percent)
    suspend fun setShowWallpaperOnStart(enabled: Boolean) = preferences.setShowWallpaperOnStart(enabled)
    suspend fun incrementBlockedTrackers(delta: Long = 1L) = preferences.incrementBlockedTrackers(delta)
    suspend fun resetBlockedCounter() = preferences.resetBlockedCounter()
}
