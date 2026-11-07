package com.openweibo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.openweibo.app.R

@Immutable
data class AppPalette(
    val bg: Color,
    val chrome: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val dangerSoft: Color,
    val onDanger: Color,
    val highlight: Color,
    val scrim: Color
)

val OpenPalette = AppPalette(
    bg = Color(0xFF080809),
    chrome = Color(0xFF0C0C0E),
    surface = Color(0xFF121214),
    surfaceAlt = Color(0xFF18181B),
    surfaceRaised = Color(0xFF1D1D21),
    border = Color(0xFF2A2A2F),
    borderSoft = Color(0xFF1D1D21),
    text = Color(0xFFF0F0F1),
    textDim = Color(0xFFA2A2A9),
    textFaint = Color(0xFF6C6C74),
    accent = Color(0xFFF0F0F1),
    onAccent = Color(0xFF080809),
    success = Color(0xFF8BE28B),
    danger = Color(0xFFFF7474),
    dangerSoft = Color(0xFF2A1717),
    onDanger = Color(0xFF210808),
    highlight = Color(0xFFF2B95C),
    scrim = Color(0xE6000000)
)

val LightPalette = AppPalette(
    bg = Color(0xFFF6F6F3),
    chrome = Color(0xFFEDEDE8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F1EC),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFD6D6CF),
    borderSoft = Color(0xFFE5E5DE),
    text = Color(0xFF171719),
    textDim = Color(0xFF55555B),
    textFaint = Color(0xFF85858C),
    accent = Color(0xFF171719),
    onAccent = Color(0xFFFAFAF7),
    success = Color(0xFF2F8F46),
    danger = Color(0xFFC63B3B),
    dangerSoft = Color(0xFFFBE7E7),
    onDanger = Color(0xFFFFFFFF),
    highlight = Color(0xFF9A6200),
    scrim = Color(0x99000000)
)

val LocalPalette = staticCompositionLocalOf { OpenPalette }

object AppTheme {
    val colors: AppPalette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current
}

val Geist = FontFamily(
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.SemiBold)
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono, FontWeight.Medium)
)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.9).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.5.sp,
        letterSpacing = 0.05.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.7.sp
    )
)

fun monoStyle(
    size: Number = 10,
    weight: FontWeight = FontWeight.Medium,
    letterSpacing: Double = 0.6
): TextStyle = TextStyle(
    fontFamily = GeistMono,
    fontWeight = weight,
    fontSize = size.toFloat().sp,
    letterSpacing = letterSpacing.sp
)

@Composable
fun OpenWeiboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) OpenPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.surfaceRaised,
            onPrimaryContainer = palette.text,
            secondary = palette.textDim,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textDim,
            outline = palette.border,
            outlineVariant = palette.borderSoft,
            error = palette.danger,
            onError = palette.onDanger,
            scrim = palette.scrim
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.surfaceRaised,
            onPrimaryContainer = palette.text,
            secondary = palette.textDim,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textDim,
            outline = palette.border,
            outlineVariant = palette.borderSoft,
            error = palette.danger,
            onError = palette.onDanger,
            scrim = palette.scrim
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}
