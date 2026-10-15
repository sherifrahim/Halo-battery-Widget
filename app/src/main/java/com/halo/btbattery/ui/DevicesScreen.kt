package com.halo.btbattery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.btbattery.R
import com.halo.btbattery.bt.Permissions
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.ui.components.Capsule
import com.halo.btbattery.ui.components.GlassCard
import com.halo.btbattery.ui.components.Ring
import com.halo.btbattery.ui.components.SmoothShape
import com.halo.btbattery.ui.components.bouncy
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.LocalTones
import com.halo.btbattery.widget.BatteryPalette
import com.halo.btbattery.widget.WidgetModel

@Composable
fun levelColors(level: Int?, charging: Boolean, accent: AccentStyle): Pair<Color, Color> {
    val context = LocalContext.current
    val (a, b) = BatteryPalette.gradient(context, level, charging, accent)
    return Color(a) to Color(b)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val t = LocalTones.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val phone = rememberPhoneBattery()
    var sheetFor by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val canConnect = remember(snapshot) { Permissions.canConnect(context) }
    val canScan = remember(snapshot) { Permissions.canScan(context) }

    val connected = snapshot.devices.filter { it.connected }
    val recent = snapshot.devices.filter { !it.connected }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(refreshing = refreshing, updatedAt = snapshot.updatedAt, onRefresh = vm::refresh)
            }
            if (!canConnect || !canScan) item {
                NoticeCard(
                    icon = R.drawable.ic_bluetooth,
                    title = if (!canConnect) "Allow Bluetooth access" else "Allow nearby scanning",
                    body = if (!canConnect) "Halo needs to see your connected devices to read their battery."
                    else "Lets Halo read each earbud and the case from AirPods and Fast Pair earbuds.",
                    action = "Allow",
                    onAction = { permissionLauncher.launch(Permissions.required) },
                )
            } else if (!snapshot.bluetoothOn) item {
                NoticeCard(R.drawable.ic_bluetooth, "Bluetooth is off", "Turn it on to see your accessories.", null, null)
            }
            item {
                PhoneHero(phone, connected.size, settings.accent)
            }
            if (connected.isNotEmpty()) {
                item { SectionLabel("Connected") }
                items(connected, key = { it.address }) { d ->
                    DeviceCard(d, settings.accent, hidden = d.address in settings.hidden, onClick = { sheetFor = d.address })
                }
            } else if (canConnect && snapshot.bluetoothOn) item {
                EmptyCard()
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel("Recently connected") }
                items(recent, key = { it.address }) { d ->
                    DeviceCard(d, settings.accent, hidden = d.address in settings.hidden, onClick = { sheetFor = d.address })
                }
            }
        }
    }

    val sheetDevice = snapshot.devices.firstOrNull { it.address == sheetFor }
    if (sheetDevice != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            containerColor = if (t.dark) Color(0xFF15181E) else Color(0xFFF7F9FC),
            shape = SmoothShape(32.dp),
        ) {
            DeviceSheet(
                device = sheetDevice,
                hidden = sheetDevice.address in settings.hidden,
                onHidden = { vm.setHidden(sheetDevice.address, it) },
                onType = { vm.setType(sheetDevice.address, it) },
                onForget = { vm.forget(sheetDevice.address); sheetFor = null },
            )
        }
    }
}

@Composable
private fun Header(refreshing: Boolean, updatedAt: Long, onRefresh: () -> Unit) {
    val t = LocalTones.current
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("HALO", style = MaterialTheme.typography.labelMedium, color = Fluid.Mint, letterSpacing = 3.sp)
            Text("Battery", style = MaterialTheme.typography.displaySmall, color = t.ink)
            Text(
                if (updatedAt > 0) "Updated ${WidgetModel.ago(updatedAt)}" else "Pull down to refresh",
                style = MaterialTheme.typography.labelMedium, color = t.inkSoft,
            )
        }
        Box(
            Modifier.size(46.dp).bouncy(onClick = onRefresh).clip(SmoothShape(18.dp)).background(t.glassStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_refresh), "Refresh",
                Modifier.size(22.dp).rotate(if (refreshing) angle else 0f), tint = t.ink,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalTones.current.inkSoft,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun PhoneHero(phone: PhoneBattery?, connectedCount: Int, accent: AccentStyle) {
    val t = LocalTones.current
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(phone?.level, levelColors(phone?.level, phone?.charging == true, accent), size = 92.dp, stroke = 9.dp) {
                Icon(
                    painterResource(if (phone?.charging == true) R.drawable.ic_bolt else R.drawable.ic_phone), null,
                    Modifier.size(28.dp), tint = if (phone?.charging == true) Fluid.Charge else t.ink,
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("This phone", style = MaterialTheme.typography.labelMedium, color = t.inkSoft)
                Text(
                    phone?.let { "${it.level}%" } ?: "—",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = t.ink, letterSpacing = (-1).sp,
                )
                Text(
                    when (connectedCount) {
                        0 -> "No accessories connected"
                        1 -> "1 accessory connected"
                        else -> "$connectedCount accessories connected"
                    },
                    style = MaterialTheme.typography.labelMedium, color = t.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(d: DeviceBattery, accent: AccentStyle, hidden: Boolean, onClick: () -> Unit) {
    val t = LocalTones.current
    val now = System.currentTimeMillis()
    GlassCard(
        modifier = Modifier.fillMaxWidth().animateContentSize().alpha(if (d.connected) 1f else 0.72f),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(d.type.icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLine(d, hidden, now), style = MaterialTheme.typography.labelMedium, color = t.inkSoft, maxLines = 1)
            }
            if (!d.hasBuds) {
                val main = d.main
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (main?.charging == true && d.connected) {
                        Icon(painterResource(R.drawable.ic_bolt), null, Modifier.size(16.dp), tint = Fluid.Charge)
                    }
                    Text(main?.let { "${it.level}%" } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = t.ink)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (d.hasBuds) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BudGauge("Left", R.drawable.ic_earbud_left, d.left, d.connected, accent, now)
                BudGauge("Right", R.drawable.ic_earbud_right, d.right, d.connected, accent, now)
                BudGauge("Case", R.drawable.ic_case, d.case, d.connected, accent, now)
            }
        } else {
            val main = d.main
            Capsule(
                main?.level, levelColors(main?.level, main?.charging == true && d.connected, accent),
                Modifier.fillMaxWidth().height(10.dp),
                dimmed = !d.connected || main == null || main.isStale(now),
            )
        }
    }
}

private fun statusLine(d: DeviceBattery, hidden: Boolean, now: Long): String {
    val parts = mutableListOf<String>()
    parts += if (d.connected) "Connected" else "Seen ${WidgetModel.ago(d.lastSeen, now)}"
    if (d.connected && d.sources.isNotEmpty()) parts += d.sources.joinToString(" + ") { it.label }
    if (hidden) parts += "Hidden on widget"
    return parts.joinToString(" · ")
}

@Composable
private fun BudGauge(label: String, @DrawableRes icon: Int, cell: Cell?, connected: Boolean, accent: AccentStyle, now: Long) {
    val t = LocalTones.current
    val stale = !connected || cell == null || cell.isStale(now)
    val charging = cell?.charging == true && connected
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(cell?.level, levelColors(cell?.level, charging, accent), size = 78.dp, stroke = 7.dp, dimmed = stale) {
            Icon(painterResource(if (charging) R.drawable.ic_bolt else icon), label, Modifier.size(26.dp), tint = if (charging) Fluid.Charge else t.ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(cell?.let { "${it.level}%" } ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (stale) t.inkSoft else t.ink)
        Text(
            if (cell != null && cell.isStale(now)) "$label · ${WidgetModel.ago(cell.at, now)}" else label,
            style = MaterialTheme.typography.labelMedium, color = t.inkSoft,
        )
    }
}

@Composable
private fun IconChip(@DrawableRes icon: Int, size: Dp = 46.dp) {
    val t = LocalTones.current
    Box(Modifier.size(size).clip(SmoothShape(16.dp)).background(t.glassStrong), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(size * 0.5f), tint = t.ink)
    }
}

@Composable
private fun NoticeCard(@DrawableRes icon: Int, title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    val t = LocalTones.current
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
            }
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            PillButton(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .bouncy(onClick = onClick)
            .clip(SmoothShape(22.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Fluid.Mint, Fluid.Aqua)))
            .padding(vertical = 13.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF04140E), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyCard() {
    val t = LocalTones.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_bluetooth), null, Modifier.size(34.dp), tint = t.inkSoft)
            Spacer(Modifier.height(10.dp))
            Text("Nothing connected yet", style = MaterialTheme.typography.titleMedium, color = t.ink)
            Text(
                "Connect earbuds, headphones, a speaker or a watch and it appears here.",
                style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun DeviceSheet(
    device: DeviceBattery,
    hidden: Boolean,
    onHidden: (Boolean) -> Unit,
    onType: (DeviceType) -> Unit,
    onForget: () -> Unit,
) {
    val t = LocalTones.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(device.type.icon, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(device.name, style = MaterialTheme.typography.headlineMedium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(device.address, style = MaterialTheme.typography.labelMedium, color = t.inkSoft)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().clip(SmoothShape(20.dp)).background(t.glass).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show on widget", Modifier.weight(1f), color = t.ink, style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = !hidden, onCheckedChange = { onHidden(!it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Fluid.Mint, checkedThumbColor = Color.White),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("ICON", style = MaterialTheme.typography.labelMedium, color = t.inkSoft, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceType.entries.filter { it != DeviceType.PHONE }.forEach { type ->
                val active = type == device.type
                Column(
                    Modifier
                        .width(72.dp)
                        .bouncy { onType(type) }
                        .clip(SmoothShape(18.dp))
                        .background(if (active) Fluid.Mint.copy(alpha = 0.22f) else t.glass)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(type.icon), null, Modifier.size(24.dp), tint = if (active) Fluid.Mint else t.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(type.label, fontSize = 11.sp, color = if (active) t.ink else t.inkSoft, maxLines = 1)
                }
            }
        }
        if (!device.connected) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().bouncy(onClick = onForget).clip(SmoothShape(20.dp))
                    .background(Fluid.Coral.copy(alpha = 0.14f)).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Remove from list", color = Fluid.Coral, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
