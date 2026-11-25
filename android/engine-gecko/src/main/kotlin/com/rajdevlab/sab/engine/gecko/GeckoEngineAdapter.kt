package com.rajdevlab.sab.engine.gecko

import android.content.Context
import com.rajdevlab.sab.core.browser.BrowserEngine
import com.rajdevlab.sab.core.browser.BrowserTabSession
import com.rajdevlab.sab.core.browser.PageContextSnapshot
import com.rajdevlab.sab.core.browser.TabUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * GeckoView 159+ (non-omni, arm64-v8a, Android 10+) adapter implementing SAB's
 * Opera-inspired engine-agnostic BrowserEngine contract.
 */
class GeckoEngineAdapter(
    context: Context
) : BrowserEngine {

    override val engineName: String = "GeckoView 159+ (arm64-v8a)"

    private val runtime: GeckoRuntime = synchronized(Companion) {
        sharedRuntime ?: createRuntime(context.applicationContext).also { sharedRuntime = it }
    }

    override fun createSession(tabId: String, isPrivate: Boolean): BrowserTabSession {
        return GeckoTabSession(
            tabId = tabId,
            isPrivate = isPrivate,
            runtime = runtime
        )
    }

    override fun setAdBlockEnabled(enabled: Boolean) {
        val level = if (enabled) {
            ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
        } else {
            ContentBlocking.AntiTracking.NONE
        }
        runtime.settings.contentBlocking.setAntiTracking(level)
    }

    override fun clearBrowsingData() {
        runtime.storageController.clearData(
            org.mozilla.geckoview.StorageController.ClearFlags.ALL
        )
    }

    private fun createRuntime(appContext: Context): GeckoRuntime {
        val contentBlocking = ContentBlocking.Settings.Builder()
            .antiTracking(
                ContentBlocking.AntiTracking.AD or
                    ContentBlocking.AntiTracking.ANALYTIC or
                    ContentBlocking.AntiTracking.SOCIAL or
                    ContentBlocking.AntiTracking.CRYPTOMINING or
                    ContentBlocking.AntiTracking.FINGERPRINTING
            )
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .automaticFontSizeAdjustment(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .contentBlocking(contentBlocking)
            .build()

        return GeckoRuntime.create(appContext, settings).also { rt ->
            rt.webExtensionController.ensureBuiltIn(
                "resource://android/assets/extensions/sab-bridge/",
                "sab-bridge@rajdevlab.com"
            )
        }
    }

    companion object {
        @Volatile
        private var sharedRuntime: GeckoRuntime? = null
    }
}

class GeckoTabSession(
    tabId: String,
    isPrivate: Boolean,
    private val runtime: GeckoRuntime
) : BrowserTabSession {

    private val _state = MutableStateFlow(
        TabUiState(id = tabId, isPrivate = isPrivate)
    )
    override val state: StateFlow<TabUiState> = _state.asStateFlow()

    val geckoSession: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .build()
    )

    init {
        attachDelegates()
        geckoSession.open(runtime)
    }

    private fun attachDelegates() {
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGuesture: Boolean
            ) {
                val safeUrl = url ?: return
                _state.update {
                    it.copy(
                        url = safeUrl,
                        isSecure = safeUrl.startsWith("https://") || safeUrl.startsWith("sab://")
                    )
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _state.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _state.update { it.copy(canGoForward = canGoForward) }
            }
        }

        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                _state.update { it.copy(isLoading = true, progress = 10, blockedTrackersCount = 0) }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _state.update { it.copy(progress = progress) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                _state.update { it.copy(isLoading = false, progress = 100) }
            }
        }

        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    _state.update { it.copy(title = title) }
                }
            }
        }

        geckoSession.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: ContentBlocking.BlockEvent
            ) {
                _state.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }
            }
        }
    }

    override fun loadUrl(urlOrQuery: String) {
        val trimmed = urlOrQuery.trim()
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=" +
                URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
        }
        geckoSession.loadUri(target)
    }

    override fun goBack() = geckoSession.goBack()
    override fun goForward() = geckoSession.goForward()
    override fun reload() = geckoSession.reload()
    override fun stopLoading() = geckoSession.stop()

    override suspend fun extractPageText(maxChars: Int): PageContextSnapshot {
        // Wired to sab-bridge WebExtension port in full feature implementation
        val current = _state.value
        return PageContextSnapshot(
            url = current.url,
            title = current.title,
            textContent = ""
        )
    }

    override fun setDesktopMode(enabled: Boolean) {
        geckoSession.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        geckoSession.reload()
    }

    override fun setReaderMode(enabled: Boolean) {
        // Dispatched to sab-bridge WebExtension port
    }

    override fun setForceDarkMode(enabled: Boolean) {
        runtime.settings.preferredColorScheme = if (enabled) {
            GeckoRuntimeSettings.COLOR_SCHEME_DARK
        } else {
            GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        }
    }

    override fun release() {
        geckoSession.close()
    }
}
