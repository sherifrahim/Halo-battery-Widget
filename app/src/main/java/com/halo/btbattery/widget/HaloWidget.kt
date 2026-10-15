package com.halo.btbattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.halo.btbattery.R
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.model.SurfaceStyle
import com.halo.btbattery.model.ThemeMode
import com.halo.btbattery.ui.MainActivity
import com.halo.btbattery.work.RefreshWorker
import kotlinx.coroutines.flow.first
import androidx.glance.color.ColorProvider as DayNightColor

class HaloWidget : GlanceAppWidget() {

    // Exact: layouts get the real size, so rings and bars scale with however big the widget is.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = HaloStore.get(context)
        val firstSnapshot = store.snapshot.first()
        val firstSettings = store.settings.first()
        provideContent {
            val snapshot by store.snapshot.collectAsState(firstSnapshot)
            val settings by store.settings.collectAsState(firstSettings)
            WidgetRoot(snapshot, settings)
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            runCatching { HaloWidget().updateAll(context) }
        }
    }
}

class HaloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HaloWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueue(context, scan = true, tag = "widget-added")
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.enqueue(context, scan = true, tag = "widget-tap")
    }
}

// --- Look ---------------------------------------------------------------------------------------

private class WidgetLook(
    val text: ColorProvider,
    val subtle: ColorProvider,
    val background: ImageProvider,
    val chip: ImageProvider,
    val accent: AccentStyle,
)

private val Ink = Color(0xFF1A1A1A)
private val Charge = Color(BatteryPalette.GREEN_B)

private fun look(settings: Settings): WidgetLook {
    val glass = settings.surface == SurfaceStyle.GLASS
    return when (settings.theme) {
        ThemeMode.SYSTEM -> WidgetLook(
            text = DayNightColor(day = Ink, night = Color.White),
            subtle = DayNightColor(day = Ink.copy(alpha = 0.5f), night = Color.White.copy(alpha = 0.55f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass else R.drawable.widget_bg_solid),
            chip = ImageProvider(R.drawable.widget_tile),
            accent = settings.accent,
        )
        ThemeMode.LIGHT -> WidgetLook(
            text = ColorProvider(Ink),
            subtle = ColorProvider(Ink.copy(alpha = 0.5f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass_light else R.drawable.widget_bg_solid_light),
            chip = ImageProvider(R.drawable.widget_tile_light),
            accent = settings.accent,
        )
        ThemeMode.DARK -> WidgetLook(
            text = ColorProvider(Color.White),
            subtle = ColorProvider(Color.White.copy(alpha = 0.55f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass_dark else R.drawable.widget_bg_solid_dark),
            chip = ImageProvider(R.drawable.widget_tile_dark),
            accent = settings.accent,
        )
    }
}

// --- Layouts ------------------------------------------------------------------------------------

@Composable
internal fun WidgetRoot(snapshot: Snapshot, settings: Settings) {
    val context = LocalContext.current
    val size = LocalSize.current
    val look = look(settings)
    val rows = WidgetModel.rows(context, snapshot, settings)
    val gauges = WidgetModel.gauges(rows)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(look.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = if (size.height < 90.dp) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val message = when {
            snapshot.permissionMissing -> "Open Halo to allow Bluetooth"
            !snapshot.bluetoothOn && gauges.isEmpty() -> "Bluetooth is off"
            gauges.isEmpty() -> "No devices connected"
            else -> null
        }
        when {
            message != null -> Message(message, look)
            size.height < 90.dp -> StripLayout(gauges, look)
            size.width < 200.dp -> SquareLayout(gauges, look)
            size.height < 190.dp -> RingsLayout(gauges, look)
            else -> ListLayout(rows, look)
        }
    }
}

@Composable
private fun Message(text: String, look: WidgetLook) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(40.dp).background(look.chip), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_bluetooth), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(look.text))
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(text, style = TextStyle(color = look.subtle, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** "78" with a smaller "%" sitting on the same baseline, and a bolt in front while charging. */
@Composable
private fun Percent(g: Gauge, size: Float, look: WidgetLook, weight: FontWeight = FontWeight.Medium, showBolt: Boolean = true) {
    val color = if (g.stale) look.subtle else look.text
    Row(verticalAlignment = Alignment.Bottom) {
        if (g.charging && showBolt) {
            Image(
                ImageProvider(R.drawable.ic_bolt), "Charging",
                GlanceModifier.size((size * 0.62f).dp).padding(bottom = (size * 0.16f).dp),
                colorFilter = ColorFilter.tint(ColorProvider(Charge)),
            )
        }
        Text(g.level?.toString() ?: "—", style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight))
        if (g.level != null) {
            Text(
                "%",
                modifier = GlanceModifier.padding(bottom = (size * 0.09f).dp),
                style = TextStyle(color = color, fontSize = (size * 0.6f).sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** Ring with the device icon in the middle. */
@Composable
private fun RingIcon(g: Gauge, ringDp: Dp, look: WidgetLook, iconRatio: Float = 0.4f) {
    val context = LocalContext.current
    val bmp = GaugeRenderer.ring(context, ringDp.value, g.level, g.charging, g.stale, look.accent)
    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(ringDp))
        Image(
            ImageProvider(g.icon), g.label,
            GlanceModifier.size(ringDp * iconRatio),
            colorFilter = ColorFilter.tint(if (g.stale) look.subtle else look.text),
        )
    }
}

/** 2×1 / 4×1: ring + number pairs. */
@Composable
private fun StripLayout(gauges: List<Gauge>, look: WidgetLook) {
    val size = LocalSize.current
    val count = ((size.width.value - 8) / 80).toInt().coerceIn(1, 6)
    val ring = (size.height.value - 18).coerceIn(28f, 36f).dp
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        gauges.take(count).forEach { g ->
            Row(
                GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RingIcon(g, ring, look, iconRatio = 0.44f)
                Spacer(GlanceModifier.width(6.dp))
                // The ring's glow already says "charging" here; the bolt would crowd the number.
                Percent(g, 15f, look, showBolt = false)
            }
        }
    }
}

/** 2×2: one or two big rows, or a 2×2 grid of rings. */
@Composable
private fun SquareLayout(gauges: List<Gauge>, look: WidgetLook) {
    val size = LocalSize.current
    val shown = gauges.take(4)
    if (shown.size <= 2) {
        val ring = ((size.height.value - 28) / 2 - 10).coerceIn(34f, 56f).dp
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { g ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    RingIcon(g, ring, look)
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Percent(g, 22f, look)
                        Text(g.label, style = TextStyle(color = look.subtle, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
        return
    }
    val cellW = (size.width.value - 28) / 2
    val cellH = (size.height.value - 28) / 2
    val roomy = cellH >= 66
    val ring = (if (roomy) minOf(cellW - 8, cellH - 22) else minOf(cellW, cellH) - 4).coerceIn(34f, 76f).dp
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        shown.chunked(2).forEach { pair ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { g ->
                    Column(
                        GlanceModifier.defaultWeight().fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (roomy) {
                            RingIcon(g, ring, look)
                            Spacer(GlanceModifier.height(4.dp))
                            Percent(g, 13f, look, FontWeight.Bold)
                        } else {
                            RingWithNumber(g, ring, look)
                        }
                    }
                }
                if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

/** Tiny 2×2 cells: icon over the number, both inside the ring. */
@Composable
private fun RingWithNumber(g: Gauge, ringDp: Dp, look: WidgetLook) {
    val context = LocalContext.current
    val bmp = GaugeRenderer.ring(context, ringDp.value, g.level, g.charging, g.stale, look.accent, strokeRatio = 0.09f)
    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), null, GlanceModifier.size(ringDp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(ImageProvider(g.icon), g.label, GlanceModifier.size(ringDp * 0.26f), colorFilter = ColorFilter.tint(look.text))
            Text(
                g.level?.toString() ?: "—",
                style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = (ringDp.value * 0.24f).sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** 4×2: a row of rings with the number and name underneath. */
@Composable
private fun RingsLayout(gauges: List<Gauge>, look: WidgetLook) {
    val size = LocalSize.current
    val maxCount = (size.width.value / 70).toInt().coerceIn(2, 6)
    val shown = gauges.take(maxCount)
    val colW = (size.width.value - 28) / shown.size.coerceAtLeast(1)
    val ring = minOf(colW - 16f, size.height.value - 28 - 44).coerceIn(36f, 76f).dp

    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { g ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                RingIcon(g, ring, look)
                Spacer(GlanceModifier.height(8.dp))
                Percent(g, 16f, look)
                Text(g.label, style = TextStyle(color = look.subtle, fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

/** 4×3 and up: one row per device, no inner cards. */
@Composable
private fun ListLayout(rows: List<DeviceRow>, look: WidgetLook) {
    val size = LocalSize.current
    val inner = size.width.value - 28
    val fits = ((size.height.value - 28 - 34) / 62f).toInt().coerceAtLeast(1)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Bluetooth battery", modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = look.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Box(
                GlanceModifier.size(28.dp).background(look.chip).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_refresh), "Refresh", GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(look.text))
            }
        }
        rows.take(fits).forEach { row ->
            DeviceLine(row, inner, look)
            Spacer(GlanceModifier.height(14.dp))
        }
    }
}

@Composable
private fun DeviceLine(row: DeviceRow, innerWidth: Float, look: WidgetLook) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(40.dp).background(look.chip), contentAlignment = Alignment.Center) {
            Image(ImageProvider(row.icon), null, GlanceModifier.size(21.dp), colorFilter = ColorFilter.tint(if (row.connected) look.text else look.subtle))
        }
        Spacer(GlanceModifier.width(12.dp))
        if (row.gauges.size > 1) {
            Column(GlanceModifier.defaultWeight()) {
                Text(row.title, style = TextStyle(color = look.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(row.status, style = TextStyle(color = look.subtle, fontSize = 11.sp), maxLines = 1)
            }
            row.gauges.forEach { g ->
                Column(GlanceModifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RingIcon(g, 30.dp, look, iconRatio = 0.5f)
                    Percent(g, 11f, look, FontWeight.Bold)
                }
            }
        } else {
            val g = row.gauges.first()
            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(row.title, style = TextStyle(color = look.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(row.status, style = TextStyle(color = look.subtle, fontSize = 11.sp), maxLines = 1)
                    }
                    Percent(g, 18f, look)
                }
                Spacer(GlanceModifier.height(5.dp))
                val barW = (innerWidth - 40 - 12).coerceAtLeast(40f)
                Image(
                    ImageProvider(GaugeRenderer.capsule(context, barW, 7f, g.level, g.charging, g.stale, look.accent)),
                    null,
                    GlanceModifier.width(barW.dp).height(7.dp),
                )
            }
        }
    }
}
