package com.simpleaibrowser.sab.core.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

enum class TrackingProtectionLevel(val id: String, val displayName: String, val subtitle: String) {
    OFF("off", "Off", "No tracker blocking (legacy compatibility)"),
    STANDARD("standard", "Standard", "Blocks ads, analytics, cryptominers & social trackers smoothly"),
    STRICT("strict", "Strict", "Aggressive tracker, email pixel, social & fingerprint isolation");

    companion object {
        fun fromId(id: String): TrackingProtectionLevel = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

enum class CookieIsolationMode(val id: String, val displayName: String, val subtitle: String) {
    TOTAL_ISOLATE(
        id = "total_isolate",
        displayName = "Total Cookie Protection",
        subtitle = "Confines cross-site cookies to per-site jars (dFPI)"
    ),
    BLOCK_TRACKERS(
        id = "block_trackers",
        displayName = "Block Trackers",
        subtitle = "Blocks known tracking cookies while allowing benign embeds"
    ),
    FIRST_PARTY_ONLY(
        id = "first_party",
        displayName = "First-Party Only",
        subtitle = "Rejects all third-party cookies across sites"
    ),
    BLOCK_ALL(
        id = "block_all",
        displayName = "Block All Cookies",
        subtitle = "Rejects every cookie (may break logins)"
    );

    companion object {
        fun fromId(id: String): CookieIsolationMode = entries.firstOrNull { it.id == id } ?: TOTAL_ISOLATE
    }
}

enum class DnsOverHttpsMode(
    val id: String,
    val displayName: String,
    val resolverUri: String,
    val subtitle: String
) {
    OFF("off", "System DNS", "", "Uses active network / carrier DNS"),
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare (1.1.1.1)",
        resolverUri = "https://mozilla.cloudflare-dns.com/dns-query",
        subtitle = "Fast encrypted DoH with 24h zero-retention policy"
    ),
    QUAD9(
        id = "quad9",
        displayName = "Quad9 Security",
        resolverUri = "https://dns.quad9.net/dns-query",
        subtitle = "Encrypted DoH blocking known malware & phishing domains"
    ),
    ADGUARD(
        id = "adguard",
        displayName = "AdGuard DoH",
        resolverUri = "https://dns.adguard-dns.com/dns-query",
        subtitle = "Encrypted DNS with network-level ad & tracker filtering"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom DoH",
        resolverUri = "",
        subtitle = "Use your own HTTPS DNS-over-HTTPS endpoint"
    );

    companion object {
        fun fromId(id: String): DnsOverHttpsMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

data class PrivacyConfig(
    val trackingProtectionLevel: TrackingProtectionLevel = TrackingProtectionLevel.STANDARD,
    val doNotTrackEnabled: Boolean = true,
    val globalPrivacyControlEnabled: Boolean = true,
    val cookieIsolationMode: CookieIsolationMode = CookieIsolationMode.TOTAL_ISOLATE,
    val cookiePurgingEnabled: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val bounceTrackingProtection: Boolean = true,
    val fingerprintingDefense: Boolean = true,
    val localNetworkProtection: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val httpsOnlyEnabled: Boolean = true,
    val dnsOverHttpsMode: DnsOverHttpsMode = DnsOverHttpsMode.OFF,
    val customDohUrl: String = "",
    val dohStrict: Boolean = false
) {
    val isProtectionEnabled: Boolean get() = trackingProtectionLevel != TrackingProtectionLevel.OFF
}

sealed interface SabViewerRequest {
    val title: String

    data class Pdf(
        override val title: String,
        val localFilePath: String? = null,
        val contentUri: String? = null,
        val sourceUrl: String? = null,
        val shareUri: String? = null
    ) : SabViewerRequest

    data class Media(
        override val title: String,
        val uri: String,
        val isVideo: Boolean,
        val mimeType: String? = null,
        val shareUri: String? = null,
        val fallbackExternalUri: String? = null
    ) : SabViewerRequest
}

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
    val contentLength: Long = -1L,
    val localUri: String? = null,
    val sourceType: DownloadSourceType = DownloadSourceType.HTTP,
    val referrerUrl: String? = null,
    val stagedCacheFilePath: String? = null
)

data class ContextMenuTarget(
    val tabId: String,
    val linkUrl: String?,
    val mediaUrl: String?,
    val title: String?
)

data class PromptChoiceOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val disabled: Boolean,
    val isGroupHeader: Boolean = false,
    val isSeparator: Boolean = false,
    val indented: Boolean = false
)

sealed interface WebPromptRequest {
    val id: String
    val originHost: String
    val tabId: String

    fun cancelSilently()

    data class Alert(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val message: String,
        val onDismiss: () -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onDismiss()
    }

    data class Confirm(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val message: String,
        val confirmLabel: String = "Confirm",
        val cancelLabel: String = "Cancel",
        val onResult: (confirmed: Boolean) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(false)
    }

    data class TextInput(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val message: String,
        val defaultValue: String,
        val onResult: (value: String?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null)
    }

    data class SitePermission(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val permissionLabel: String,
        val onResult: (granted: Boolean) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(false)
    }

    data class HttpAuth(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val message: String,
        val usernameOnly: Boolean,
        val onResult: (username: String?, password: String?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null, null)
    }

    data class ChoiceSelect(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val message: String,
        val multiple: Boolean,
        val options: List<PromptChoiceOption>,
        val onResult: (selectedIds: List<String>?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null)
    }

    data class ColorPick(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val defaultValue: String,
        val predefinedColors: List<String>,
        val onResult: (hexColor: String?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null)
    }

    data class DateTimePick(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val modeHint: String,
        val defaultValue: String,
        val onResult: (value: String?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null)
    }

    data class FileUpload(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val title: String,
        val mimeTypes: List<String>,
        val allowMultiple: Boolean,
        val onResult: (uris: List<Uri>?) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(null)
    }

    data class AndroidPermissions(
        override val id: String,
        override val originHost: String,
        override val tabId: String = "",
        val permissions: List<String>,
        val onResult: (granted: Boolean) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(false)
    }

    data class ExtensionInstall(
        override val id: String,
        override val originHost: String = "Extension Store",
        override val tabId: String = "",
        val extensionName: String,
        val version: String,
        val permissions: List<String>,
        val origins: List<String>,
        val onResult: (granted: Boolean, allowPrivate: Boolean) -> Unit
    ) : WebPromptRequest {
        override fun cancelSilently() = onResult(false, false)
    }
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
    val onFindResult: (FindInPageResult) -> Unit = {},
    val onShareRequested: (title: String, textOrUrl: String) -> Unit = { _, _ -> },
    val onSessionRecovered: (tabId: String, message: String) -> Unit = { _, _ -> }
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
    suspend fun detectPageMedia(): List<DetectedPageMedia> = emptyList()

    /**
     * Captures the currently rendered page as a bitmap (page screenshot feature).
     * Returns null when no view is attached or capture is unsupported.
     */
    suspend fun capturePageBitmap(): Bitmap? = null
    fun release()
}

/**
 * Engine-agnostic browser engine factory and global runtime controller.
 */
interface BrowserEngine {
    val engineInfo: EngineInfo
    val extensionsController: BrowserExtensionsController? get() = null

    fun createRenderView(context: Context): View

    fun createSession(
        tabId: String,
        isPrivate: Boolean,
        callbacks: SessionCallbacks
    ): BrowserTabSession

    fun setActiveSession(session: BrowserTabSession?) {}
    fun setAdBlockEnabled(enabled: Boolean)
    fun applyPrivacyConfig(config: PrivacyConfig) {
        setAdBlockEnabled(config.isProtectionEnabled)
    }
    fun setPreferredDarkScheme(isDark: Boolean)
    fun clearBrowsingData()
    fun clearCookiesOnly() { clearBrowsingData() }
    fun clearHistoryAndCachesOnly() { clearBrowsingData() }
}
