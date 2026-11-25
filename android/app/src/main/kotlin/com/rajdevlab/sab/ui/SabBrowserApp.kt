package com.rajdevlab.sab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rajdevlab.sab.core.browser.BrowserEngine
import com.rajdevlab.sab.core.browser.BrowserTabSession
import com.rajdevlab.sab.engine.gecko.GeckoTabSession
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView

private val Obsidian = Color(0xFF0B0D12)
private val ChromeSurface = Color(0xFF141821)
private val SabCrimson = Color(0xFFFF2E4C)

@Composable
fun SabBrowserApp(
    engine: BrowserEngine,
    initialUri: String? = null
) {
    var showSplash by remember { mutableStateOf(true) }
    val sessions = remember {
        mutableStateListOf(engine.createSession("tab-1", isPrivate = false))
    }
    var activeIndex by remember { mutableStateOf(0) }
    val activeSession: BrowserTabSession = sessions[activeIndex]
    val uiState by activeSession.state.collectAsState()

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank()) {
            activeSession.loadUrl(initialUri)
        }
    }

    LaunchedEffect(Unit) {
        delay(950)
        showSplash = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Compact Address Strip
            Surface(color = ChromeSurface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.url == "sab://start") "Simple AI Browser" else uiState.url,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    if (uiState.isLoading) {
                        LinearProgressIndicator(
                            progress = { uiState.progress / 100f },
                            color = SabCrimson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    }
                }
            }

            // GeckoView 159+ Rendering Viewport
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val geckoTab = activeSession as? GeckoTabSession
                if (geckoTab != null && uiState.url != "sab://start") {
                    AndroidView(
                        factory = { ctx ->
                            GeckoView(ctx).apply {
                                setSession(geckoTab.geckoSession)
                            }
                        },
                        update = { view ->
                            if (view.session != geckoTab.geckoSession) {
                                view.setSession(geckoTab.geckoSession)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SpeedDialStartPage(
                        onOpenUrl = { url -> activeSession.loadUrl(url) }
                    )
                }
            }

            // Opera-Style 5-Slot Bottom Navigation Bar
            Surface(
                color = ChromeSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (uiState.canGoBack) activeSession.goBack() },
                        enabled = uiState.canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    IconButton(onClick = { activeSession.loadUrl("https://duckduckgo.com") }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }

                    // Central SAB AI Orb
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SabCrimson)
                            .clickable { /* Opens SAB AI Sheet */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "SAB AI", tint = Color.White)
                    }

                    // Tab Count
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sessions.size.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { /* Opens Opera Menu Sheet */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                }
            }
        }

        // In-App SAB Splash Intro
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Obsidian),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(SabCrimson),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SAB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Simple AI Browser", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("GeckoView 159+ · arm64-v8a", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SpeedDialStartPage(onOpenUrl: (String) -> Unit) {
    val shortcuts = listOf(
        "Wikipedia" to "https://en.wikipedia.org",
        "GitHub" to "https://github.com",
        "DuckDuckGo" to "https://duckduckgo.com",
        "arXiv" to "https://arxiv.org"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Speed Dial", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        shortcuts.forEach { (name, url) ->
            Surface(
                color = ChromeSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUrl(url) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Icon(Icons.Default.Home, contentDescription = null, tint = SabCrimson)
                }
            }
        }
    }
}
