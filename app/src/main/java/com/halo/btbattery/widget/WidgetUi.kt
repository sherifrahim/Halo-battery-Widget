package com.halo.btbattery.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.halo.btbattery.R
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.SurfaceStyle
import com.halo.btbattery.model.ThemeMode
import androidx.glance.color.ColorProvider as DayNightColor

/** Colours and surfaces sampled from the ColorOS 17 "My Devices" widgets. */
internal class WidgetLook(
    val title: ColorProvider,
    val subtitle: ColorProvider,
    val badgeText: ColorProvider,
    val container: ImageProvider,
    val tile: ImageProvider,
    val top: ImageProvider,
    val chip: ImageProvider,
    val badge: ImageProvider,
    val accent: AccentStyle,
)

private val TitleDark = Color(0xFFEEEEEE)
private val SubDark = Color(0xFFAAAAAA)
private val TitleLight = Color(0xFF1A1A1A)
private val SubLight = Color(0xFF8A8A8E)

internal fun widgetLook(settings: Settings): WidgetLook {
    val glass = settings.surface == SurfaceStyle.GLASS
    fun pick(auto: Int, light: Int, dark: Int) = ImageProvider(
        when (settings.theme) { ThemeMode.SYSTEM -> auto; ThemeMode.LIGHT -> light; ThemeMode.DARK -> dark },
    )
    fun color(light: Color, dark: Color) = when (settings.theme) {
        ThemeMode.SYSTEM -> DayNightColor(day = light, night = dark)
        ThemeMode.LIGHT -> ColorProvider(light)
        ThemeMode.DARK -> ColorProvider(dark)
    }
    return WidgetLook(
        title = color(TitleLight, TitleDark),
        subtitle = color(SubLight, SubDark),
        badgeText = color(Color.White, Color(0xFF474747)),
        container = if (glass) pick(R.drawable.widget_bg_glass, R.drawable.widget_bg_glass_light, R.drawable.widget_bg_glass_dark)
        else pick(R.drawable.widget_bg_solid, R.drawable.widget_bg_solid_light, R.drawable.widget_bg_solid_dark),
        tile = pick(R.drawable.widget_tile, R.drawable.widget_tile_light, R.drawable.widget_tile_dark),
        top = pick(R.drawable.widget_top, R.drawable.widget_top_light, R.drawable.widget_top_dark),
        chip = pick(R.drawable.widget_chip, R.drawable.widget_chip_light, R.drawable.widget_chip_dark),
        badge = pick(R.drawable.widget_badge, R.drawable.widget_badge_light, R.drawable.widget_badge_dark),
        accent = settings.accent,
    )
}

/** Device icon inside its battery ring; a plain circle when the level is unknown. */
@Composable
internal fun RingIcon(g: Gauge, ringDp: Dp, look: WidgetLook, iconRatio: Float = 0.5f) {
    val context = LocalContext.current
    val tint = if (g.stale) look.subtitle else look.title
    if (g.level == null) {
        Box(GlanceModifier.size(ringDp).background(look.chip), contentAlignment = Alignment.Center) {
            Image(ImageProvider(g.icon), g.label, GlanceModifier.size(ringDp * iconRatio), colorFilter = ColorFilter.tint(tint))
        }
        return
    }
    val bmp = GaugeRenderer.ring(context, ringDp.value, g.level, g.charging, g.stale, look.accent)
    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), null, GlanceModifier.size(ringDp))
        Image(ImageProvider(g.icon), g.label, GlanceModifier.size(ringDp * iconRatio), colorFilter = ColorFilter.tint(tint))
    }
}

/** Circled letter, as in "Ⓛ100 Ⓡ100". */
@Composable
internal fun LetterBadge(letter: String, look: WidgetLook, sizeDp: Float) {
    Box(GlanceModifier.size(sizeDp.dp).background(look.badge), contentAlignment = Alignment.Center) {
        Text(letter, style = TextStyle(color = look.badgeText, fontSize = (sizeDp * 0.62f).sp, fontWeight = FontWeight.Bold))
    }
}

/** The "▭100 Ⓛ100 Ⓡ100" line; case only when [withCase]. */
@Composable
internal fun BudsLine(row: DeviceRow, look: WidgetLook, fontSp: Float, withCase: Boolean, color: ColorProvider = look.subtitle) {
    val left = row.gauges.getOrNull(0)
    val right = row.gauges.getOrNull(1)
    val case = row.gauges.getOrNull(2)
    val badge = fontSp * 1.05f
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withCase && case?.level != null) {
            Image(ImageProvider(R.drawable.ic_case), "Case", GlanceModifier.size((badge * 1.15f).dp), colorFilter = ColorFilter.tint(look.subtitle))
            Text(case.level.toString(), style = TextStyle(color = color, fontSize = fontSp.sp))
            Spacer(GlanceModifier.width((fontSp * 0.5f).dp))
        }
        if (left?.level != null) {
            LetterBadge("L", look, badge)
            Text(left.level.toString(), style = TextStyle(color = color, fontSize = fontSp.sp))
            Spacer(GlanceModifier.width((fontSp * 0.5f).dp))
        }
        if (right?.level != null) {
            LetterBadge("R", look, badge)
            Text(right.level.toString(), style = TextStyle(color = color, fontSize = fontSp.sp))
        }
    }
}

/** Second line of a tile: L/R badges for earbuds, "Battery: 67%" otherwise. */
@Composable
internal fun StatusLine(row: DeviceRow, look: WidgetLook, fontSp: Float) {
    val g = row.gauges.first()
    when {
        row.gauges.size > 1 && row.connected -> BudsLine(row, look, fontSp, withCase = false)
        !row.connected -> Text(row.status, style = TextStyle(color = look.subtitle, fontSize = fontSp.sp), maxLines = 1)
        g.level == null -> Text("Connected", style = TextStyle(color = look.subtitle, fontSize = fontSp.sp), maxLines = 1)
        g.charging -> Text("Charging: ${g.level}%", style = TextStyle(color = look.subtitle, fontSize = fontSp.sp), maxLines = 1)
        else -> Text("Battery: ${g.level}%", style = TextStyle(color = look.subtitle, fontSize = fontSp.sp), maxLines = 1)
    }
}

/** The ring a whole device is summarised by: the weaker bud for earbuds. */
internal fun DeviceRow.headline(): Gauge {
    if (gauges.size == 1) return gauges.first()
    val buds = gauges.take(2).filter { it.level != null }
    val weakest = buds.minByOrNull { it.level!! } ?: gauges.first()
    return weakest.copy(icon = icon, label = title)
}

@Composable
internal fun WidgetMessage(text: String, look: WidgetLook) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(44.dp).background(look.chip), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_bluetooth), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(look.title))
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(text, style = TextStyle(color = look.subtitle, fontSize = 13.sp, textAlign = TextAlign.Center), maxLines = 2)
    }
}
