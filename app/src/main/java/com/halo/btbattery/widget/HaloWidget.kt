package com.halo.btbattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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

    override val sizeMode = SizeMode.Responsive(setOf(TINY, STRIP, SQUARE, WIDE, TALL))

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
        val TINY = DpSize(100.dp, 48.dp)
        val STRIP = DpSize(220.dp, 48.dp)
        val SQUARE = DpSize(100.dp, 100.dp)
        val WIDE = DpSize(220.dp, 100.dp)
        val TALL = DpSize(220.dp, 200.dp)

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
    val tile: ImageProvider,
    val accent: AccentStyle,
)

private fun look(settings: Settings): WidgetLook {
    val ink = Color(0xFF111318)
    val glass = settings.surface == SurfaceStyle.GLASS
    return when (settings.theme) {
        ThemeMode.SYSTEM -> WidgetLook(
            text = DayNightColor(day = ink, night = Color.White),
            subtle = DayNightColor(day = ink.copy(alpha = 0.55f), night = Color.White.copy(alpha = 0.6f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass else R.drawable.widget_bg_solid),
            tile = ImageProvider(R.drawable.widget_tile),
            accent = settings.accent,
        )
        ThemeMode.LIGHT -> WidgetLook(
            text = ColorProvider(ink),
            subtle = ColorProvider(ink.copy(alpha = 0.55f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass_light else R.drawable.widget_bg_solid_light),
            tile = ImageProvider(R.drawable.widget_tile_light),
            accent = settings.accent,
        )
        ThemeMode.DARK -> WidgetLook(
            text = ColorProvider(Color.White),
            subtle = ColorProvider(Color.White.copy(alpha = 0.6f)),
            background = ImageProvider(if (glass) R.drawable.widget_bg_glass_dark else R.drawable.widget_bg_solid_dark),
            tile = ImageProvider(R.drawable.widget_tile_dark),
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
            .cornerRadius(28.dp)
            .background(look.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = if (size.height < 90.dp) 6.dp else 12.dp),
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
            size.height < 190.dp -> RingsLayout(gauges, snapshot, look)
            else -> ListLayout(rows, snapshot, look)
        }
    }
}

@Composable
private fun Message(text: String, look: WidgetLook) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_bluetooth), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(look.subtle))
        Spacer(GlanceModifier.height(6.dp))
        Text(text, style = TextStyle(color = look.subtle, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** Ring with the device icon floating in the middle. */
@Composable
private fun RingIcon(g: Gauge, ringDp: Dp, look: WidgetLook, iconRatio: Float = 0.42f) {
    val context = LocalContext.current
    val bmp = GaugeRenderer.ring(context, ringDp.value, g.level, g.charging, g.stale, look.accent)
    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(ringDp))
        Image(
            ImageProvider(if (g.charging) R.drawable.ic_bolt else g.icon),
            contentDescription = g.label,
            modifier = GlanceModifier.size(ringDp * iconRatio),
            colorFilter = ColorFilter.tint(if (g.charging) ColorProvider(Color(BatteryPalette.CHARGE_A)) else look.text),
        )
    }
}

private fun pct(level: Int?) = level?.let { "$it%" } ?: "—"

/** 2×1 / 4×1: compact pills. */
@Composable
private fun StripLayout(gauges: List<Gauge>, look: WidgetLook) {
    val size = LocalSize.current
    val count = ((size.width.value - 8) / 74).toInt().coerceIn(1, 6)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        gauges.take(count).forEach { g ->
            Row(
                GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RingIcon(g, 32.dp, look, iconRatio = 0.46f)
                Spacer(GlanceModifier.width(6.dp))
                Text(pct(g.level), style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** 2×2: up to four rings, or two big rows when there is room. */
@Composable
private fun SquareLayout(gauges: List<Gauge>, look: WidgetLook) {
    val size = LocalSize.current
    val shown = gauges.take(4)
    if (shown.size <= 2) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { g ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    RingIcon(g, 40.dp, look)
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Text(pct(g.level), style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        Text(g.label, style = TextStyle(color = look.subtle, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
        return
    }
    val cell = minOf(size.width.value, size.height.value) / 2f - 6f
    val ring = cell.coerceIn(36f, 64f).dp
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        shown.chunked(2).forEach { pair ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { g ->
                    Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) {
                        RingWithNumber(g, ring, look)
                    }
                }
                if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

/** Small icon above the percentage, both inside the ring. */
@Composable
private fun RingWithNumber(g: Gauge, ringDp: Dp, look: WidgetLook) {
    val context = LocalContext.current
    val bmp = GaugeRenderer.ring(context, ringDp.value, g.level, g.charging, g.stale, look.accent, strokeRatio = 0.085f)
    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bmp), null, GlanceModifier.size(ringDp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                ImageProvider(if (g.charging) R.drawable.ic_bolt else g.icon), g.label,
                GlanceModifier.size(ringDp * 0.26f),
                colorFilter = ColorFilter.tint(if (g.charging) ColorProvider(Color(BatteryPalette.CHARGE_A)) else look.text),
            )
            Text(
                g.level?.toString() ?: "—",
                style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = (ringDp.value * 0.24f).sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** 4×2: a row of rings with numbers and labels underneath. */
@Composable
private fun RingsLayout(gauges: List<Gauge>, snapshot: Snapshot, look: WidgetLook) {
    val size = LocalSize.current
    val showHeader = size.height >= 140.dp
    val maxCount = ((size.width.value) / 64).toInt().coerceIn(2, 6)
    val shown = gauges.take(maxCount)
    val colW = size.width.value / shown.size.coerceAtLeast(1)
    val availH = size.height.value - (if (showHeader) 30 else 0) - 40
    val ring = minOf(colW - 10f, availH).coerceIn(34f, 64f).dp

    Column(GlanceModifier.fillMaxSize()) {
        if (showHeader) Header(snapshot, look)
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { g ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RingIcon(g, ring, look)
                    Spacer(GlanceModifier.height(6.dp))
                    Text(pct(g.level), style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(g.label, style = TextStyle(color = look.subtle, fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Header(snapshot: Snapshot, look: WidgetLook) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text("Battery", style = TextStyle(color = look.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            if (snapshot.updatedAt > 0) {
                Text("Updated ${WidgetModel.ago(snapshot.updatedAt)}", style = TextStyle(color = look.subtle, fontSize = 10.sp))
            }
        }
        Box(
            GlanceModifier.size(30.dp).background(look.tile).cornerRadius(15.dp).clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_refresh), "Refresh", GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(look.text))
        }
    }
}

/** 4×3 and up: a stack of glass tiles, one per device, as many as fit. */
@Composable
private fun ListLayout(rows: List<DeviceRow>, snapshot: Snapshot, look: WidgetLook) {
    val size = LocalSize.current
    val tileInner = size.width.value - 24 - 24
    val fits = ((size.height.value - 24 - 50) / 66f).toInt().coerceAtLeast(1)
    Column(GlanceModifier.fillMaxSize()) {
        Header(snapshot, look)
        rows.take(fits).forEach { row ->
            Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                DeviceTile(row, tileInner, look)
            }
        }
    }
}

@Composable
private fun DeviceTile(row: DeviceRow, innerWidth: Float, look: WidgetLook) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().background(look.tile).cornerRadius(20.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.gauges.size > 1) {
            Column(GlanceModifier.defaultWeight()) {
                TileTitle(row, look)
            }
            row.gauges.forEach { g ->
                Column(GlanceModifier.padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RingIcon(g, 30.dp, look, iconRatio = 0.46f)
                    Text(pct(g.level), style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
        } else {
            val g = row.gauges.first()
            RingIcon(g, 34.dp, look, iconRatio = 0.46f)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                TileTitle(row, look)
                val barW = (innerWidth - 34 - 10 - 52).coerceAtLeast(40f)
                Image(
                    ImageProvider(GaugeRenderer.capsule(context, barW, 10f, g.level, g.charging, g.stale, look.accent)),
                    null,
                    GlanceModifier.width(barW.dp).height(13.dp).padding(top = 3.dp),
                )
            }
            Text(
                pct(g.level),
                modifier = GlanceModifier.width(52.dp),
                style = TextStyle(color = if (g.stale) look.subtle else look.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
private fun TileTitle(row: DeviceRow, look: WidgetLook) {
    Text(row.title, style = TextStyle(color = look.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    Text(row.status, style = TextStyle(color = look.subtle, fontSize = 10.sp), maxLines = 1)
}
