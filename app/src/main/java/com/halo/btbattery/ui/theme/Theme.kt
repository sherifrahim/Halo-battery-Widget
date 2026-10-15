package com.halo.btbattery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.halo.btbattery.model.ThemeMode

object Fluid {
    val Mint = Color(0xFF2FE6A6)
    val Aqua = Color(0xFF2BC8FF)
    val Violet = Color(0xFF8C7BFF)
    val Charge = Color(0xFF3BEA7C)
    val Amber = Color(0xFFFF9F43)
    val Coral = Color(0xFFFF4D64)
}

/** Glass and ink tones that flip with the theme. */
@Immutable
data class FluidTones(
    val dark: Boolean,
    val base: Color,
    val ink: Color,
    val inkSoft: Color,
    val glass: Color,
    val glassStrong: Color,
    val edgeTop: Color,
    val edgeBottom: Color,
    val orbStrength: Float,
)

private val DarkTones = FluidTones(
    dark = true,
    base = Color(0xFF07090D),
    ink = Color(0xFFF4F6FA),
    inkSoft = Color(0x99F4F6FA),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x24FFFFFF),
    edgeTop = Color(0x3DFFFFFF),
    edgeBottom = Color(0x0AFFFFFF),
    orbStrength = 0.55f,
)

private val LightTones = FluidTones(
    dark = false,
    base = Color(0xFFF1F4F8),
    ink = Color(0xFF0F1217),
    inkSoft = Color(0x8C0F1217),
    glass = Color(0xA6FFFFFF),
    glassStrong = Color(0xD9FFFFFF),
    edgeTop = Color(0xE6FFFFFF),
    edgeBottom = Color(0x14000000),
    orbStrength = 0.38f,
)

val LocalTones = staticCompositionLocalOf { DarkTones }

private val HaloType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun HaloTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val tones = if (dark) DarkTones else LightTones
    val scheme = if (dark) {
        darkColorScheme(primary = Fluid.Mint, secondary = Fluid.Aqua, background = tones.base, surface = Color(0xFF14171D), onSurface = tones.ink, onBackground = tones.ink)
    } else {
        lightColorScheme(primary = Color(0xFF0FB985), secondary = Fluid.Aqua, background = tones.base, surface = Color.White, onSurface = tones.ink, onBackground = tones.ink)
    }
    CompositionLocalProvider(LocalTones provides tones) {
        MaterialTheme(colorScheme = scheme, typography = HaloType, content = content)
    }
}
