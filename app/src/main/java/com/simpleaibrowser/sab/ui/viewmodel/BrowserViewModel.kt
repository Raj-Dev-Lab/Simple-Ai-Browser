package com.simpleaibrowser.sab.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.ContextMenuTarget
import com.simpleaibrowser.sab.core.browser.DownloadRequest
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.FindInPageResult
import com.simpleaibrowser.sab.core.browser.IslandColor
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.SessionCallbacks
import com.simpleaibrowser.sab.core.browser.TabIsland
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import com.simpleaibrowser.sab.data.BookmarkEntity
import com.simpleaibrowser.sab.data.BrowserPreferences
import com.simpleaibrowser.sab.data.BrowserRepository
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.data.DownloadEntity
import com.simpleaibrowser.sab.data.HistoryEntity
import com.simpleaibrowser.sab.data.SabDatabase
import com.simpleaibrowser.sab.data.SpeedDialEntity
import com.simpleaibrowser.sab.engine.BrowserEngineFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    HISTORY,
    DOWNLOADS
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads.stateIn(
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

    private val _findResult = MutableStateFlow(FindInPageResult())
    val findResult: StateFlow<FindInPageResult> = _findResult.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _contextMenuTarget = MutableStateFlow<ContextMenuTarget?>(null)
    val contextMenuTarget: StateFlow<ContextMenuTarget?> = _contextMenuTarget.asStateFlow()

    private val _activePrompt = MutableStateFlow<WebPromptRequest?>(null)
    val activePrompt: StateFlow<WebPromptRequest?> = _activePrompt.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    @Volatile
    private var isSessionHydrated = false

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
        viewModelScope.launch {
            hydratePreviousSessionOrCreateFresh()
        }
        viewModelScope.launch {
            combine(tabs, _activeTabId, _islands) { tabList, currentId, islandList ->
                Triple(tabList, currentId, islandList)
            }
                .debounce(650L)
                .collect { (tabList, currentId, islandList) ->
                    if (!isSessionHydrated) return@collect
                    if (!settings.value.restoreTabsOnStart) return@collect
                    val regularTabs = tabList.filterNot { it.isPrivate }
                    repository.saveSessionSnapshot(
                        regularTabs = regularTabs,
                        activeTabId = currentId,
                        islands = islandList
                    )
                }
        }
    }

    private suspend fun hydratePreviousSessionOrCreateFresh() {
        val initialSettings = runCatching { repository.settings.first() }.getOrDefault(BrowserSettings())
        if (initialSettings.restoreTabsOnStart) {
            val (savedTabs, savedIslands) = runCatching { repository.loadSessionSnapshot() }
                .getOrDefault(emptyList<com.simpleaibrowser.sab.data.SavedTabEntity>() to emptyList())
            if (savedTabs.isNotEmpty()) {
                val restoredIslands = savedIslands.map { entity ->
                    TabIsland(
                        id = entity.islandId,
                        name = entity.name,
                        color = IslandColor.fromId(entity.colorId),
                        isCollapsed = entity.isCollapsed
                    )
                }
                _islands.value = restoredIslands
                var selectedId: String? = null
                savedTabs.forEach { saved ->
                    val createdId = createNewTab(
                        isPrivate = false,
                        initialUrl = saved.url.takeIf { it != SAB_START_URL && it.isNotBlank() },
                        islandId = saved.islandId,
                        select = false,
                        preferredTabId = saved.tabId
                    )
                    if (saved.isActive) {
                        selectedId = createdId
                    }
                }
                _activeTabId.value = selectedId ?: _sessions.value.firstOrNull()?.id.orEmpty()
                isSessionHydrated = true
                return
            }
        }
        if (_sessions.value.isEmpty()) {
            createNewTab(isPrivate = false, initialUrl = null, select = true)
        }
        isSessionHydrated = true
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

    fun dismissContextMenu() {
        _contextMenuTarget.value = null
    }

    fun dismissPrompt() {
        _activePrompt.value = null
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
        select: Boolean = true,
        preferredTabId: String? = null
    ): String {
        val tabId = preferredTabId ?: "tab-${UUID.randomUUID().toString().take(8)}"
        val callbacks = SessionCallbacks(
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
            },
            onDownloadRequested = { request ->
                enqueueDownload(request)
            },
            onContextMenu = { target ->
                _contextMenuTarget.value = target
            },
            onFullScreenChanged = { fullScreen ->
                _isFullScreen.value = fullScreen
            },
            onPromptRequested = { prompt ->
                _activePrompt.value = prompt
            },
            onFindResult = { result ->
                _findResult.value = result
            }
        )
        val session = engine.createSession(
            tabId = tabId,
            isPrivate = isPrivate,
            callbacks = callbacks
        )
        val currentSettings = settings.value
        if (currentSettings.forceDarkDefault) {
            session.setForceDarkMode(true)
        }
        if (currentSettings.defaultTextZoom != 100) {
            session.setTextZoom(currentSettings.defaultTextZoom)
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

    fun openLinkInNewTab(url: String, inIsland: Boolean = false, isPrivate: Boolean = false) {
        _contextMenuTarget.value = null
        val currentId = _activeTabId.value.takeIf { it.isNotBlank() }
        if (inIsland && !isPrivate && currentId != null) {
            createChildTabInIsland(url = url, parentTabId = currentId, isPrivate = false)
        } else {
            createNewTab(isPrivate = isPrivate, initialUrl = url, select = true)
        }
    }

    fun copyTextToClipboard(label: String, text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        showToast("Copied $label")
    }

    fun downloadUrl(url: String) {
        _contextMenuTarget.value = null
        enqueueDownload(
            DownloadRequest(
                url = url,
                fileName = UrlResolver.guessFileName(url, null),
                mimeType = null,
                contentLength = -1L
            )
        )
    }

    fun enqueueDownload(request: DownloadRequest) {
        val uri = runCatching { Uri.parse(request.url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https")) {
            showToast("Unsupported download link")
            return
        }
        runCatching {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val dmRequest = DownloadManager.Request(uri)
                .setTitle(request.fileName)
                .setDescription("Downloading via Simple AI Browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            if (!request.mimeType.isNullOrBlank()) {
                dmRequest.setMimeType(request.mimeType)
            }
            val downloadId = dm.enqueue(dmRequest)
            viewModelScope.launch {
                repository.recordDownload(
                    systemDownloadId = downloadId,
                    fileName = request.fileName,
                    url = request.url,
                    mimeType = request.mimeType,
                    sizeBytes = request.contentLength
                )
            }
            showToast("Downloading ${request.fileName}")
        }.onFailure {
            showToast("Download could not start")
        }
    }

    fun openDownloadedFile(context: Context, item: DownloadEntity) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val fileUri = runCatching {
            if (item.systemDownloadId > 0L) dm?.getUriForDownloadedFile(item.systemDownloadId) else null
        }.getOrNull()
        if (fileUri != null) {
            val mime = dm?.getMimeTypeForDownloadedFile(item.systemDownloadId) ?: item.mimeType
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(viewIntent) }.isSuccess
            if (launched) return
        }
        val fallback = runCatching {
            context.startActivity(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.isSuccess
        if (!fallback) {
            showToast("File is in your Android Downloads folder")
        }
    }

    fun deleteDownloadRecord(id: Long) {
        viewModelScope.launch { repository.deleteDownloadRecord(id) }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            repository.clearDownloads()
            showToast("Cleared downloads list")
        }
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
        showToast("Renamed island to \"$trimmed\"")
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
            _findResult.value = FindInPageResult()
        }
    }

    fun switchToAdjacentTab(step: Int) {
        val current = activeTab.value
        val sameModeTabs = _sessions.value.filter { it.isPrivate == current.isPrivate }
        if (sameModeTabs.size <= 1) return
        val idx = sameModeTabs.indexOfFirst { it.id == current.id }
        if (idx == -1) return
        val nextIdx = (idx + step).mod(sameModeTabs.size)
        val target = sameModeTabs[nextIdx]
        _activeTabId.value = target.id
        _isFindInPageVisible.value = false
        _findResult.value = FindInPageResult()
        val label = target.state.value.title.take(26)
        showToast("Switched to $label")
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
        _findResult.value = FindInPageResult()
        activeSession.value?.showStartPage()
    }

    fun exitFullScreen() {
        activeSession.value?.exitFullScreen()
        _isFullScreen.value = false
    }

    fun goBack(): Boolean {
        if (_isFullScreen.value) {
            exitFullScreen()
            return true
        }
        if (_contextMenuTarget.value != null) {
            _contextMenuTarget.value = null
            return true
        }
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

    fun adjustActiveTabTextZoom(delta: Int) {
        val current = activeTab.value.textZoomPercent
        val next = (current + delta).coerceIn(75, 160)
        activeSession.value?.setTextZoom(next)
    }

    fun resetActiveTabTextZoom() {
        activeSession.value?.setTextZoom(100)
    }

    fun openFindInPage() {
        _overlay.value = BrowserOverlay.None
        _isFindInPageVisible.value = true
    }

    fun updateFindQuery(query: String) {
        _findQuery.value = query
        activeSession.value?.findInPage(query)
    }

    fun findNextMatch() {
        val q = _findQuery.value
        if (q.isNotBlank()) activeSession.value?.findNext(q)
    }

    fun findPreviousMatch() {
        val q = _findQuery.value
        if (q.isNotBlank()) activeSession.value?.findPrevious(q)
    }

    fun closeFindInPage() {
        _isFindInPageVisible.value = false
        _findQuery.value = ""
        _findResult.value = FindInPageResult()
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
            repository.clearSessionSnapshot()
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

    fun setAddressBarPosition(position: AddressBarPosition) {
        viewModelScope.launch {
            repository.setAddressBarPosition(position)
            showToast(position.displayName)
        }
    }

    fun setRestoreTabsOnStart(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRestoreTabsOnStart(enabled)
            if (!enabled) {
                repository.clearSessionSnapshot()
            }
        }
    }

    fun setDefaultTextZoom(percent: Int) {
        val clamped = percent.coerceIn(75, 160)
        viewModelScope.launch {
            repository.setDefaultTextZoom(clamped)
            activeSession.value?.setTextZoom(clamped)
        }
    }

    fun setShowWallpaperOnStart(enabled: Boolean) {
        viewModelScope.launch { repository.setShowWallpaperOnStart(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        _sessions.value.forEach { it.release() }
    }
}
