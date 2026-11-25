package com.simpleaibrowser.sab.engine.compat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.simpleaibrowser.sab.core.browser.AdBlockFilterList
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
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compatibility fallback adapter implementing BrowserEngine on non-ARM64 host
 * emulators (e.g., x86_64 preview containers without ARM64 native translation).
 * On Android 10+ ARM64 devices, GeckoEngineAdapter (GeckoView 161 arm64-v8a)
 * is always selected first.
 */
class CompatWebViewEngine(
    private val context: Context,
    private val fallbackReason: String
) : BrowserEngine {

    private val adBlockEnabled = AtomicBoolean(true)

    override val engineInfo: EngineInfo
        get() = EngineInfo(
            name = "Compat WebView (x86_64 Host Guard)",
            version = "System WebView • GeckoView 161 arm64-v8a packaged",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            isGeckoViewActive = false,
            builtInExtensionReady = true,
            notes = "Running host compatibility engine because this device lacks arm64-v8a execution ($fallbackReason). Install on an ARM64 Android 10+ device for native GeckoView 161."
        )

    override fun createRenderView(context: Context): View {
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF0B0D14.toInt())
        }
    }

    override fun createSession(
        tabId: String,
        isPrivate: Boolean,
        onNewTabRequested: (url: String, parentTabId: String, isPrivate: Boolean) -> Unit,
        onHistoryVisit: (url: String, title: String) -> Unit,
        onTrackerBlocked: (tabId: String) -> Unit
    ): BrowserTabSession {
        return CompatWebViewTabSession(
            id = tabId,
            isPrivate = isPrivate,
            context = context.applicationContext,
            adBlockEnabled = { adBlockEnabled.get() },
            onHistoryVisit = onHistoryVisit,
            onTrackerBlocked = onTrackerBlocked
        )
    }

    override fun setAdBlockEnabled(enabled: Boolean) {
        adBlockEnabled.set(enabled)
    }

    override fun setPreferredDarkScheme(isDark: Boolean) {
        // Handled per-session CSS injection
    }

    override fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class CompatWebViewTabSession(
    override val id: String,
    override val isPrivate: Boolean,
    private val context: Context,
    private val adBlockEnabled: () -> Boolean,
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

    private val defaultUserAgent: String
    private val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(0xFF0B0D14.toInt())
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = !isPrivate
        settings.databaseEnabled = !isPrivate
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        if (isPrivate) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
        defaultUserAgent = settings.userAgentString
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host
                if (adBlockEnabled() && AdBlockFilterList.isBlockedHost(host)) {
                    _state.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }
                    onTrackerBlocked(this@CompatWebViewTabSession.id)
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url.isNullOrBlank() || url == "about:blank") return
                _state.update {
                    it.copy(
                        url = url,
                        isLoading = true,
                        progress = 15,
                        securityState = UrlResolver.securityForUrl(url),
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val current = _state.value
                val finalUrl = url?.takeIf { it != "about:blank" } ?: current.url
                val finalTitle = view?.title?.takeIf { it.isNotBlank() } ?: UrlResolver.extractHost(finalUrl)
                _state.update {
                    it.copy(
                        url = if (current.isStartPage) SAB_START_URL else finalUrl,
                        title = if (current.isStartPage) current.title else finalTitle,
                        isLoading = false,
                        progress = 100,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true,
                        lastVisitedTimestamp = System.currentTimeMillis()
                    )
                }
                if (!isPrivate && !current.isStartPage && finalUrl.startsWith("http")) {
                    onHistoryVisit(finalUrl, finalTitle)
                }
                applyInjectedStyles()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _state.update { it.copy(progress = newProgress.coerceIn(0, 100)) }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank() && !_state.value.isStartPage) {
                    _state.update { it.copy(title = title) }
                }
            }
        }
    }

    private fun applyInjectedStyles() {
        val current = _state.value
        val darkJs = if (current.forceDarkMode) {
            """(function(){var s=document.getElementById('sab-dark')||document.createElement('style');s.id='sab-dark';s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#0b0d14!important;}img,video,canvas,svg{filter:invert(1) hue-rotate(180deg)!important;}';document.head.appendChild(s);})();"""
        } else {
            """(function(){var s=document.getElementById('sab-dark');if(s)s.remove();})();"""
        }
        val readerJs = if (current.readerMode) {
            """(function(){var s=document.getElementById('sab-reader')||document.createElement('style');s.id='sab-reader';s.textContent='body{max-width:720px!important;margin:0 auto!important;padding:18px!important;line-height:1.7!important;font-size:18px!important;}aside,nav,footer{display:none!important;}';document.head.appendChild(s);})();"""
        } else {
            """(function(){var s=document.getElementById('sab-reader');if(s)s.remove();})();"""
        }
        webView.evaluateJavascript(darkJs, null)
        webView.evaluateJavascript(readerJs, null)
    }

    override fun loadUrl(url: String) {
        if (url == SAB_START_URL || url.isBlank()) {
            showStartPage()
            return
        }
        _state.update {
            it.copy(
                url = url,
                title = UrlResolver.extractHost(url),
                isLoading = true,
                progress = 10,
                securityState = UrlResolver.securityForUrl(url)
            )
        }
        webView.loadUrl(url)
    }

    override fun showStartPage() {
        webView.stopLoading()
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (!_state.value.isStartPage) {
            showStartPage()
        }
    }

    override fun goForward() {
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }

    override fun reload() {
        if (!_state.value.isStartPage) webView.reload()
    }

    override fun stopLoading() {
        webView.stopLoading()
        _state.update { it.copy(isLoading = false) }
    }

    override fun setDesktopMode(enabled: Boolean) {
        _state.update { it.copy(desktopMode = enabled) }
        webView.settings.userAgentString = if (enabled) {
            "Mozilla/5.0 (X11; Linux x86_64; rv:161.0) Gecko/20100101 Firefox/161.0"
        } else {
            defaultUserAgent
        }
        if (!_state.value.isStartPage) webView.reload()
    }

    override fun setForceDarkMode(enabled: Boolean) {
        _state.update { it.copy(forceDarkMode = enabled) }
        applyInjectedStyles()
    }

    override fun setReaderMode(enabled: Boolean) {
        _state.update { it.copy(readerMode = enabled) }
        applyInjectedStyles()
    }

    override fun findInPage(query: String) {
        if (query.isBlank()) clearFindInPage() else webView.findAllAsync(query)
    }

    override fun clearFindInPage() {
        webView.clearMatches()
    }

    override fun attachToView(view: View) {
        val container = view as? FrameLayout ?: return
        val parent = webView.parent as? ViewGroup
        if (parent !== container) {
            parent?.removeView(webView)
            container.removeAllViews()
            container.addView(webView)
        }
    }

    override fun detachFromView(view: View) {
        val container = view as? FrameLayout ?: return
        if (webView.parent === container) {
            container.removeView(webView)
        }
    }

    override suspend fun extractPageSnapshot(maxChars: Int): PageContextSnapshot {
        val current = _state.value
        return PageContextSnapshot(
            url = current.url,
            title = current.title,
            textContent = Uri.parse(current.url).host.orEmpty()
        )
    }

    override fun release() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }
}
