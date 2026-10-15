package com.halo.btbattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.halo.btbattery.R
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.ui.MainActivity
import com.halo.btbattery.work.RefreshWorker
import kotlinx.coroutines.flow.first

/** "Selected device": one accessory up close, laid out like the ColorOS 17 widget of that name. */
class SelectedWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = HaloStore.get(context)
        val firstSnapshot = store.snapshot.first()
        val firstSettings = store.settings.first()
        provideContent {
            val snapshot by store.snapshot.collectAsState(firstSnapshot)
            val settings by store.settings.collectAsState(firstSettings)
            SelectedRoot(snapshot, settings)
        }
    }
}

class SelectedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelectedWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.enqueue(context, scan = true, tag = "widget-added")
    }
}

/** Earbuds you're wearing beat everything; then any connected accessory; then the latest seen. */
internal fun pickSelected(snapshot: Snapshot, settings: Settings): DeviceBattery? {
    val visible = snapshot.devices.filter { it.address !in settings.hidden && it.type != DeviceType.PHONE }
    return visible.firstOrNull { it.connected && it.hasBuds }
        ?: visible.firstOrNull { it.connected }
        ?: visible.maxByOrNull { it.lastSeen }
}

@Composable
internal fun SelectedRoot(snapshot: Snapshot, settings: Settings) {
    val context = LocalContext.current
    val size = LocalSize.current
    val look = widgetLook(settings)
    val device = pickSelected(snapshot, settings).takeIf { !snapshot.permissionMissing }
    val rows = WidgetModel.rows(context, snapshot, settings.copy(showPhone = false))
    val row = rows.firstOrNull { it.key == device?.address }

    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(look.container)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (device == null || row == null) {
            WidgetMessage(if (snapshot.permissionMissing) "Open Halo to allow Bluetooth" else "No devices connected", look)
            return@Box
        }
        val wide = size.width.value >= 200
        Column(GlanceModifier.fillMaxSize()) {
            // Product area, darker, with the connection dot in the corner.
            Box(GlanceModifier.fillMaxWidth().defaultWeight().background(look.top)) {
                val bandH = if (wide) 76f else 62f
                val topH = size.height.value - bandH
                if (wide) {
                    Row(GlanceModifier.fillMaxSize().padding(start = 18.dp, end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = (topH - 16).coerceIn(48f, 92f)
                        Image(ImageProvider(ProductArt.render(context, device.type, art)), device.name, GlanceModifier.size(art.dp))
                        Spacer(GlanceModifier.width(14.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(device.name, style = TextStyle(color = look.title, fontSize = 19.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Spacer(GlanceModifier.height(4.dp))
                            DetailLine(row, look, 14f)
                        }
                    }
                } else {
                    val art = minOf(size.width.value * 0.62f, topH - 14).coerceIn(40f, 120f)
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(ProductArt.render(context, device.type, art)), device.name, GlanceModifier.size(art.dp))
                    }
                }
                if (device.connected) {
                    Box(GlanceModifier.fillMaxSize().padding(top = 12.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
                        Image(ImageProvider(R.drawable.widget_dot), "Connected", GlanceModifier.size(9.dp))
                    }
                }
            }
            if (wide) WideBand(row, rows, look) else NarrowBand(device, row, look)
        }
    }
}

@Composable
private fun DetailLine(row: DeviceRow, look: WidgetLook, fontSp: Float) {
    if (row.gauges.size > 1 && row.connected) BudsLine(row, look, fontSp, withCase = true)
    else StatusLine(row, look, fontSp)
}

/** 2×2 band: name and levels, centred. */
@Composable
private fun NarrowBand(device: DeviceBattery, row: DeviceRow, look: WidgetLook) {
    Column(
        GlanceModifier.fillMaxWidth().height(62.dp).padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(device.name, style = TextStyle(color = look.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 1)
        Spacer(GlanceModifier.height(3.dp))
        DetailLine(row, look, 12.5f)
    }
}

/** 4×2 band: Left / Right / Case rings for earbuds, otherwise your other connected accessories. */
@Composable
private fun WideBand(row: DeviceRow, all: List<DeviceRow>, look: WidgetLook) {
    val items = if (row.gauges.size > 1) row.gauges else all.filter { it.key != row.key && it.connected }.map { it.headline() }.take(4)
    Row(
        GlanceModifier.fillMaxWidth().height(76.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (items.isEmpty()) {
            Text(
                if (row.connected) "Connected" else row.status,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = look.subtitle, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
            return@Row
        }
        items.forEach { g ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                RingIcon(g, 40.dp, look)
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    g.level?.let { "${g.label} · $it%" } ?: g.label,
                    style = TextStyle(color = look.subtitle, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1,
                )
            }
        }
    }
}
