package com.simpleaibrowser.sab.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.simpleaibrowser.sab.core.browser.BootGuard
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.engine.compat.CompatWebViewEngine
import com.simpleaibrowser.sab.engine.gecko.GeckoEngineAdapter

private const val TAG = "SabEngineFactory"

/**
 * Instantiates Mozilla GeckoView 161 (omni artifact: arm64-v8a for Android 10+ devices,
 * x86_64 for emulators) natively.
 * Falls back to CompatWebViewEngine only if an unsupported ABI or native
 * runtime initialization error occurs.
 */
object BrowserEngineFactory {
    @Volatile
    private var cachedEngine: BrowserEngine? = null

    private val supportedGeckoAbis = setOf("arm64-v8a", "x86_64")

    fun getOrCreate(context: Context): BrowserEngine {
        return cachedEngine ?: synchronized(this) {
            cachedEngine ?: createInternal(context.applicationContext).also { cachedEngine = it }
        }
    }

    private fun createInternal(appContext: Context): BrowserEngine {
        // BootGuard escalation: after two consecutive crash loops where safe mode (removing
        // the routing prefs file) did not cure the startup crashes, stop creating GeckoView
        // entirely and run on the WebView fallback until the user re-enables it.
        if (BootGuard.isEngineLockout(appContext)) {
            Log.w(TAG, "Engine lockout active after repeated startup crash loops; using CompatWebViewEngine.")
            return CompatWebViewEngine(
                context = appContext,
                fallbackReason = "Engine lockout (startup crash recovery)"
            )
        }
        val hasSupportedAbi = Build.SUPPORTED_ABIS.any { abi ->
            supportedGeckoAbis.any { it.equals(abi, ignoreCase = true) }
        }
        if (!hasSupportedAbi) {
            Log.w(TAG, "Host ABIs ${Build.SUPPORTED_ABIS.joinToString()} not in $supportedGeckoAbis; using CompatWebViewEngine.")
            return CompatWebViewEngine(
                context = appContext,
                fallbackReason = "Host ABIs: ${Build.SUPPORTED_ABIS.joinToString()}"
            )
        }
        return try {
            GeckoEngineAdapter(appContext).also {
                Log.i(TAG, "Initialized ${it.engineInfo.name} ${it.engineInfo.version} on ${it.engineInfo.abi}")
            }
        } catch (err: Throwable) {
            Log.e(TAG, "GeckoView initialization failed on host (${err.javaClass.simpleName}): ${err.message}", err)
            CompatWebViewEngine(
                context = appContext,
                fallbackReason = err.javaClass.simpleName
            )
        }
    }
}
