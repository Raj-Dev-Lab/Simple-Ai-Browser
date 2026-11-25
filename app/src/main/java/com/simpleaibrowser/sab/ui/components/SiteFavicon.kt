package com.simpleaibrowser.sab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.ui.theme.SpaceGroteskFamily

/**
 * Loads a site's crisp favicon via Coil with an instant monogram fallback when
 * offline, loading, or showing internal SAB pages.
 */
@Composable
fun SiteFavicon(
    url: String,
    title: String,
    accentColor: Color,
    size: Dp = 32.dp,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = 10.dp,
    monogramSize: TextUnit = 15.sp,
    containerColor: Color = accentColor.copy(alpha = 0.14f),
    modifier: Modifier = Modifier
) {
    val faviconUrl = UrlResolver.faviconUrl(url)
    val monogram = UrlResolver.extractMonogram(title.ifBlank { UrlResolver.extractHost(url) })

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (faviconUrl == null) {
            MonogramGlyph(monogram = monogram, accentColor = accentColor, fontSize = monogramSize)
        } else {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(6.dp)),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MonogramGlyph(monogram = monogram, accentColor = accentColor, fontSize = monogramSize)
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MonogramGlyph(monogram = monogram, accentColor = accentColor, fontSize = monogramSize)
                    }
                }
            )
        }
    }
}

@Composable
private fun MonogramGlyph(
    monogram: String,
    accentColor: Color,
    fontSize: TextUnit
) {
    Text(
        text = monogram,
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = accentColor
    )
}
