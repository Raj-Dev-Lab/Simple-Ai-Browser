package com.simpleaibrowser.sab.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.engine.compat.CompatWebViewEngine
import com.simpleaibrowser.sab.engine.gecko.GeckoEngineAdapter

private const val TAG = "SabEngineFactory"

/**
 * Instantiates Mozilla GeckoView 161.0 (non-omni) natively on both
 * arm64-v8a (Android 10+ devices) and x86_64 (emulators).
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
