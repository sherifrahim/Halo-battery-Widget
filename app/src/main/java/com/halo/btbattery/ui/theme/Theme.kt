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
    val Green = Color(0xFF22C063)
    val GreenLight = Color(0xFF52DB8C)
    val Amber = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    /** ColorOS system accent (sampled from the system "Add" button). */
    val Blue = Color(0xFF198CFF)
    val Violet = Color(0xFF8C6CFF)
}

/**
 * Surface and ink tones. Neutral canvas, translucent cards lit from above: a bright hairline on
 * the top edge, a faint reflection at the bottom, and (in light mode) a soft shadow at the base.
 */
@Immutable
data class FluidTones(
    val dark: Boolean,
    val base: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val card: Color,
    val raised: Color,
    val edgeTop: Color,
    val edgeBottom: Color,
    val divider: Color,
    val well: Color,
    val bar: Color,
    val touchGlow: Color,
)

private val Light = FluidTones(
    dark = false,
    base = Color(0xFFF2F3F5),
    ink = Color(0xFF1A1A1A),
    inkSoft = Color(0x8C1A1A1A),
    inkFaint = Color(0x4D1A1A1A),
    card = Color(0xD9FFFFFF),
    raised = Color(0xFFFFFFFF),
    edgeTop = Color(0xFFFFFFFF),
    edgeBottom = Color(0x0F000000),
    divider = Color(0x12000000),
    well = Color(0x0D000000),
    bar = Color(0xF5FFFFFF),
    touchGlow = Color(0x40FFFFFF),
)

private val Dark = FluidTones(
    dark = true,
    base = Color(0xFF000000),
    ink = Color(0xFFF2F2F2),
    inkSoft = Color(0x8CF2F2F2),
    inkFaint = Color(0x4DF2F2F2),
    card = Color(0xC71C1C1E),
    raised = Color(0xFF3A3A3C),
    edgeTop = Color(0x2EFFFFFF),
    edgeBottom = Color(0x0AFFFFFF),
    divider = Color(0x14FFFFFF),
    well = Color(0x17FFFFFF),
    bar = Color(0xF22A2A2D),
    touchGlow = Color(0x1FFFFFFF),
)

val LocalTones = staticCompositionLocalOf { Dark }
val LocalAccent = staticCompositionLocalOf { Fluid.Blue }

// The system font: on ColorOS this resolves to OPPO Sans, so text matches the rest of the phone.
private val HaloType = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun HaloTheme(mode: ThemeMode, accent: Color = Fluid.Blue, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val tones = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = accent, background = tones.base, surface = Color(0xFF1C1C1E), onSurface = tones.ink, onBackground = tones.ink)
    } else {
        lightColorScheme(primary = accent, background = tones.base, surface = Color.White, onSurface = tones.ink, onBackground = tones.ink)
    }
    CompositionLocalProvider(LocalTones provides tones, LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = HaloType, content = content)
    }
}
