package com.simpleaibrowser.sab.engine.compat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
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
import com.simpleaibrowser.sab.core.browser.DownloadRequest
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.FindInPageResult
import com.simpleaibrowser.sab.core.browser.PageContextSnapshot
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.SessionCallbacks
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compatibility fallback adapter implementing BrowserEngine if native GeckoView
 * runtime initialization ever fails on an unsupported host environment.
 * On Android 10+ arm64-v8a & x86_64 devices, GeckoEngineAdapter is always primary.
 */
class CompatWebViewEngine(
    private val context: Context,
    private val fallbackReason: String
) : BrowserEngine {

    private val adBlockEnabled = AtomicBoolean(true)

    override val engineInfo: EngineInfo
        get() = EngineInfo(
            name = "Compat WebView (Fallback Guard)",
            version = "System WebView • GeckoView 161 packaged",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            isGeckoViewActive = false,
            builtInExtensionReady = true,
            notes = "Running fallback compatibility engine ($fallbackReason)."
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
        callbacks: SessionCallbacks
    ): BrowserTabSession {
        return CompatWebViewTabSession(
            id = tabId,
            isPrivate = isPrivate,
            context = context.applicationContext,
            adBlockEnabled = { adBlockEnabled.get() },
            callbacks = callbacks
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

    override fun clearHistoryAndCachesOnly() {
        runCatching { WebView(context).clearCache(true) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class CompatWebViewTabSession(
    override val id: String,
    override val isPrivate: Boolean,
    private val context: Context,
    private val adBlockEnabled: () -> Boolean,
    private val callbacks: SessionCallbacks
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
        setDownloadListener { url, _, _, mimeType, contentLength ->
            if (url.isNullOrBlank()) return@setDownloadListener
            callbacks.onDownloadRequested(
                DownloadRequest(
                    url = url,
                    fileName = UrlResolver.guessFileName(url, mimeType),
                    mimeType = mimeType,
                    contentLength = contentLength
                )
            )
        }
        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            callbacks.onFindResult(
                FindInPageResult(
                    currentMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                    totalMatches = numberOfMatches
                )
            )
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host
                if (adBlockEnabled() && AdBlockFilterList.isBlockedHost(host)) {
                    _state.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }
                    callbacks.onTrackerBlocked(this@CompatWebViewTabSession.id)
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
                    callbacks.onHistoryVisit(finalUrl, finalTitle)
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

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val host = UrlResolver.extractHost(url ?: _state.value.url)
                val done = AtomicBoolean(false)
                callbacks.onPromptRequested(
                    WebPromptRequest.Alert(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = this@CompatWebViewTabSession.id,
                        title = host,
                        message = message.orEmpty(),
                        onDismiss = { if (done.compareAndSet(false, true)) result?.confirm() }
                    )
                )
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val host = UrlResolver.extractHost(url ?: _state.value.url)
                val done = AtomicBoolean(false)
                callbacks.onPromptRequested(
                    WebPromptRequest.Confirm(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = this@CompatWebViewTabSession.id,
                        title = host,
                        message = message.orEmpty(),
                        onResult = { ok ->
                            if (done.compareAndSet(false, true)) {
                                if (ok) result?.confirm() else result?.cancel()
                            }
                        }
                    )
                )
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val host = UrlResolver.extractHost(url ?: _state.value.url)
                val done = AtomicBoolean(false)
                callbacks.onPromptRequested(
                    WebPromptRequest.TextInput(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = this@CompatWebViewTabSession.id,
                        title = host,
                        message = message.orEmpty(),
                        defaultValue = defaultValue.orEmpty(),
                        onResult = { value ->
                            if (done.compareAndSet(false, true)) {
                                if (value != null) result?.confirm(value) else result?.cancel()
                            }
                        }
                    )
                )
                return true
            }
        }
    }

    private fun applyInjectedStyles() {
        val current = _state.value
        val darkJs = if (current.forceDarkMode) {
            """(function(){
              var bg=getComputedStyle(document.documentElement).backgroundColor;
              var m=bg&&bg.match(/\d+/g);
              if(m&&m.length>=3&&((0.2126*m[0]+0.7152*m[1]+0.0722*m[2])<105)){return;}
              var s=document.getElementById('sab-dark')||document.createElement('style');
              s.id='sab-dark';
              s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#0e1118!important;}img,video,canvas,svg,picture,iframe{filter:invert(1) hue-rotate(180deg)!important;}';
              (document.head||document.documentElement).appendChild(s);
            })();"""
        } else {
            """(function(){var s=document.getElementById('sab-dark');if(s)s.remove();})();"""
        }
        val readerJs = if (current.readerMode) {
            """(function(){var s=document.getElementById('sab-reader')||document.createElement('style');s.id='sab-reader';s.textContent='body{max-width:720px!important;margin:0 auto!important;padding:18px!important;line-height:1.72!important;font-size:18px!important;}aside,nav,footer,[role="banner"],[role="navigation"]{display:none!important;}';(document.head||document.documentElement).appendChild(s);})();"""
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

    override fun setTextZoom(percent: Int) {
        val clamped = percent.coerceIn(75, 160)
        _state.update { it.copy(textZoomPercent = clamped) }
        webView.settings.textZoom = clamped
    }

    override fun findInPage(query: String) {
        if (query.isBlank()) clearFindInPage() else webView.findAllAsync(query)
    }

    override fun findNext(query: String) {
        if (query.isNotBlank()) webView.findNext(true)
    }

    override fun findPrevious(query: String) {
        if (query.isNotBlank()) webView.findNext(false)
    }

    override fun clearFindInPage() {
        webView.clearMatches()
        callbacks.onFindResult(FindInPageResult(0, 0))
    }

    override fun exitFullScreen() {
        callbacks.onFullScreenChanged(false)
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
