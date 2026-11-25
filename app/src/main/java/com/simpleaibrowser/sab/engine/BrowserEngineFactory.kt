package com.simpleaibrowser.sab.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.engine.compat.CompatWebViewEngine
import com.simpleaibrowser.sab.engine.gecko.GeckoEngineAdapter

private const val TAG = "SabEngineFactory"

/**
 * Instantiates the Mozilla GeckoView 161.0 (non-omni, arm64-v8a) engine first.
 * Only if the runtime host physically lacks ARM64 execution (e.g., an x86_64
 * preview container without ARM translation) does it gracefully switch to
 * CompatWebViewEngine so the Opera-inspired UI shell remains fully operational.
 */
object BrowserEngineFactory {
    @Volatile
    private var cachedEngine: BrowserEngine? = null

    fun getOrCreate(context: Context): BrowserEngine {
        return cachedEngine ?: synchronized(this) {
            cachedEngine ?: createInternal(context.applicationContext).also { cachedEngine = it }
        }
    }

    private fun createInternal(appContext: Context): BrowserEngine {
        val supportsArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
        if (!supportsArm64) {
            Log.w(TAG, "Host ABIs ${Build.SUPPORTED_ABIS.joinToString()} do not list arm64-v8a; using CompatWebViewEngine.")
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
