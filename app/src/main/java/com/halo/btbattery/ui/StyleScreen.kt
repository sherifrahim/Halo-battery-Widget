package com.halo.btbattery.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.btbattery.R
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.model.SurfaceStyle
import com.halo.btbattery.model.ThemeMode
import com.halo.btbattery.ui.components.FluidSwitch
import com.halo.btbattery.ui.components.GlassCard
import com.halo.btbattery.ui.components.IconWell
import com.halo.btbattery.ui.components.PillButton
import com.halo.btbattery.ui.components.Ring
import com.halo.btbattery.ui.components.RowDivider
import com.halo.btbattery.ui.components.Segmented
import com.halo.btbattery.ui.components.bouncy
import com.halo.btbattery.ui.theme.LocalTones
import com.halo.btbattery.widget.HaloWidgetReceiver
import com.halo.btbattery.widget.WidgetModel

@Composable
fun StyleScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val t = LocalTones.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp)) {
                Text("Style", style = MaterialTheme.typography.displaySmall, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text("Applies to the app and every widget", fontSize = 13.sp, color = t.inkSoft)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                WidgetPreview(snapshot, settings)
                Spacer(Modifier.height(18.dp))
                PillButton("Add to home screen", onClick = {
                    val mgr = AppWidgetManager.getInstance(context)
                    val ok = mgr.isRequestPinAppWidgetSupported &&
                        mgr.requestPinAppWidget(ComponentName(context, HaloWidgetReceiver::class.java), null, null)
                    if (!ok) Toast.makeText(context, "Long-press the home screen → Widgets → Halo", Toast.LENGTH_LONG).show()
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    "Two widgets: Device overview and Selected device. Both resize freely.",
                    fontSize = 12.sp, color = t.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Header("Appearance") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Theme")
                Segmented(ThemeMode.entries, settings.theme, { it.label }, { m -> vm.update { it.copy(theme = m) } })
                Spacer(Modifier.height(18.dp))
                Label("Widget surface")
                Segmented(SurfaceStyle.entries, settings.surface, { it.label }, { s -> vm.update { it.copy(surface = s) } })
                Spacer(Modifier.height(18.dp))
                Label("Battery colour")
                Segmented(AccentStyle.entries, settings.accent, { it.label }, { a -> vm.update { it.copy(accent = a) } })
            }
        }
        item { Header("Widget") }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                ToggleRow(R.drawable.ic_phone, "Show phone battery", "Your phone appears first", settings.showPhone) { on ->
                    vm.update { it.copy(showPhone = on) }
                }
                RowDivider()
                ToggleRow(R.drawable.ic_bluetooth, "Recently connected", "Keep last-known levels after disconnecting", settings.showRecent) { on ->
                    vm.update { it.copy(showRecent = on) }
                }
                RowDivider()
                ToggleRow(R.drawable.ic_bolt, "Live updates", "Follow changes within seconds. Shows a silent notification.", settings.liveMode) { on ->
                    vm.setLive(on)
                }
                if (settings.hidden.isNotEmpty()) {
                    RowDivider()
                    LinkRow(R.drawable.ic_tune, "Hidden accessories", "${settings.hidden.size} hidden · tap to show all") {
                        vm.update { s -> s.copy(hidden = emptySet()) }
                    }
                }
            }
        }
        item { Header("Background") }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                LinkRow(R.drawable.ic_refresh, "Allow background activity", "App info → Battery usage, so the widget keeps updating") {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        item { Header("How it works") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Headphones, speakers and most buds report one level through Android.\n\n" +
                        "Left, right and case come from the low-energy signal AirPods, Beats and Fast Pair earbuds " +
                        "(Pixel, OnePlus, OPPO Enco, Sony, JBL, Nothing…) broadcast. The case updates while its lid is open.\n\n" +
                        "Watches and bands are read through the standard Bluetooth battery service. Car systems are never shown.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = t.inkSoft,
                )
            }
        }
    }
}

/** Live replica of the 4×2 "Device overview" widget, in the widget's own colours. */
@Composable
private fun WidgetPreview(snapshot: Snapshot, settings: Settings) {
    val context = LocalContext.current
    val t = LocalTones.current
    val dark = when (settings.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> t.dark }
    val container = if (dark) Color(0xFF373737) else Color(0xFFF1F1F3)
    val tile = if (dark) Color(0xFF474747) else Color.White
    val title = if (dark) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
    val sub = if (dark) Color(0xFFAAAAAA) else Color(0xFF8A8A8E)
    val rows = WidgetModel.rows(context, snapshot, settings).take(4)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(container).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) {
            Text("Your accessories will appear here", fontSize = 13.sp, color = sub, modifier = Modifier.padding(24.dp))
        }
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { row ->
                    val g = if (row.gauges.size > 1) row.gauges.take(2).filter { it.level != null }.minByOrNull { it.level!! } ?: row.gauges.first() else row.gauges.first()
                    Row(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(tile).padding(start = 9.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ring(g.level, levelColors(g.level, g.charging, settings.accent), size = 44.dp, stroke = 3.dp, dimmed = g.stale) {
                            Icon(painterResource(row.icon), null, Modifier.size(21.dp), tint = title)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, fontSize = 13.sp, color = title, maxLines = 2, lineHeight = 16.sp)
                            Text(
                                when {
                                    row.gauges.size > 1 -> listOfNotNull(
                                        row.gauges[0].level?.let { "Ⓛ$it" }, row.gauges[1].level?.let { "Ⓡ$it" },
                                    ).joinToString("  ")
                                    g.level == null -> "Connected"
                                    else -> "Battery: ${g.level}%"
                                },
                                fontSize = 11.sp, color = sub, maxLines = 1,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalTones.current.inkSoft,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, color = LocalTones.current.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun ToggleRow(@DrawableRes icon: Int, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTones.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconWell(icon, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
            Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = t.inkSoft)
        }
        FluidSwitch(checked, onChange)
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, title: String, summary: String, onClick: () -> Unit) {
    val t = LocalTones.current
    Row(
        Modifier.fillMaxWidth().bouncy(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(icon, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
            Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = t.inkSoft)
        }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(22.dp), tint = t.inkFaint)
    }
}
