package com.example

import android.app.Application
import android.util.Log
import com.simpleaibrowser.sab.core.browser.CrashGuard

class SabApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        Log.i("SabApp", "SAB Application started (crash guard installed)")
    }
}
