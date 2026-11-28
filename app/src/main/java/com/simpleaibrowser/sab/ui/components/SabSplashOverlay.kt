package com.simpleaibrowser.sab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.delay

private val SplashBackdrop = Color(0xFF080C15)
private val SplashAccent = Color(0xFFFF2E54)
private val SplashTrack = Color(0xFF1B2233)

/**
 * SAB launch loading screen.
 *
 * Deliberately simple and natural (replaced the old choreographed orbit-ring animation, which
 * users experienced as laggy): a static emblem with a soft breathing pulse, the wordmark, and
 * an indeterminate Material progress bar. Everything is GPU-composited (one alpha layer + the
 * built-in indicator), so there is no custom Canvas work, no shader allocation and no
 * recomposition while it is visible. It hides itself automatically after a short, fixed
 * warm-up window, or immediately on tap.
 */
@Composable
fun SabSplashOverlay(
    visible: Boolean,
    engineInfo: EngineInfo,
    onFinished: () -> Unit
) {
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(900)
        onFinished()
    }

    val breathing = rememberInfiniteTransition(label = "sabSplashBreath")
    val emblemAlpha by breathing.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(820),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sabSplashBreathAlpha"
    )

    val engineStatusLabel = remember(engineInfo.isGeckoViewActive, engineInfo.abi) {
        if (engineInfo.isGeckoViewActive) {
            "GECKOVIEW 161 • ${engineInfo.abi.uppercase()} ENGINE"
        } else {
            "GECKOVIEW 161 ARCH • READY"
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(240))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                )
                .testTag("sab_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sab_logo),
                    contentDescription = "Simple AI Browser emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer { alpha = emblemAlpha }
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SAB",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = 6.sp,
                    color = Color(0xFFF6F8FD)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SIMPLE AI BROWSER",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(26.dp))

                LinearProgressIndicator(
                    modifier = Modifier
                        .width(184.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = SplashAccent,
                    trackColor = SplashTrack
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = engineStatusLabel,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.alpha(0.9f)
                )
            }
        }
    }
}
