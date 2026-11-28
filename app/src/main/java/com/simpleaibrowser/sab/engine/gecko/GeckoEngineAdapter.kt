package com.simpleaibrowser.sab.engine.gecko

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserExtensionsController
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.PrivacyConfig
import com.simpleaibrowser.sab.core.browser.SecureRouting
import com.simpleaibrowser.sab.core.browser.SessionCallbacks
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SabGeckoEngine"
internal const val SAB_NATIVE_APP = "sabBridge"

/**
 * Production Mozilla GeckoView 161.0 (arm64-v8a & x86_64, Android 10+) adapter
 * implementing SAB's engine-agnostic BrowserEngine contract with full WebExtensions & Privacy Suite support.
 */
class GeckoEngineAdapter(
    context: Context
) : BrowserEngine {

    private val appContext = context.applicationContext
    private val extensionReady = AtomicBoolean(false)
    private val activeSessions = CopyOnWriteArrayList<WeakReference<GeckoTabSession>>()

    @Volatile
    private var currentPrivacyConfig: PrivacyConfig = PrivacyConfig()

    @Volatile
    private var runtimePromptHandler: ((WebPromptRequest) -> Unit)? = null

    @Volatile
    private var runtimeNewTabHandler: ((url: String, isPrivate: Boolean) -> Unit)? = null

    @Volatile
    private var activeGeckoSession: GeckoSession? = null

    private val runtime: GeckoRuntime = synchronized(Companion) {
        sharedRuntime ?: createRuntime(appContext).also { sharedRuntime = it }
    }

    private val geckoExtensionsController: GeckoExtensionsController = GeckoExtensionsController(
        runtime = runtime,
        onPromptRequested = { prompt ->
            runtimePromptHandler?.invoke(prompt) ?: prompt.cancelSilently()
        },
        onOpenNewTab = { url, isPrivate ->
            runtimeNewTabHandler?.invoke(url, isPrivate)
        },
        onBuiltInReady = { ext ->
            extensionReady.set(true)
            val cfg = currentPrivacyConfig
            geckoExtensionsController.updatePrivacyHeaders(
                doNotTrack = cfg.doNotTrackEnabled,
                globalPrivacyControl = cfg.globalPrivacyControlEnabled
            )
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
                notes = "GeckoView $geckoVer (${org.mozilla.geckoview.BuildConfig.OMNIJAR_NAME}) with WebExtensions, Privacy Suite (ETP/GPC/DNT/DoH), and sab-bridge v1.3"
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
        val session = GeckoTabSession(
            id = tabId,
            isPrivate = isPrivate,
            appContext = appContext,
            runtime = runtime,
            extensionsController = geckoExtensionsController,
            callbacks = callbacks
        )
        val cfg = currentPrivacyConfig
        session.applyPrivacySignals(
            useTrackingProtection = cfg.isProtectionEnabled,
            doNotTrack = cfg.doNotTrackEnabled,
            globalPrivacyControl = cfg.globalPrivacyControlEnabled
        )
        activeSessions.removeAll { it.get() == null }
        activeSessions.add(WeakReference(session))
        return session
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
        val level = if (enabled) TrackingProtectionLevel.STANDARD else TrackingProtectionLevel.OFF
        applyPrivacyConfig(currentPrivacyConfig.copy(trackingProtectionLevel = level))
    }

    override fun applyPrivacyConfig(config: PrivacyConfig) {
        currentPrivacyConfig = config
        val cb = runtime.settings.contentBlocking
        val rs = runtime.settings

        val etpLevel = when (config.trackingProtectionLevel) {
            TrackingProtectionLevel.OFF -> ContentBlocking.EtpLevel.NONE
            TrackingProtectionLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
            TrackingProtectionLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
        }
        val antiTracking = when (config.trackingProtectionLevel) {
            TrackingProtectionLevel.OFF -> ContentBlocking.AntiTracking.NONE
            TrackingProtectionLevel.STANDARD ->
                ContentBlocking.AntiTracking.DEFAULT or
                    ContentBlocking.AntiTracking.CRYPTOMINING or
                    (if (config.fingerprintingDefense) ContentBlocking.AntiTracking.FINGERPRINTING else 0)
            TrackingProtectionLevel.STRICT ->
                ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.EMAIL
        }
        val cookieBehavior = when (config.cookieIsolationMode) {
            CookieIsolationMode.TOTAL_ISOLATE -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            CookieIsolationMode.BLOCK_TRACKERS -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
            CookieIsolationMode.FIRST_PARTY_ONLY -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
            CookieIsolationMode.BLOCK_ALL -> ContentBlocking.CookieBehavior.ACCEPT_NONE
        }
        val privateCookieBehavior = if (config.cookieIsolationMode == CookieIsolationMode.BLOCK_ALL) {
            ContentBlocking.CookieBehavior.ACCEPT_NONE
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        }

        runCatching { cb.setEnhancedTrackingProtectionLevel(etpLevel) }
        runCatching { cb.setAntiTracking(antiTracking) }
        runCatching { cb.setStrictSocialTrackingProtection(config.trackingProtectionLevel == TrackingProtectionLevel.STRICT) }
        runCatching { cb.setEmailTrackerBlockingPrivateBrowsing(config.isProtectionEnabled) }
        runCatching { cb.setCookieBehavior(cookieBehavior) }
        runCatching { cb.setCookieBehaviorPrivateMode(privateCookieBehavior) }
        runCatching { rs.setCookieBehaviorOptInPartitioning(config.cookieIsolationMode == CookieIsolationMode.TOTAL_ISOLATE) }
        runCatching { rs.setCookieBehaviorOptInPartitioningPBM(true) }
        runCatching { cb.setCookiePurging(config.cookiePurgingEnabled) }
        runCatching { cb.setQueryParameterStrippingEnabled(config.stripTrackingParams) }
        runCatching { cb.setQueryParameterStrippingPrivateBrowsingEnabled(config.stripTrackingParams) }
        runCatching {
            cb.setBounceTrackingProtectionMode(
                if (config.bounceTrackingProtection) {
                    ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
                } else {
                    ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_DISABLED
                }
            )
        }
        runCatching {
            cb.setSafeBrowsing(
                if (config.safeBrowsingEnabled) ContentBlocking.SafeBrowsing.DEFAULT
                else ContentBlocking.SafeBrowsing.NONE
            )
        }

        runCatching { rs.setGlobalPrivacyControl(config.globalPrivacyControlEnabled) }
        runCatching { rs.setFingerprintingProtection(config.fingerprintingDefense) }
        runCatching { rs.setFingerprintingProtectionPrivateBrowsing(config.fingerprintingDefense) }
        runCatching { rs.setBaselineFingerprintingProtection(config.fingerprintingDefense) }
        runCatching { rs.setLnaBlocking(config.localNetworkProtection) }
        runCatching { rs.setLnaBlockTrackers(config.localNetworkProtection) }
        runCatching {
            rs.setAllowInsecureConnections(
                if (config.httpsOnlyEnabled) GeckoRuntimeSettings.HTTPS_ONLY
                else GeckoRuntimeSettings.ALLOW_ALL
            )
        }

        val dohUri = when (config.dnsOverHttpsMode) {
            DnsOverHttpsMode.OFF -> ""
            DnsOverHttpsMode.CUSTOM -> config.customDohUrl.trim()
            else -> config.dnsOverHttpsMode.resolverUri
        }
        val trrMode = if (config.dnsOverHttpsMode == DnsOverHttpsMode.OFF || dohUri.isBlank()) {
            GeckoRuntimeSettings.TRR_MODE_OFF
        } else if (config.dohStrict) {
            GeckoRuntimeSettings.TRR_MODE_ONLY
        } else {
            GeckoRuntimeSettings.TRR_MODE_FIRST
        }
        runCatching {
            rs.setTrustedRecursiveResolverMode(trrMode)
            if (dohUri.isNotBlank()) {
                rs.setTrustedRecursiveResolverUri(dohUri)
            }
        }

        geckoExtensionsController.updatePrivacyHeaders(
            doNotTrack = config.doNotTrackEnabled,
            globalPrivacyControl = config.globalPrivacyControlEnabled
        )

        activeSessions.forEach { ref ->
            ref.get()?.applyPrivacySignals(
                useTrackingProtection = config.isProtectionEnabled,
                doNotTrack = config.doNotTrackEnabled,
                globalPrivacyControl = config.globalPrivacyControlEnabled
            )
        }
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

    override fun clearCookiesOnly() {
        val cookieFlags = StorageController.ClearFlags.COOKIES or
            StorageController.ClearFlags.DOM_STORAGES
        runtime.storageController.clearData(cookieFlags)
    }

    override fun clearHistoryAndCachesOnly() {
        val cacheFlags = StorageController.ClearFlags.ALL_CACHES or
            StorageController.ClearFlags.NETWORK_CACHE or
            StorageController.ClearFlags.IMAGE_CACHE
        runtime.storageController.clearData(cacheFlags)
    }

    private fun createRuntime(context: Context): GeckoRuntime {
        val contentBlocking = ContentBlocking.Settings.Builder()
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .antiTracking(FULL_ANTI_TRACKING)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookiePurging(true)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .bounceTrackingProtectionMode(
                ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
            )
            .build()

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .automaticFontSizeAdjustment(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .globalPrivacyControlEnabled(true)
            .contentBlocking(contentBlocking)
            .extensionsWebAPIEnabled(true)
            .extensionsProcessEnabled(true)

        // Secure Routing: when a routing profile is active, the generated user.js-style prefs
        // file forces all Gecko page traffic (and DNS) through SOCKS5 (Tor/Orbot, trusted
        // SOCKS5, or an explicitly experimental public relay candidate). GeckoRuntime reads it
        // at creation, so routing changes take effect at next launch; tabs are restored by
        // session hydration.
        val routingPrefsFile = SecureRouting.prefsFile(context)
        if (routingPrefsFile.exists() && routingPrefsFile.length() > 0) {
            Log.i(TAG, "Secure Routing prefs detected: ${routingPrefsFile.name}")
            settingsBuilder.configFilePath(routingPrefsFile.absolutePath)
        }

        val settings = settingsBuilder.build()

        return GeckoRuntime.create(context, settings)
    }

    companion object {
        @Volatile
        private var sharedRuntime: GeckoRuntime? = null

        private const val FULL_ANTI_TRACKING =
            ContentBlocking.AntiTracking.DEFAULT or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
    }
}
