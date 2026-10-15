package com.halo.btbattery.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.SurfaceStyle
import com.halo.btbattery.model.ThemeMode
import com.halo.btbattery.ui.components.GlassCard
import com.halo.btbattery.ui.components.Segmented
import com.halo.btbattery.ui.components.SmoothShape
import com.halo.btbattery.ui.components.bouncy
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.LocalTones
import com.halo.btbattery.widget.BatteryPalette
import com.halo.btbattery.widget.HaloWidgetReceiver

@Composable
fun StyleScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val t = LocalTones.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("HALO", style = MaterialTheme.typography.labelMedium, color = Fluid.Mint, letterSpacing = 3.sp)
                Text("Style", style = MaterialTheme.typography.displaySmall, color = t.ink)
                Text("Applies to the app and every widget", style = MaterialTheme.typography.labelMedium, color = t.inkSoft)
            }
        }
        item {
            GlassCard(strong = true) {
                Text("Add the widget", style = MaterialTheme.typography.titleMedium, color = t.ink)
                Text(
                    "Resize it freely: 2×1 pills, 2×2 rings, 4×2 ring row, 4×3+ device list.",
                    style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
                )
                Spacer(Modifier.height(14.dp))
                PillButton("Place on home screen", onClick = {
                    val mgr = AppWidgetManager.getInstance(context)
                    val ok = mgr.isRequestPinAppWidgetSupported &&
                        mgr.requestPinAppWidget(ComponentName(context, HaloWidgetReceiver::class.java), null, null)
                    if (!ok) Toast.makeText(context, "Long-press the home screen → Widgets → Halo", Toast.LENGTH_LONG).show()
                }, Modifier.fillMaxWidth())
            }
        }
        item {
            GlassCard {
                Label("Theme")
                Segmented(ThemeMode.entries, settings.theme, { it.label }, { m -> vm.update { it.copy(theme = m) } })
                Spacer(Modifier.height(18.dp))
                Label("Widget surface")
                Segmented(SurfaceStyle.entries, settings.surface, { it.label }, { s -> vm.update { it.copy(surface = s) } })
                Spacer(Modifier.height(18.dp))
                Label("Accent")
                Segmented(AccentStyle.entries, settings.accent, { it.label }, { a -> vm.update { it.copy(accent = a) } })
                Spacer(Modifier.height(14.dp))
                AccentPreview(settings.accent)
            }
        }
        item {
            GlassCard {
                ToggleRow("Show phone battery", "Your phone appears first on the widget", settings.showPhone) { on ->
                    vm.update { it.copy(showPhone = on) }
                }
                Divider()
                ToggleRow("Show recently connected", "Keep last-known levels for devices that disconnected", settings.showRecent) { on ->
                    vm.update { it.copy(showRecent = on) }
                }
                Divider()
                ToggleRow(
                    "Live updates",
                    "Follows levels within seconds instead of every 15 min. Shows a silent notification.",
                    settings.liveMode,
                ) { on -> vm.setLive(on) }
                if (settings.hidden.isNotEmpty()) {
                    Divider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${settings.hidden.size} hidden from widget", Modifier.weight(1f), color = t.ink)
                        Text(
                            "Show all", color = Fluid.Mint, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.bouncy { vm.update { s -> s.copy(hidden = emptySet()) } }.padding(8.dp),
                        )
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("Keep Halo running", style = MaterialTheme.typography.titleMedium, color = t.ink)
                Text(
                    "ColorOS may pause background apps. In App info → Battery usage, allow background activity " +
                        "so the widget keeps updating.",
                    style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .bouncy {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        .clip(SmoothShape(20.dp)).background(t.glassStrong).padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Open App info", color = t.ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            GlassCard {
                Text("How it reads your batteries", style = MaterialTheme.typography.titleMedium, color = t.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Headphones, speakers and most buds: the level Android shows in the status bar.\n" +
                        "• Left, right and case: from the low-energy signal AirPods, Beats and Fast Pair earbuds " +
                        "(Pixel, OnePlus, OPPO Enco, Sony, JBL, Nothing…) broadcast. The case level updates when the lid opens.\n" +
                        "• Watches and bands: the standard Bluetooth battery service, when the watch offers it.\n" +
                        "• Car systems are never shown.",
                    style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalTones.current.inkSoft,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(LocalTones.current.edgeBottom))
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTones.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Fluid.Mint, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent),
        )
    }
}

@Composable
private fun AccentPreview(accent: AccentStyle) {
    val context = LocalContext.current
    val (a, b) = BatteryPalette.healthy(context, accent)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Color(a) to Color(b),
            Color(BatteryPalette.CHARGE_A) to Color(BatteryPalette.CHARGE_B),
            Color(BatteryPalette.AMBER_A) to Color(BatteryPalette.AMBER_B),
            Color(BatteryPalette.CORAL_A) to Color(BatteryPalette.CORAL_B),
        ).forEach { (x, y) ->
            Box(Modifier.size(width = 54.dp, height = 22.dp).clip(SmoothShape(11.dp)).background(Brush.horizontalGradient(listOf(x, y))))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("healthy · charging · ≤20% · ≤10%", style = MaterialTheme.typography.labelMedium, color = LocalTones.current.inkSoft)
}
