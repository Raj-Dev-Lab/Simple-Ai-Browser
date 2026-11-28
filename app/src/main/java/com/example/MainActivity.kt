package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.simpleaibrowser.sab.core.browser.BootGuard
import com.simpleaibrowser.sab.downloads.SabDownloadService
import com.simpleaibrowser.sab.ui.SabBrowserApp
import com.simpleaibrowser.sab.ui.viewmodel.BrowserOverlay
import com.simpleaibrowser.sab.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BootGuard: count this boot BEFORE the ViewModel (and thus the engine) is created.
        // If the process dies before focus is ever gained, the counter persists and the next
        // launch enters safe mode (removes the Secure Routing prefs file) to break the loop.
        BootGuard.recordBootStart(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        if (consumeOpenDownloadsExtra(intent)) {
            browserViewModel.openOverlay(BrowserOverlay.DownloadManager())
        }
        val incomingUrl = consumeIncomingUrl(intent)

        setContent {
            SabBrowserApp(
                viewModel = browserViewModel,
                initialExternalUrl = incomingUrl
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Reaching window focus means the app is genuinely interactive: boot succeeded.
        if (hasFocus) {
            BootGuard.markInteractive(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (consumeOpenDownloadsExtra(intent)) {
            browserViewModel.openOverlay(BrowserOverlay.DownloadManager())
        }
        consumeIncomingUrl(intent)?.let { url ->
            browserViewModel.handleIncomingExternalUri(url)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            browserViewModel.handleAppExitCleanup()
        }
        super.onDestroy()
    }

    private fun consumeOpenDownloadsExtra(intent: Intent?): Boolean {
        val flag = intent?.getBooleanExtra(SabDownloadService.EXTRA_OPEN_DOWNLOADS, false) == true
        if (flag) {
            intent?.removeExtra(SabDownloadService.EXTRA_OPEN_DOWNLOADS)
        }
        return flag
    }

    private fun consumeIncomingUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.dataString
            intent.action = null
            intent.data = null
            return uri
        }
        return null
    }
}
