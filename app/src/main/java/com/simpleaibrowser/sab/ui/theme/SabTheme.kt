package com.simpleaibrowser.sab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.R
import com.simpleaibrowser.sab.core.browser.ThemePreset

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

@Immutable
data class SabExtendedColors(
    val chromeBar: Color,
    val chromeSurfaceElevated: Color,
    val chromeBorder: Color,
    val accentGlow: Color,
    val privateAccent: Color,
    val privateSurface: Color,
    val shieldGreen: Color,
    val subtleText: Color
)

val LocalSabColors = staticCompositionLocalOf {
    SabExtendedColors(
        chromeBar = Color(0xFF10141F),
        chromeSurfaceElevated = Color(0xFF191F2E),
        chromeBorder = Color(0xFF262E42),
        accentGlow = Color(0xFFFF2E54),
        privateAccent = Color(0xFFA855F7),
        privateSurface = Color(0xFF1C1329),
        shieldGreen = Color(0xFF10B981),
        subtleText = Color(0xFF94A3B8)
    )
}

private val ObsidianCrimsonScheme = darkColorScheme(
    primary = Color(0xFFFF2E54),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3E101D),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF041D2A),
    secondaryContainer = Color(0xFF122738),
    onSecondaryContainer = Color(0xFFC9ECFF),
    tertiary = Color(0xFFA855F7),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF121622),
    onSurface = Color(0xFFF3F6FC),
    surfaceVariant = Color(0xFF1B2132),
    onSurfaceVariant = Color(0xFFA3B0C6),
    outline = Color(0xFF2B344B),
    error = Color(0xFFFF5C72),
    onError = Color(0xFF2B0008)
)

private val MidnightCyanScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF021A22),
    primaryContainer = Color(0xFF083241),
    onPrimaryContainer = Color(0xFFCCF5FF),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF132645),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF022016),
    background = Color(0xFF080D16),
    onBackground = Color(0xFFF0F6FF),
    surface = Color(0xFF0F1726),
    onSurface = Color(0xFFF0F6FF),
    surfaceVariant = Color(0xFF18243A),
    onSurfaceVariant = Color(0xFF94A8C4),
    outline = Color(0xFF263654),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2A0007)
)

private val AuroraVelvetScheme = darkColorScheme(
    primary = Color(0xFFE056FD),
    onPrimary = Color(0xFF1F0327),
    primaryContainer = Color(0xFF3A1348),
    onPrimaryContainer = Color(0xFFF8D6FF),
    secondary = Color(0xFFFF5E7E),
    onSecondary = Color(0xFF26030C),
    secondaryContainer = Color(0xFF401220),
    onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF041D2A),
    background = Color(0xFF0E0A16),
    onBackground = Color(0xFFF7F2FF),
    surface = Color(0xFF171124),
    onSurface = Color(0xFFF7F2FF),
    surfaceVariant = Color(0xFF231A36),
    onSurfaceVariant = Color(0xFFB3A4CC),
    outline = Color(0xFF362852),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2B0009)
)

private val DaylightCleanScheme = lightColorScheme(
    primary = Color(0xFFE11D48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4E9),
    onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAEEF6),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFD5DCE8),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val SabTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

private val SabShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SabBrowserTheme(
    preset: ThemePreset = ThemePreset.OBSIDIAN_CRIMSON,
    content: @Composable () -> Unit
) {
    val colorScheme = when (preset) {
        ThemePreset.OBSIDIAN_CRIMSON -> ObsidianCrimsonScheme
        ThemePreset.MIDNIGHT_CYAN -> MidnightCyanScheme
        ThemePreset.AURORA_VELVET -> AuroraVelvetScheme
        ThemePreset.DAYLIGHT_CLEAN -> DaylightCleanScheme
    }

    val extended = when (preset) {
        ThemePreset.OBSIDIAN_CRIMSON -> SabExtendedColors(
            chromeBar = Color(0xFF0F131E),
            chromeSurfaceElevated = Color(0xFF181E2D),
            chromeBorder = Color(0xFF273046),
            accentGlow = Color(0xFFFF2E54),
            privateAccent = Color(0xFFA855F7),
            privateSurface = Color(0xFF1B1228),
            shieldGreen = Color(0xFF10B981),
            subtleText = Color(0xFF94A3B8)
        )
        ThemePreset.MIDNIGHT_CYAN -> SabExtendedColors(
            chromeBar = Color(0xFF0C1320),
            chromeSurfaceElevated = Color(0xFF152136),
            chromeBorder = Color(0xFF233552),
            accentGlow = Color(0xFF00D4FF),
            privateAccent = Color(0xFFA855F7),
            privateSurface = Color(0xFF17132B),
            shieldGreen = Color(0xFF10B981),
            subtleText = Color(0xFF8FA5C4)
        )
        ThemePreset.AURORA_VELVET -> SabExtendedColors(
            chromeBar = Color(0xFF140E20),
            chromeSurfaceElevated = Color(0xFF201733),
            chromeBorder = Color(0xFF34264F),
            accentGlow = Color(0xFFE056FD),
            privateAccent = Color(0xFFC084FC),
            privateSurface = Color(0xFF221338),
            shieldGreen = Color(0xFF34D399),
            subtleText = Color(0xFFA99AC4)
        )
        ThemePreset.DAYLIGHT_CLEAN -> SabExtendedColors(
            chromeBar = Color(0xFFFFFFFF),
            chromeSurfaceElevated = Color(0xFFF1F4FA),
            chromeBorder = Color(0xFFDCE3EF),
            accentGlow = Color(0xFFE11D48),
            privateAccent = Color(0xFF7C3AED),
            privateSurface = Color(0xFFF3E8FF),
            shieldGreen = Color(0xFF059669),
            subtleText = Color(0xFF64748B)
        )
    }

    CompositionLocalProvider(LocalSabColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SabTypography,
            shapes = SabShapes,
            content = content
        )
    }
}
