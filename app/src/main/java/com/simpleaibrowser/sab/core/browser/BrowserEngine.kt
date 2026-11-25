package com.simpleaibrowser.sab.core.browser

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

const val SAB_START_URL = "sab://start"

enum class SecurityState {
    INTERNAL,
    SECURE,
    INSECURE,
    MIXED
}

enum class AddressBarPosition(val id: String, val displayName: String, val subtitle: String) {
    TOP("top", "Top Address Bar", "Classic desktop-aligned bar above webpage"),
    BOTTOM("bottom", "Bottom Address Bar", "Opera one-handed thumb-zone bar above navigation");

    companion object {
        fun fromId(id: String): AddressBarPosition = entries.firstOrNull { it.id == id } ?: TOP
    }
}

enum class SearchEngine(
    val id: String,
    val displayName: String,
    val queryTemplate: String,
    val badgeChar: String
) {
    DUCKDUCKGO(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        queryTemplate = "https://duckduckgo.com/?q=%s",
        badgeChar = "D"
    ),
    GOOGLE(
        id = "google",
        displayName = "Google",
        queryTemplate = "https://www.google.com/search?q=%s",
        badgeChar = "G"
    ),
    BRAVE(
        id = "brave",
        displayName = "Brave Search",
        queryTemplate = "https://search.brave.com/search?q=%s",
        badgeChar = "B"
    ),
    STARTPAGE(
        id = "startpage",
        displayName = "Startpage",
        queryTemplate = "https://www.startpage.com/do/search?q=%s",
        badgeChar = "S"
    ),
    ECOSIA(
        id = "ecosia",
        displayName = "Ecosia",
        queryTemplate = "https://www.ecosia.org/search?q=%s",
        badgeChar = "E"
    ),
    BING(
        id = "bing",
        displayName = "Bing",
        queryTemplate = "https://www.bing.com/search?q=%s",
        badgeChar = "M"
    );

    companion object {
        fun fromId(id: String): SearchEngine = entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

enum class ThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean
) {
    OBSIDIAN_CRIMSON(
        id = "obsidian_crimson",
        displayName = "Obsidian Crimson",
        subtitle = "Signature SAB OLED matte with crimson accent",
        isDark = true
    ),
    MIDNIGHT_CYAN(
        id = "midnight_cyan",
        displayName = "Midnight Cobalt",
        subtitle = "Deep slate canvas with electric cyan highlights",
        isDark = true
    ),
    AURORA_VELVET(
        id = "aurora_velvet",
        displayName = "Aurora Velvet",
        subtitle = "Nocturnal plum surface with coral-violet glow",
        isDark = true
    ),
    DAYLIGHT_CLEAN(
        id = "daylight_clean",
        displayName = "Daylight Studio",
        subtitle = "Crisp ceramic white with crimson precision",
        isDark = false
    );

    companion object {
        fun fromId(id: String): ThemePreset = entries.firstOrNull { it.id == id } ?: OBSIDIAN_CRIMSON
    }
}

enum class IslandColor(val id: String, val hex: Long) {
    CRIMSON("crimson", 0xFFFF2E54),
    CYAN("cyan", 0xFF00D4FF),
    EMERALD("emerald", 0xFF10B981),
    AMBER("amber", 0xFFF59E0B),
    VIOLET("violet", 0xFF8B5CF6);

    companion object {
        fun fromIndex(index: Int): IslandColor = entries[index.mod(entries.size)]
        fun fromId(id: String): IslandColor = entries.firstOrNull { it.id == id } ?: CRIMSON
    }
}

data class TabIsland(
    val id: String,
    val name: String,
    val color: IslandColor,
    val isCollapsed: Boolean = false
)

data class FindInPageResult(
    val currentMatch: Int = 0,
    val totalMatches: Int = 0
)

data class TabUiState(
    val id: String,
    val islandId: String? = null,
    val title: String = "Speed Dial",
    val url: String = SAB_START_URL,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val securityState: SecurityState = SecurityState.INTERNAL,
    val desktopMode: Boolean = false,
    val forceDarkMode: Boolean = false,
    val readerMode: Boolean = false,
    val textZoomPercent: Int = 100,
    val blockedTrackersCount: Int = 0,
    val lastVisitedTimestamp: Long = System.currentTimeMillis()
) {
    val isStartPage: Boolean
        get() = url == SAB_START_URL || url.isBlank()
}

data class PageContextSnapshot(
    val url: String,
    val title: String,
    val textContent: String,
    val selectedText: String? = null
)

data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long = -1L
)

data class ContextMenuTarget(
    val tabId: String,
    val linkUrl: String?,
    val mediaUrl: String?,
    val title: String?
)

sealed interface WebPromptRequest {
    val id: String
    val originHost: String

    data class Alert(
        override val id: String,
        override val originHost: String,
        val title: String,
        val message: String,
        val onDismiss: () -> Unit
    ) : WebPromptRequest

    data class Confirm(
        override val id: String,
        override val originHost: String,
        val title: String,
        val message: String,
        val onResult: (confirmed: Boolean) -> Unit
    ) : WebPromptRequest

    data class TextInput(
        override val id: String,
        override val originHost: String,
        val title: String,
        val message: String,
        val defaultValue: String,
        val onResult: (value: String?) -> Unit
    ) : WebPromptRequest

    data class SitePermission(
        override val id: String,
        override val originHost: String,
        val permissionLabel: String,
        val onResult: (granted: Boolean) -> Unit
    ) : WebPromptRequest
}

data class EngineInfo(
    val name: String,
    val version: String,
    val abi: String,
    val isGeckoViewActive: Boolean,
    val builtInExtensionReady: Boolean,
    val notes: String
)

data class SessionCallbacks(
    val onNewTabRequested: (url: String, parentTabId: String, isPrivate: Boolean) -> Unit,
    val onHistoryVisit: (url: String, title: String) -> Unit,
    val onTrackerBlocked: (tabId: String) -> Unit,
    val onDownloadRequested: (DownloadRequest) -> Unit = {},
    val onContextMenu: (ContextMenuTarget) -> Unit = {},
    val onFullScreenChanged: (Boolean) -> Unit = {},
    val onPromptRequested: (WebPromptRequest) -> Unit = {},
    val onFindResult: (FindInPageResult) -> Unit = {}
)

/**
 * Engine-agnostic contract for a single browser tab session.
 * Mirrors Opera Mobile's strict shell-vs-engine decoupling so no UI composable
 * ever imports GeckoView or WebView directly.
 */
interface BrowserTabSession {
    val id: String
    val isPrivate: Boolean
    val state: StateFlow<TabUiState>

    fun loadUrl(url: String)
    fun showStartPage()
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()
    fun setDesktopMode(enabled: Boolean)
    fun setForceDarkMode(enabled: Boolean)
    fun setReaderMode(enabled: Boolean)
    fun setTextZoom(percent: Int)
    fun findInPage(query: String)
    fun findNext(query: String)
    fun findPrevious(query: String)
    fun clearFindInPage()
    fun exitFullScreen()
    fun attachToView(view: View)
    fun detachFromView(view: View)
    suspend fun extractPageSnapshot(maxChars: Int = 12_000): PageContextSnapshot
    fun release()
}

/**
 * Engine-agnostic browser engine factory and global runtime controller.
 */
interface BrowserEngine {
    val engineInfo: EngineInfo

    fun createRenderView(context: Context): View

    fun createSession(
        tabId: String,
        isPrivate: Boolean,
        callbacks: SessionCallbacks
    ): BrowserTabSession

    fun setAdBlockEnabled(enabled: Boolean)
    fun setPreferredDarkScheme(isDark: Boolean)
    fun clearBrowsingData()
}
