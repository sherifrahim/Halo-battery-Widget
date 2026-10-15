package com.halo.btbattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.halo.btbattery.R
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.ui.MainActivity
import com.halo.btbattery.work.RefreshWorker
import kotlinx.coroutines.flow.first

/** "Device overview": a tile per device, laid out like the ColorOS 17 My Devices widget. */
class HaloWidget : GlanceAppWidget() {

    // Exact: layouts get the real size, so tiles and rings fit whatever the widget is resized to.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = HaloStore.get(context)
        val firstSnapshot = store.snapshot.first()
        val firstSettings = store.settings.first()
        provideContent {
            val snapshot by store.snapshot.collectAsState(firstSnapshot)
            val settings by store.settings.collectAsState(firstSettings)
            OverviewRoot(snapshot, settings)
        }
    }

    companion object {
        /** Redraws every Halo widget of both kinds. */
        suspend fun updateAll(context: Context) {
            runCatching { HaloWidget().updateAll(context) }
            runCatching { SelectedWidget().updateAll(context) }
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

private const val PAD = 8
private const val GAP = 8

@Composable
internal fun OverviewRoot(snapshot: Snapshot, settings: Settings) {
    val context = LocalContext.current
    val size = LocalSize.current
    val look = widgetLook(settings)
    val rows = WidgetModel.rows(context, snapshot, settings)

    WidgetSurface(look, padding = PAD.dp, contentAlignment = Alignment.Center) {
        val message = when {
            snapshot.permissionMissing -> "Open Halo to allow Bluetooth"
            !snapshot.bluetoothOn && rows.isEmpty() -> "Bluetooth is off"
            rows.isEmpty() -> "No devices connected"
            else -> null
        }
        when {
            message != null -> WidgetMessage(message, look)
            size.height < 100.dp -> RingStrip(rows, look)
            else -> TileGrid(rows, look)
        }
    }
}

/** 2×1 / 4×1: rings only, as in the smallest system widget. */
@Composable
private fun RingStrip(rows: List<DeviceRow>, look: WidgetLook) {
    val size = LocalSize.current
    val ring = (size.height.value - PAD * 2 - 10).coerceIn(32f, 60f)
    val count = ((size.width.value - PAD * 2) / (ring + 20)).toInt().coerceIn(1, 6)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        rows.take(count).forEach { row ->
            Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) {
                RingIcon(row.headline(), ring.dp, look)
            }
        }
    }
}

/** One or two columns of tiles; the last slot links into the app when there's room or overflow. */
@Composable
private fun TileGrid(rows: List<DeviceRow>, look: WidgetLook) {
    val size = LocalSize.current
    val cols = if (size.width.value >= 200) 2 else 1
    val innerH = size.height.value - PAD * 2
    val maxRows = ((innerH + GAP) / (58f + GAP)).toInt().coerceAtLeast(1)
    val slots = maxRows * cols

    val cells: List<DeviceRow?> = when {
        rows.size > slots && cols == 2 -> rows.take(slots - 1) + null
        rows.size > slots -> rows.take(slots)
        cols == 2 && rows.size % 2 == 1 && rows.size < slots -> rows + null
        else -> rows
    }
    val usedRows = (cells.size + cols - 1) / cols
    val tileH = (innerH - GAP * (usedRows - 1)) / usedRows
    val ring = (tileH - 18).coerceIn(30f, 48f)
    val overflow = (rows.size - (slots - 1)).coerceAtLeast(0)

    Column(GlanceModifier.fillMaxSize()) {
        cells.chunked(cols).forEachIndexed { r, line ->
            if (r > 0) Spacer(GlanceModifier.height(GAP.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                line.forEachIndexed { c, cell ->
                    if (c > 0) Spacer(GlanceModifier.width(GAP.dp))
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        if (cell != null) DeviceTile(cell, ring, look)
                        else AppTile(if (rows.size > slots) "+$overflow more" else "Tap to open", ring, look)
                    }
                }
                if (line.size < cols) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun DeviceTile(row: DeviceRow, ring: Float, look: WidgetLook) {
    Row(
        GlanceModifier.fillMaxSize().background(look.tile).cornerRadius(16.dp).padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingIcon(row.headline(), ring.dp, look)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, style = TextStyle(color = look.title, fontSize = 14.sp), maxLines = 2)
            Spacer(GlanceModifier.height(3.dp))
            StatusLine(row, look, 12f)
        }
    }
}

/** Mirrors the system widget's "My Devices · Tap to enter" tile. */
@Composable
private fun AppTile(subtitle: String, ring: Float, look: WidgetLook) {
    Row(
        GlanceModifier.fillMaxSize().background(look.tile).cornerRadius(16.dp).padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(ring.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_orbit), null, GlanceModifier.size((ring * 0.8f).dp), colorFilter = ColorFilter.tint(look.title))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text("Halo", style = TextStyle(color = look.title, fontSize = 14.sp), maxLines = 1)
            Spacer(GlanceModifier.height(3.dp))
            Text(subtitle, style = TextStyle(color = look.subtitle, fontSize = 12.sp), maxLines = 1)
        }
    }
}
