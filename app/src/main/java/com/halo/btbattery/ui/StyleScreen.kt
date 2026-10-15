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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import com.halo.btbattery.ui.components.BigPercent
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
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
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
                    "Resize freely: 2×1, 2×2, 4×2, or 4×3 and larger for the full list.",
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

/** A small live preview drawn like the 4×2 widget. */
@Composable
private fun WidgetPreview(snapshot: Snapshot, settings: Settings) {
    val context = LocalContext.current
    val t = LocalTones.current
    val gauges = WidgetModel.gauges(WidgetModel.rows(context, snapshot, settings)).take(4)
    if (gauges.isEmpty()) {
        Text("Your accessories will appear here", fontSize = 13.sp, color = t.inkSoft, modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        gauges.forEach { g ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(g.level, levelColors(g.level, g.charging, settings.accent), size = 62.dp, stroke = 7.dp, dimmed = g.stale, charging = g.charging) {
                    Icon(painterResource(g.icon), g.label, Modifier.size(24.dp), tint = if (g.stale) t.inkSoft else t.ink)
                }
                Spacer(Modifier.height(8.dp))
                BigPercent(g.level, 16.sp, if (g.stale) t.inkSoft else t.ink, charging = g.charging)
                Text(g.label, fontSize = 11.sp, color = t.inkSoft, maxLines = 1)
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
