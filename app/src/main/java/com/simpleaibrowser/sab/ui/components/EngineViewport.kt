package com.simpleaibrowser.sab.ui.components

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.simpleaibrowser.sab.core.browser.BrowserEngine
import com.simpleaibrowser.sab.core.browser.BrowserTabSession

/**
 * Engine-agnostic native viewport surface.
 * Hosts GeckoView on ARM64 Android 10+ devices without leaking any GeckoView
 * import into the Jetpack Compose UI layer.
 */
@Composable
fun EngineViewport(
    engine: BrowserEngine,
    session: BrowserTabSession,
    modifier: Modifier = Modifier
) {
    var hostView by remember { mutableStateOf<View?>(null) }

    DisposableEffect(session, hostView) {
        val currentView = hostView
        if (currentView != null) {
            session.attachToView(currentView)
        }
        onDispose {
            if (currentView != null) {
                session.detachFromView(currentView)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            engine.createRenderView(ctx).also { created ->
                hostView = created
                session.attachToView(created)
            }
        },
        update = { view ->
            hostView = view
            session.attachToView(view)
        },
        modifier = modifier.fillMaxSize()
    )
}
