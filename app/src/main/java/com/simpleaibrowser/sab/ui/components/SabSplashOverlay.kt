package com.simpleaibrowser.sab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch

@Composable
fun SabSplashOverlay(
    visible: Boolean,
    engineInfo: EngineInfo,
    onFinished: () -> Unit
) {
    val ringSweep = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.74f) }
    val logoRotation = remember { Animatable(-18f) }
    val logoAlpha = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(18f) }
    val auraScale = remember { Animatable(0.6f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        ringSweep.snapTo(0f)
        logoScale.snapTo(0.74f)
        logoRotation.snapTo(-18f)
        logoAlpha.snapTo(0f)
        wordmarkAlpha.snapTo(0f)
        wordmarkOffsetY.snapTo(18f)
        auraScale.snapTo(0.6f)

        val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

        launch {
            auraScale.animateTo(1.15f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            ringSweep.animateTo(360f, tween(760, easing = easeOutExpo))
        }
        launch {
            delay(90)
            launch { logoAlpha.animateTo(1f, tween(360)) }
            launch {
                logoScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                logoRotation.animateTo(0f, tween(580, easing = easeOutExpo))
            }
        }
        launch {
            delay(340)
            launch { wordmarkAlpha.animateTo(1f, tween(420)) }
            launch { wordmarkOffsetY.animateTo(0f, tween(460, easing = easeOutExpo)) }
        }

        delay(1450)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(340)) + scaleOut(targetScale = 1.06f, animationSpec = tween(340))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080C15))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                )
                .testTag("sab_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            // Atmospheric crimson + cobalt radial glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.44f)
                val radius = size.minDimension * 0.62f * auraScale.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x38FF2E54),
                            Color(0x1838BDF8),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Box(
                    modifier = Modifier.size(164.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Animated orbital precision sweep ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 4.dp.toPx()
                        val inset = strokePx * 1.5f
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0x00FF2E54),
                                    Color(0xFFFF2E54),
                                    Color(0xFFFF7A90),
                                    Color(0xFF38BDF8),
                                    Color(0xFFFF2E54)
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = ringSweep.value,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    // SAB Prism Emblem
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .scale(logoScale.value)
                            .rotate(logoRotation.value)
                            .alpha(logoAlpha.value)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_sab_logo),
                            contentDescription = "Simple AI Browser emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(wordmarkAlpha.value)
                        .offset(y = wordmarkOffsetY.value.dp)
                ) {
                    Text(
                        text = "SAB",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        letterSpacing = 6.sp,
                        color = Color(0xFFF6F8FD)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SIMPLE AI BROWSER",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF131927))
                            .border(1.dp, Color(0xFF273147), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (engineInfo.isGeckoViewActive) Color(0xFF10B981)
                                    else Color(0xFF38BDF8)
                                )
                        )
                        Text(
                            text = if (engineInfo.isGeckoViewActive) {
                                "GECKOVIEW 161 • ${engineInfo.abi.uppercase()} ENGINE"
                            } else {
                                "GECKOVIEW 161 ARCH • READY"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}
