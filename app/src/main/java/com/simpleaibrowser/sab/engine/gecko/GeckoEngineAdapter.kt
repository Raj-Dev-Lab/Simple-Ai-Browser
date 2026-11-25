package com.simpleaibrowser.sab.engine.gecko

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.PageContextSnapshot
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SabGeckoEngine"
private const val SAB_EXTENSION_ID = "sab-bridge@simpleaibrowser.com"
private const val SAB_EXTENSION_URI = "resource://android/assets/extensions/sab-bridge/"
private const val SAB_NATIVE_APP = "sabBridge"

/**
 * Production Mozilla GeckoView 161.0 (non-omni, arm64-v8a, Android 10+) adapter
 * implementing SAB's Opera-inspired engine-agnostic BrowserEngine contract.
 */
class GeckoEngineAdapter(
    context: Context
) : BrowserEngine {

    private val extensionReady = AtomicBoolean(false)

    @Volatile
    private var sabExtension: WebExtension? = null

    private val runtime: GeckoRuntime = synchronized(Companion) {
        sharedRuntime ?: createRuntime(context.applicationContext).also { sharedRuntime = it }
    }

    override val engineInfo: EngineInfo
        get() = EngineInfo(
            name = "Mozilla GeckoView",
            version = "161.0 (non-omni)",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            isGeckoViewActive = true,
            builtInExtensionReady = extensionReady.get(),
            notes = "Pre-compiled GeckoView 161+ arm64-v8a with Enhanced Tracking Protection & sab-bridge WebExtension"
        )

    override fun createRenderView(context: Context): View {
        return GeckoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            coverUntilFirstPaint(0xFF0B0D14.toInt())
        }
    }

    override fun createSession(
        tabId: String,
        isPrivate: Boolean,
        onNewTabRequested: (url: String, parentTabId: String, isPrivate: Boolean) -> Unit,
        onHistoryVisit: (url: String, title: String) -> Unit,
        onTrackerBlocked: (tabId: String) -> Unit
    ): BrowserTabSession {
        return GeckoTabSession(
            id = tabId,
            isPrivate = isPrivate,
            runtime = runtime,
            extensionProvider = { sabExtension },
            onNewTabRequested = onNewTabRequested,
            onHistoryVisit = onHistoryVisit,
            onTrackerBlocked = onTrackerBlocked
        )
    }

    override fun setAdBlockEnabled(enabled: Boolean) {
        val antiTracking = if (enabled) {
            FULL_ANTI_TRACKING
        } else {
            ContentBlocking.AntiTracking.NONE
        }
        runtime.settings.contentBlocking.setAntiTracking(antiTracking)
    }

    override fun setPreferredDarkScheme(isDark: Boolean) {
        runtime.settings.preferredColorScheme = if (isDark) {
            GeckoRuntimeSettings.COLOR_SCHEME_DARK
        } else {
            GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        }
    }

    override fun clearBrowsingData() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
    }

    private fun createRuntime(appContext: Context): GeckoRuntime {
        val contentBlocking = ContentBlocking.Settings.Builder()
            .antiTracking(FULL_ANTI_TRACKING)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .automaticFontSizeAdjustment(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .contentBlocking(contentBlocking)
            .build()

        return GeckoRuntime.create(appContext, settings).also { rt ->
            rt.webExtensionController
                .ensureBuiltIn(SAB_EXTENSION_URI, SAB_EXTENSION_ID)
                .accept(
                    { ext ->
                        sabExtension = ext
                        extensionReady.set(ext != null)
                        Log.i(TAG, "Built-in sab-bridge WebExtension initialized.")
                    },
                    { err ->
                        Log.w(TAG, "Built-in sab-bridge WebExtension setup skipped: ${err?.message}")
                    }
                )
        }
    }

    companion object {
        @Volatile
        private var sharedRuntime: GeckoRuntime? = null

        private const val FULL_ANTI_TRACKING =
            ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
    }
}

class GeckoTabSession(
    override val id: String,
    override val isPrivate: Boolean,
    private val runtime: GeckoRuntime,
    private val extensionProvider: () -> WebExtension?,
    private val onNewTabRequested: (url: String, parentTabId: String, isPrivate: Boolean) -> Unit,
    private val onHistoryVisit: (url: String, title: String) -> Unit,
    private val onTrackerBlocked: (tabId: String) -> Unit
) : BrowserTabSession {

    private val _state = MutableStateFlow(
        TabUiState(
            id = id,
            title = if (isPrivate) "Private Tab" else "Speed Dial",
            url = SAB_START_URL,
            isPrivate = isPrivate,
            securityState = SecurityState.INTERNAL
        )
    )
    override val state: StateFlow<TabUiState> = _state.asStateFlow()

    private val geckoSession: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
    )

    @Volatile
    private var nativePort: WebExtension.Port? = null

    private var isOpened = false

    init {
        installDelegates()
        ensureSessionOpened()
        attachBridgeMessageDelegate()
    }

    private fun ensureSessionOpened() {
        if (!isOpened) {
            geckoSession.open(runtime)
            isOpened = true
        }
    }

    private fun installDelegates() {
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank() || url == "about:blank") return
                _state.update { current ->
                    current.copy(
                        url = url,
                        securityState = UrlResolver.securityForUrl(url),
                        lastVisitedTimestamp = System.currentTimeMillis()
                    )
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _state.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _state.update { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW && uri.startsWith("http")) {
                    onNewTabRequested(uri, id, isPrivate)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String
            ): GeckoResult<GeckoSession>? {
                if (uri.startsWith("http")) {
                    onNewTabRequested(uri, id, isPrivate)
                }
                return null
            }
        }

        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (url == "about:blank") return
                _state.update {
                    it.copy(
                        url = url,
                        isLoading = true,
                        progress = 12,
                        securityState = UrlResolver.securityForUrl(url)
                    )
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _state.update { it.copy(progress = progress.coerceIn(0, 100)) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val snapshot = _state.value
                _state.update {
                    it.copy(
                        isLoading = false,
                        progress = 100,
                        lastVisitedTimestamp = System.currentTimeMillis()
                    )
                }
                if (success && !isPrivate && !snapshot.isStartPage) {
                    onHistoryVisit(snapshot.url, snapshot.title)
                }
                syncBridgeModes()
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                val sec = when {
                    _state.value.isStartPage -> SecurityState.INTERNAL
                    securityInfo.isSecure -> SecurityState.SECURE
                    else -> SecurityState.INSECURE
                }
                _state.update { it.copy(securityState = sec) }
            }
        }

        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val cleanTitle = title?.trim().orEmpty()
                if (cleanTitle.isNotEmpty() && !_state.value.isStartPage) {
                    _state.update { it.copy(title = cleanTitle) }
                }
            }
        }

        geckoSession.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: ContentBlocking.BlockEvent
            ) {
                _state.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }
                onTrackerBlocked(id)
            }
        }
    }

    private fun attachBridgeMessageDelegate() {
        val ext = extensionProvider() ?: return
        geckoSession.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    nativePort = port
                    syncBridgeModes()
                }
            },
            SAB_NATIVE_APP
        )
    }

    private fun syncBridgeModes() {
        val port = nativePort ?: return
        val current = _state.value
        runCatching {
            port.postMessage(
                JSONObject().apply {
                    put("type", "SET_FORCE_DARK")
                    put("enabled", current.forceDarkMode)
                }
            )
            port.postMessage(
                JSONObject().apply {
                    put("type", "SET_READER_MODE")
                    put("enabled", current.readerMode)
                }
            )
        }
    }

    override fun loadUrl(url: String) {
        if (url == SAB_START_URL || url.isBlank()) {
            showStartPage()
            return
        }
        ensureSessionOpened()
        attachBridgeMessageDelegate()
        _state.update {
            it.copy(
                url = url,
                title = UrlResolver.extractHost(url),
                isLoading = true,
                progress = 8,
                securityState = UrlResolver.securityForUrl(url)
            )
        }
        geckoSession.loadUri(url)
    }

    override fun showStartPage() {
        geckoSession.stop()
        _state.update {
            it.copy(
                url = SAB_START_URL,
                title = if (isPrivate) "Private Tab" else "Speed Dial",
                isLoading = false,
                progress = 0,
                securityState = SecurityState.INTERNAL
            )
        }
    }

    override fun goBack() {
        if (_state.value.canGoBack) {
            geckoSession.goBack()
        } else if (!_state.value.isStartPage) {
            showStartPage()
        }
    }

    override fun goForward() {
        if (_state.value.canGoForward) {
            geckoSession.goForward()
        }
    }

    override fun reload() {
        if (!_state.value.isStartPage) {
            geckoSession.reload()
        }
    }

    override fun stopLoading() {
        geckoSession.stop()
        _state.update { it.copy(isLoading = false) }
    }

    override fun setDesktopMode(enabled: Boolean) {
        _state.update { it.copy(desktopMode = enabled) }
        geckoSession.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        geckoSession.settings.viewportMode = if (enabled) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        if (!_state.value.isStartPage) {
            geckoSession.reload()
        }
    }

    override fun setForceDarkMode(enabled: Boolean) {
        _state.update { it.copy(forceDarkMode = enabled) }
        syncBridgeModes()
    }

    override fun setReaderMode(enabled: Boolean) {
        _state.update { it.copy(readerMode = enabled) }
        syncBridgeModes()
    }

    override fun findInPage(query: String) {
        if (query.isBlank()) {
            clearFindInPage()
        } else {
            geckoSession.finder.find(query, GeckoSession.FINDER_FIND_MATCH_CASE.inv() and 0)
        }
    }

    override fun clearFindInPage() {
        geckoSession.finder.clear()
    }

    override fun attachToView(view: View) {
        val geckoView = view as? GeckoView ?: return
        if (geckoView.session !== geckoSession) {
            geckoView.releaseSession()
            geckoView.setSession(geckoSession)
        }
    }

    override fun detachFromView(view: View) {
        val geckoView = view as? GeckoView ?: return
        if (geckoView.session === geckoSession) {
            geckoView.releaseSession()
        }
    }

    override suspend fun extractPageSnapshot(maxChars: Int): PageContextSnapshot {
        val current = _state.value
        return PageContextSnapshot(
            url = current.url,
            title = current.title,
            textContent = ""
        )
    }

    override fun release() {
        nativePort = null
        if (isOpened) {
            geckoSession.close()
            isOpened = false
        }
    }
}
