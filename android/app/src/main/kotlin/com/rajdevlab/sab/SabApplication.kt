package com.rajdevlab.sab

import android.app.Application
import com.rajdevlab.sab.core.browser.BrowserEngine
import com.rajdevlab.sab.engine.gecko.GeckoEngineAdapter

class SabApplication : Application() {
    lateinit var browserEngine: BrowserEngine
        private set

    override fun onCreate() {
        super.onCreate()
        browserEngine = GeckoEngineAdapter(applicationContext)
    }
}
