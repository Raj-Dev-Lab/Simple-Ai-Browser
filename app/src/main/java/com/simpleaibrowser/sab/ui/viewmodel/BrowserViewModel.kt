package com.simpleaibrowser.sab.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.IslandColor
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.TabIsland
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BookmarkEntity
import com.simpleaibrowser.sab.data.BrowserPreferences
import com.simpleaibrowser.sab.data.BrowserRepository
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.data.HistoryEntity
import com.simpleaibrowser.sab.data.SabDatabase
import com.simpleaibrowser.sab.data.SpeedDialEntity
import com.simpleaibrowser.sab.engine.BrowserEngineFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface BrowserOverlay {
    data object None : BrowserOverlay
    data object TabGallery : BrowserOverlay
    data object OperaMenu : BrowserOverlay
    data object ShieldSheet : BrowserOverlay
    data class Library(val initialTab: LibraryTab = LibraryTab.BOOKMARKS) : BrowserOverlay
    data object Settings : BrowserOverlay
    data object AddSpeedDial : BrowserOverlay
}

enum class LibraryTab {
    BOOKMARKS,
    HISTORY
}

data class ClosedTabRecord(
    val title: String,
    val url: String,
    val closedAt: Long = System.currentTimeMillis()
)

data class OmniboxSuggestion(
    val title: String,
    val url: String,
    val source: String // "Bookmark" | "History" | "Speed Dial"
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val engine: BrowserEngine = BrowserEngineFactory.getOrCreate(application.applicationContext)
    val engineInfo: EngineInfo get() = engine.engineInfo

    private val repository = BrowserRepository(
        database = SabDatabase.getInstance(application.applicationContext),
        preferences = BrowserPreferences(application.applicationContext)
    )

    val settings: StateFlow<BrowserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserSettings()
    )

    val speedDials: StateFlow<List<SpeedDialEntity>> = repository.speedDials.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val history: StateFlow<List<HistoryEntity>> = repository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _sessions = MutableStateFlow<List<BrowserTabSession>>(emptyList())
    private val _activeTabId = MutableStateFlow<String>("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _tabIslandAssignment = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _islands = MutableStateFlow<List<TabIsland>>(emptyList())
    val islands: StateFlow<List<TabIsland>> = _islands.asStateFlow()

    private val _recentlyClosed = MutableStateFlow<List<ClosedTabRecord>>(emptyList())
    val recentlyClosed: StateFlow<List<ClosedTabRecord>> = _recentlyClosed.asStateFlow()

    private val _overlay = MutableStateFlow<BrowserOverlay>(BrowserOverlay.None)
    val overlay: StateFlow<BrowserOverlay> = _overlay.asStateFlow()

    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

    private val _isOmniboxEditing = MutableStateFlow(false)
    val isOmniboxEditing: StateFlow<Boolean> = _isOmniboxEditing.asStateFlow()

    private val _omniboxQuery = MutableStateFlow("")
    val omniboxQuery: StateFlow<String> = _omniboxQuery.asStateFlow()

    private val _isFindInPageVisible = MutableStateFlow(false)
    val isFindInPageVisible: StateFlow<Boolean> = _isFindInPageVisible.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val tabs: StateFlow<List<TabUiState>> = combine(_sessions, _tabIslandAssignment) { list, islandMap ->
        list to islandMap
    }.flatMapLatest { (sessions, islandMap) ->
        if (sessions.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sessions.map { it.state }) { states ->
                states.map { st -> st.copy(islandId = islandMap[st.id]) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeSession: StateFlow<BrowserTabSession?> = combine(_sessions, _activeTabId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val activeTab: StateFlow<TabUiState> = combine(tabs, _activeTabId) { tabList, id ->
        tabList.firstOrNull { it.id == id } ?: tabList.firstOrNull() ?: TabUiState(id = "init")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TabUiState(id = "init")
    )

    val isActivePageBookmarked: StateFlow<Boolean> = combine(activeTab, bookmarks) { tab, list ->
        !tab.isStartPage && list.any { it.url.equals(tab.url, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val omniboxSuggestions: StateFlow<List<OmniboxSuggestion>> = combine(
        _omniboxQuery,
        bookmarks,
        history,
        speedDials
    ) { query, bmList, histList, sdList ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@combine emptyList()
        val results = LinkedHashMap<String, OmniboxSuggestion>()
        bmList.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(4)
            .forEach { results.putIfAbsent(it.url, OmniboxSuggestion(it.title, it.url, "Bookmark")) }
        sdList.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(3)
            .forEach { results.putIfAbsent(it.url, OmniboxSuggestion(it.title, it.url, "Speed Dial")) }
        histList.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(5)
            .forEach { results.putIfAbsent(it.url, OmniboxSuggestion(it.title, it.url, "History")) }
        results.values.take(6)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.seedDefaultSpeedDialsIfEmpty()
        }
        viewModelScope.launch {
            settings.collect { pref ->
                engine.setAdBlockEnabled(pref.adBlockEnabled)
                engine.setPreferredDarkScheme(pref.themePreset.isDark)
            }
        }
        createNewTab(isPrivate = false, initialUrl = null, select = true)
    }

    fun dismissSplash() {
        _showSplash.value = false
    }

    fun replaySplash() {
        _overlay.value = BrowserOverlay.None
        _showSplash.value = true
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun openOverlay(destination: BrowserOverlay) {
        _isOmniboxEditing.value = false
        _overlay.value = destination
    }

    fun closeOverlay() {
        _overlay.value = BrowserOverlay.None
    }

    fun handleIncomingExternalUri(uri: String?) {
        if (uri.isNullOrBlank()) return
        val resolved = UrlResolver.resolve(uri, settings.value.searchEngine, settings.value.httpsOnlyEnabled)
        createNewTab(isPrivate = false, initialUrl = resolved, select = true)
    }

    fun createNewTab(
        isPrivate: Boolean = false,
        initialUrl: String? = null,
        parentTabId: String? = null,
        islandId: String? = null,
        select: Boolean = true
    ): String {
        val tabId = "tab-${UUID.randomUUID().toString().take(8)}"
        val session = engine.createSession(
            tabId = tabId,
            isPrivate = isPrivate,
            onNewTabRequested = { url, originTabId, privateFlag ->
                viewModelScope.launch {
                    createChildTabInIsland(url = url, parentTabId = originTabId, isPrivate = privateFlag)
                }
            },
            onHistoryVisit = { url, title ->
                viewModelScope.launch {
                    repository.recordHistory(url, title)
                }
            },
            onTrackerBlocked = {
                viewModelScope.launch {
                    repository.incrementBlockedTrackers(1L)
                }
            }
        )
        if (settings.value.forceDarkDefault) {
            session.setForceDarkMode(true)
        }
        _sessions.update { it + session }

        val resolvedIsland = islandId ?: if (!isPrivate && parentTabId != null) {
            ensureIslandForParent(parentTabId)
        } else null
        if (resolvedIsland != null) {
            _tabIslandAssignment.update { it + (tabId to resolvedIsland) }
        }

        if (!initialUrl.isNullOrBlank() && initialUrl != SAB_START_URL) {
            session.loadUrl(initialUrl)
        }
        if (select) {
            _activeTabId.value = tabId
            _overlay.value = BrowserOverlay.None
        }
        return tabId
    }

    private fun createChildTabInIsland(url: String, parentTabId: String, isPrivate: Boolean) {
        val islandId = if (!isPrivate) ensureIslandForParent(parentTabId) else null
        createNewTab(
            isPrivate = isPrivate,
            initialUrl = url,
            parentTabId = parentTabId,
            islandId = islandId,
            select = true
        )
        if (islandId != null) {
            showToast("Grouped into Tab Island")
        }
    }

    private fun ensureIslandForParent(parentTabId: String): String {
        val existing = _tabIslandAssignment.value[parentTabId]
        if (existing != null) return existing
        val parentTab = tabs.value.firstOrNull { it.id == parentTabId }
        val islandName = parentTab?.let {
            UrlResolver.extractHost(it.url).substringBefore(".").replaceFirstChar { c -> c.uppercase() }
        }?.ifBlank { "Research" } ?: "Research"
        val newIslandId = "island-${UUID.randomUUID().toString().take(6)}"
        val color = IslandColor.fromIndex(_islands.value.size)
        _islands.update { it + TabIsland(id = newIslandId, name = islandName, color = color) }
        _tabIslandAssignment.update { it + (parentTabId to newIslandId) }
        return newIslandId
    }

    fun createTabIsland(name: String, tabIds: List<String> = emptyList()) {
        val cleanName = name.trim().ifBlank { "Island ${_islands.value.size + 1}" }
        val islandId = "island-${UUID.randomUUID().toString().take(6)}"
        val color = IslandColor.fromIndex(_islands.value.size)
        _islands.update { it + TabIsland(id = islandId, name = cleanName, color = color) }
        if (tabIds.isNotEmpty()) {
            _tabIslandAssignment.update { map ->
                map + tabIds.associateWith { islandId }
            }
        } else {
            createNewTab(isPrivate = false, islandId = islandId, select = false)
        }
        showToast("Created island \"$cleanName\"")
    }

    fun toggleIslandCollapsed(islandId: String) {
        _islands.update { list ->
            list.map { if (it.id == islandId) it.copy(isCollapsed = !it.isCollapsed) else it }
        }
    }

    fun renameIsland(islandId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _islands.update { list ->
            list.map { if (it.id == islandId) it.copy(name = trimmed) else it }
        }
    }

    fun assignTabToIsland(tabId: String, islandId: String?) {
        _tabIslandAssignment.update { map ->
            if (islandId == null) map - tabId else map + (tabId to islandId)
        }
        pruneEmptyIslands()
    }

    fun closeIsland(islandId: String) {
        val memberIds = _tabIslandAssignment.value.filterValues { it == islandId }.keys.toList()
        memberIds.forEach { closeTab(it) }
        _islands.update { list -> list.filterNot { it.id == islandId } }
        showToast("Closed Tab Island")
    }

    fun selectTab(tabId: String) {
        if (_sessions.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            _overlay.value = BrowserOverlay.None
            _isFindInPageVisible.value = false
        }
    }

    fun closeTab(tabId: String) {
        val currentList = _sessions.value
        val index = currentList.indexOfFirst { it.id == tabId }
        if (index == -1) return
        val session = currentList[index]
        val stateSnapshot = session.state.value
        if (!stateSnapshot.isPrivate && !stateSnapshot.isStartPage) {
            _recentlyClosed.update { prev ->
                (listOf(ClosedTabRecord(stateSnapshot.title, stateSnapshot.url)) + prev).take(25)
            }
        }
        session.release()
        val updated = currentList.filterNot { it.id == tabId }
        _sessions.value = updated
        _tabIslandAssignment.update { it - tabId }
        pruneEmptyIslands()

        if (updated.isEmpty()) {
            createNewTab(isPrivate = false, select = true)
        } else if (_activeTabId.value == tabId) {
            val nextIndex = (index - 1).coerceAtLeast(0).coerceAtMost(updated.lastIndex)
            _activeTabId.value = updated[nextIndex].id
        }
    }

    fun closeAllTabs(privateOnly: Boolean) {
        val toClose = _sessions.value.filter { it.isPrivate == privateOnly }
        toClose.forEach { session ->
            val st = session.state.value
            if (!st.isPrivate && !st.isStartPage) {
                _recentlyClosed.update { prev ->
                    (listOf(ClosedTabRecord(st.title, st.url)) + prev).take(25)
                }
            }
            session.release()
        }
        val remaining = _sessions.value.filterNot { it.isPrivate == privateOnly }
        _sessions.value = remaining
        val closedIds = toClose.map { it.id }.toSet()
        _tabIslandAssignment.update { map -> map.filterKeys { it !in closedIds } }
        pruneEmptyIslands()
        if (remaining.isEmpty()) {
            createNewTab(isPrivate = false, select = true)
        } else if (_activeTabId.value in closedIds) {
            _activeTabId.value = remaining.last().id
        }
        showToast(if (privateOnly) "Closed all private tabs" else "Closed all tabs")
    }

    fun reopenLastClosedTab() {
        val record = _recentlyClosed.value.firstOrNull() ?: return
        _recentlyClosed.update { it.drop(1) }
        createNewTab(isPrivate = false, initialUrl = record.url, select = true)
    }

    private fun pruneEmptyIslands() {
        val activeIslandIds = _tabIslandAssignment.value.values.toSet()
        _islands.update { list -> list.filter { it.id in activeIslandIds } }
    }

    fun beginOmniboxEdit() {
        val current = activeTab.value
        _omniboxQuery.value = if (current.isStartPage) "" else current.url
        _isOmniboxEditing.value = true
    }

    fun updateOmniboxQuery(query: String) {
        _omniboxQuery.value = query
    }

    fun cancelOmniboxEdit() {
        _isOmniboxEditing.value = false
    }

    fun submitOmnibox(rawInput: String = _omniboxQuery.value) {
        val resolved = UrlResolver.resolve(
            rawInput = rawInput,
            searchEngine = settings.value.searchEngine,
            httpsOnly = settings.value.httpsOnlyEnabled
        )
        _isOmniboxEditing.value = false
        _overlay.value = BrowserOverlay.None
        if (resolved == SAB_START_URL) {
            activeSession.value?.showStartPage()
        } else {
            activeSession.value?.loadUrl(resolved)
        }
    }

    fun navigateToUrl(url: String) {
        _isOmniboxEditing.value = false
        _overlay.value = BrowserOverlay.None
        val resolved = UrlResolver.resolve(url, settings.value.searchEngine, settings.value.httpsOnlyEnabled)
        activeSession.value?.loadUrl(resolved)
    }

    fun navigateHome() {
        _isOmniboxEditing.value = false
        _isFindInPageVisible.value = false
        activeSession.value?.showStartPage()
    }

    fun goBack(): Boolean {
        if (_isOmniboxEditing.value) {
            _isOmniboxEditing.value = false
            return true
        }
        if (_isFindInPageVisible.value) {
            closeFindInPage()
            return true
        }
        if (_overlay.value != BrowserOverlay.None) {
            _overlay.value = BrowserOverlay.None
            return true
        }
        val tab = activeTab.value
        if (tab.canGoBack || !tab.isStartPage) {
            activeSession.value?.goBack()
            return true
        }
        return false
    }

    fun goForward() = activeSession.value?.goForward()

    fun reloadOrStop() {
        val tab = activeTab.value
        if (tab.isLoading) activeSession.value?.stopLoading() else activeSession.value?.reload()
    }

    fun toggleDesktopMode() {
        val next = !activeTab.value.desktopMode
        activeSession.value?.setDesktopMode(next)
        showToast(if (next) "Desktop site enabled" else "Mobile site restored")
    }

    fun toggleForceDarkMode() {
        val next = !activeTab.value.forceDarkMode
        activeSession.value?.setForceDarkMode(next)
        showToast(if (next) "Force dark active on tab" else "Force dark disabled")
    }

    fun toggleReaderMode() {
        val next = !activeTab.value.readerMode
        activeSession.value?.setReaderMode(next)
        showToast(if (next) "Reader focus active" else "Standard layout restored")
    }

    fun openFindInPage() {
        _overlay.value = BrowserOverlay.None
        _isFindInPageVisible.value = true
    }

    fun updateFindQuery(query: String) {
        _findQuery.value = query
        activeSession.value?.findInPage(query)
    }

    fun closeFindInPage() {
        _isFindInPageVisible.value = false
        _findQuery.value = ""
        activeSession.value?.clearFindInPage()
    }

    fun toggleCurrentBookmark() {
        val tab = activeTab.value
        if (tab.isStartPage) return
        viewModelScope.launch {
            val added = repository.toggleBookmark(tab.title, tab.url)
            showToast(if (added) "Saved to Bookmarks" else "Removed from Bookmarks")
        }
    }

    fun addCurrentPageToSpeedDial() {
        val tab = activeTab.value
        if (tab.isStartPage) return
        viewModelScope.launch {
            repository.addSpeedDial(tab.title, tab.url)
            showToast("Pinned to Speed Dial")
        }
    }

    fun addSpeedDial(title: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            repository.addSpeedDial(title, url)
            _overlay.value = BrowserOverlay.None
            showToast("Added to Speed Dial")
        }
    }

    fun removeSpeedDial(id: Long) {
        viewModelScope.launch {
            repository.removeSpeedDial(id)
            showToast("Removed from Speed Dial")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun clearAllBrowsingData() {
        viewModelScope.launch {
            repository.clearHistory()
            engine.clearBrowsingData()
            _recentlyClosed.value = emptyList()
            showToast("History, cache, and cookies cleared")
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAdBlockEnabled(enabled)
            showToast(if (enabled) "Privacy Shield enabled" else "Privacy Shield paused")
        }
    }

    fun setHttpsOnlyEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHttpsOnlyEnabled(enabled) }
    }

    fun setForceDarkDefault(enabled: Boolean) {
        viewModelScope.launch { repository.setForceDarkDefault(enabled) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch {
            repository.setSearchEngine(engine)
            showToast("Search engine: ${engine.displayName}")
        }
    }

    fun setThemePreset(preset: ThemePreset) {
        viewModelScope.launch { repository.setThemePreset(preset) }
    }

    fun setShowWallpaperOnStart(enabled: Boolean) {
        viewModelScope.launch { repository.setShowWallpaperOnStart(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        _sessions.value.forEach { it.release() }
    }
}
