package com.simpleaibrowser.sab.engine.gecko

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserExtensionsController
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.SessionCallbacks
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SabGeckoEngine"
internal const val SAB_NATIVE_APP = "sabBridge"

/**
 * Production Mozilla GeckoView 161.0 (arm64-v8a & x86_64, Android 10+) adapter
 * implementing SAB's engine-agnostic BrowserEngine contract with full WebExtensions support.
 */
class GeckoEngineAdapter(
    context: Context
) : BrowserEngine {

    private val appContext = context.applicationContext
    private val extensionReady = AtomicBoolean(false)

    @Volatile
    private var runtimePromptHandler: ((WebPromptRequest) -> Unit)? = null

    @Volatile
    private var runtimeNewTabHandler: ((url: String, isPrivate: Boolean) -> Unit)? = null

    @Volatile
    private var activeGeckoSession: GeckoSession? = null

    private val runtime: GeckoRuntime = synchronized(Companion) {
        sharedRuntime ?: createRuntime(appContext).also { sharedRuntime = it }
    }

    private val geckoExtensionsController = GeckoExtensionsController(
        runtime = runtime,
        onPromptRequested = { prompt ->
            runtimePromptHandler?.invoke(prompt) ?: prompt.cancelSilently()
        },
        onOpenNewTab = { url, isPrivate ->
            runtimeNewTabHandler?.invoke(url, isPrivate)
        },
        onBuiltInReady = { ext ->
            extensionReady.set(true)
            Log.i(TAG, "Built-in sab-bridge v${ext.metaData.version} ready (private=${ext.metaData.allowedInPrivateBrowsing})")
        }
    )

    override val extensionsController: BrowserExtensionsController
        get() = geckoExtensionsController

    override val engineInfo: EngineInfo
        get() {
            val geckoVer = org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION.ifBlank { "161.0" }
            val buildId = org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
            return EngineInfo(
                name = "Mozilla GeckoView",
                version = "$geckoVer ($buildId)",
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
                isGeckoViewActive = true,
                builtInExtensionReady = extensionReady.get(),
                notes = "GeckoView $geckoVer (${org.mozilla.geckoview.BuildConfig.OMNIJAR_NAME}) with WebExtensions, AMO/GitHub add-ons, ETP, and sab-bridge v1.2"
            )
        }

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
        callbacks: SessionCallbacks
    ): BrowserTabSession {
        runtimePromptHandler = callbacks.onPromptRequested
        runtimeNewTabHandler = { url, priv ->
            callbacks.onNewTabRequested(url, "", priv)
        }
        return GeckoTabSession(
            id = tabId,
            isPrivate = isPrivate,
            appContext = appContext,
            runtime = runtime,
            extensionsController = geckoExtensionsController,
            callbacks = callbacks
        )
    }

    override fun setActiveSession(session: BrowserTabSession?) {
        val nextSession = (session as? GeckoTabSession)?.geckoSessionRef()
        val prevSession = activeGeckoSession
        if (prevSession === nextSession) return
        prevSession?.let {
            runCatching { runtime.webExtensionController.setTabActive(it, false) }
        }
        activeGeckoSession = nextSession
        nextSession?.let {
            runCatching { runtime.webExtensionController.setTabActive(it, true) }
        }
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

    override fun clearHistoryAndCachesOnly() {
        val cacheFlags = StorageController.ClearFlags.ALL_CACHES or
            StorageController.ClearFlags.NETWORK_CACHE or
            StorageController.ClearFlags.IMAGE_CACHE
        runtime.storageController.clearData(cacheFlags)
    }

    private fun createRuntime(context: Context): GeckoRuntime {
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
            .extensionsWebAPIEnabled(true)
            .extensionsProcessEnabled(true)
            .build()

        return GeckoRuntime.create(context, settings)
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
