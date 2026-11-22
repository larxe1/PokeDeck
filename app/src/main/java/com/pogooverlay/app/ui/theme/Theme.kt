package com.pogooverlay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PokedexBackground = Color(0xFFF7F8FA)
val PokedexSurface = Color(0xFFFFFFFF)
val PokedexInk = Color(0xFF1A1C20)
val PokedexMuted = Color(0xFF6E7582)
val PokedexGold = Color(0xFFF7CF31)
val PokedexBorder = Color(0xFFECEFF4)
val PokedexDanger = Color(0xFFE5484D)
val PokedexSuccess = Color(0xFF22A06B)
val PokedexSubtle = Color(0xFFF1F3F7)

private val LightPokedexColorScheme = lightColorScheme(
    primary = PokedexInk,
    onPrimary = PokedexSurface,
    secondary = PokedexGold,
    onSecondary = PokedexInk,
    tertiary = PokedexSuccess,
    onTertiary = PokedexSurface,
    background = PokedexBackground,
    onBackground = PokedexInk,
    surface = PokedexSurface,
    onSurface = PokedexInk,
    surfaceVariant = PokedexSubtle,
    onSurfaceVariant = PokedexMuted,
    outline = PokedexBorder,
    outlineVariant = PokedexBorder,
    error = PokedexDanger,
    onError = PokedexSurface,
)

private val PokedexTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        color = PokedexInk,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        color = PokedexInk,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = PokedexInk,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        color = PokedexInk,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = PokedexMuted,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        color = PokedexInk,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        color = PokedexMuted,
    ),
)

@Composable
fun PoGoOverlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightPokedexColorScheme,
        typography = PokedexTypography,
        content = content,
    )
}
