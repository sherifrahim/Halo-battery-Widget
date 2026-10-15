package com.halo.btbattery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.btbattery.R
import com.halo.btbattery.bt.Permissions
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.ui.components.BigPercent
import com.halo.btbattery.ui.components.Capsule
import com.halo.btbattery.ui.components.CircleButton
import com.halo.btbattery.ui.components.FluidSwitch
import com.halo.btbattery.ui.components.GlassCard
import com.halo.btbattery.ui.components.IconWell
import com.halo.btbattery.ui.components.PillButton
import com.halo.btbattery.ui.components.Ring
import com.halo.btbattery.ui.components.RowDivider
import com.halo.btbattery.ui.components.SmoothShape
import com.halo.btbattery.ui.components.bouncy
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.LocalAccent
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
    val featured = connected.filter { it.hasBuds }
    val others = connected.filterNot { it.hasBuds }
    val recent = snapshot.devices.filter { !it.connected }
    val accent = settings.accent

    // Depth of field: the page softly blurs while the device sheet is up (Android 12+).
    val depth by animateDpAsState(if (sheetFor != null) 14.dp else 0.dp, tween(320), label = "depth")
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().blur(depth)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LargeTitle(refreshing, snapshot.updatedAt, vm::refresh) }

            if (!canConnect || !canScan) item {
                NoticeCard(
                    title = if (!canConnect) "Allow Bluetooth access" else "Allow nearby scanning",
                    body = if (!canConnect) "Halo needs to see your connected accessories to read their battery."
                    else "Lets Halo read each earbud and the case from AirPods and Fast Pair earbuds.",
                    action = "Allow",
                    onAction = { permissionLauncher.launch(Permissions.required) },
                )
            } else if (!snapshot.bluetoothOn) item {
                NoticeCard("Bluetooth is off", "Turn on Bluetooth to see your accessories.", null, null)
            }

            item { PhoneCard(phone, connected.size, accent) }

            featured.forEach { d ->
                item(key = d.address) { EarbudsCard(d, accent, d.address in settings.hidden) { sheetFor = d.address } }
            }
            if (others.isNotEmpty()) {
                item { SectionHeader("Connected") }
                item { DeviceGroup(others, accent, settings.hidden, snapshot.watchesWithoutApp.isNotEmpty()) { sheetFor = it } }
            }
            if (connected.isEmpty() && canConnect && snapshot.bluetoothOn) item { EmptyCard() }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Recently connected") }
                item { DeviceGroup(recent, accent, settings.hidden, false) { sheetFor = it } }
            }
        }
    }

    val sheetDevice = snapshot.devices.firstOrNull { it.address == sheetFor }
    if (sheetDevice != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            containerColor = if (t.dark) Color(0xFF1C1C1E) else Color(0xFFF7F8FA),
            shape = SmoothShape(30.dp),
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
private fun LargeTitle(refreshing: Boolean, updatedAt: Long, onRefresh: () -> Unit) {
    val t = LocalTones.current
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Battery", style = MaterialTheme.typography.displaySmall, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Text(
                if (updatedAt > 0) "Updated ${WidgetModel.ago(updatedAt)}" else "Pull down to refresh",
                fontSize = 13.sp, color = t.inkSoft,
            )
        }
        CircleButton(R.drawable.ic_refresh, "Refresh", onRefresh, iconModifier = Modifier.rotate(if (refreshing) angle else 0f))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalTones.current.inkSoft,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp),
    )
}

@Composable
private fun PhoneCard(phone: PhoneBattery?, connectedCount: Int, accent: AccentStyle) {
    val t = LocalTones.current
    val charging = phone?.charging == true
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(phone?.level, levelColors(phone?.level, charging, accent), size = 96.dp, stroke = 6.dp, charging = charging) {
                Icon(painterResource(R.drawable.ic_phone), null, Modifier.size(30.dp), tint = t.ink)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text("This phone", fontSize = 14.sp, color = t.inkSoft)
                BigPercent(phone?.level, 46.sp, t.ink, charging = charging)
                Text(
                    when (connectedCount) {
                        0 -> "No accessories connected"
                        1 -> "1 accessory connected"
                        else -> "$connectedCount accessories connected"
                    },
                    fontSize = 13.sp, color = t.inkSoft,
                )
            }
        }
    }
}

/** Earbuds get their own card, laid out like the system pop-up that appears when the case opens. */
@Composable
private fun EarbudsCard(d: DeviceBattery, accent: AccentStyle, hidden: Boolean, onClick: () -> Unit) {
    val t = LocalTones.current
    val now = System.currentTimeMillis()
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconWell(d.type.icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLine(d, hidden), fontSize = 12.sp, color = t.inkSoft, maxLines = 1)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(22.dp), tint = t.inkFaint)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BudColumn("Left", R.drawable.ic_earbud_left, d.left, d.connected, accent, now)
            BudColumn("Right", R.drawable.ic_earbud_right, d.right, d.connected, accent, now)
            BudColumn("Case", R.drawable.ic_case, d.case, d.connected, accent, now)
        }
    }
}

@Composable
private fun BudColumn(label: String, @DrawableRes icon: Int, cell: Cell?, connected: Boolean, accent: AccentStyle, now: Long) {
    val t = LocalTones.current
    val stale = !connected || cell == null || cell.isStale(now)
    val charging = cell?.charging == true && connected
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(cell?.level, levelColors(cell?.level, charging, accent), size = 80.dp, stroke = 5.dp, dimmed = stale, charging = charging) {
            Icon(painterResource(icon), label, Modifier.size(30.dp), tint = if (stale) t.inkSoft else t.ink)
        }
        Spacer(Modifier.height(10.dp))
        BigPercent(cell?.level, 20.sp, if (stale) t.inkSoft else t.ink, charging = charging)
        Text(
            if (cell != null && cell.isStale(now)) "$label · ${WidgetModel.ago(cell.at, now)}" else label,
            fontSize = 12.sp, color = t.inkSoft,
        )
    }
}

/** Grouped list card in the ColorOS settings style: rows separated by inset hairlines. */
@Composable
private fun DeviceGroup(devices: List<DeviceBattery>, accent: AccentStyle, hidden: Set<String>, watchAppMissing: Boolean, onOpen: (String) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        devices.forEachIndexed { i, d ->
            DeviceRowItem(d, accent, d.address in hidden, watchAppMissing && d.type == DeviceType.WATCH && d.main == null) { onOpen(d.address) }
            if (i < devices.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun DeviceRowItem(d: DeviceBattery, accent: AccentStyle, hidden: Boolean, needsWatchApp: Boolean, onClick: () -> Unit) {
    val t = LocalTones.current
    val now = System.currentTimeMillis()
    val cell = d.headline
    val stale = !d.connected || cell == null || cell.isStale(now)
    val charging = cell?.charging == true && d.connected
    Row(
        Modifier.fillMaxWidth().bouncy(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (d.connected) 1f else 0.75f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(d.type.icon, size = 42.dp, tint = if (d.connected) t.ink else t.inkSoft)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(statusLine(d, hidden, needsWatchApp), fontSize = 12.sp, color = if (needsWatchApp) LocalAccent.current else t.inkSoft, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                BigPercent(cell?.level, 20.sp, if (stale) t.inkSoft else t.ink, charging = charging)
            }
            Spacer(Modifier.height(10.dp))
            Capsule(cell?.level, levelColors(cell?.level, charging, accent), Modifier.fillMaxWidth().height(8.dp), dimmed = stale)
        }
    }
}

private fun statusLine(d: DeviceBattery, hidden: Boolean, needsWatchApp: Boolean = false): String {
    val parts = mutableListOf<String>()
    parts += when {
        !d.connected -> "Last seen ${WidgetModel.ago(d.lastSeen)}"
        needsWatchApp -> "Install Halo on the watch to see its battery"
        else -> "Connected"
    }
    if (hidden) parts += "Hidden on widget"
    return parts.joinToString(" · ")
}

@Composable
private fun NoticeCard(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    val t = LocalTones.current
    val accent = LocalAccent.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconWell(R.drawable.ic_bluetooth, size = 44.dp, tint = accent, fill = accent.copy(alpha = 0.14f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = t.inkSoft)
            }
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PillButton(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EmptyCard() {
    val t = LocalTones.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconWell(R.drawable.ic_bluetooth, size = 56.dp, tint = t.inkSoft)
            Spacer(Modifier.height(12.dp))
            Text("No accessories connected", style = MaterialTheme.typography.titleMedium, color = t.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect earbuds, headphones, a speaker or a watch and it shows up here.",
                fontSize = 13.sp, color = t.inkSoft, modifier = Modifier.padding(horizontal = 16.dp),
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
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconWell(device.type.icon, size = 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(device.name, style = MaterialTheme.typography.headlineMedium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device.address, fontSize = 12.sp, color = t.inkSoft)
        }
        Spacer(Modifier.height(22.dp))
        GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show on widget", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = t.ink)
                FluidSwitch(!hidden, { onHidden(!it) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Icon", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.inkSoft, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 4) {
            DeviceType.entries.filter { it != DeviceType.PHONE }.forEach { type ->
                val active = type == device.type
                Column(
                    Modifier.weight(1f).bouncy { onType(type) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconWell(
                        type.icon, size = 52.dp,
                        tint = if (active) Color.White else t.ink,
                        fill = if (active) accent else t.well,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(type.label, fontSize = 12.sp, color = if (active) t.ink else t.inkSoft, maxLines = 1)
                }
            }
        }
        if (!device.connected) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).bouncy(onClick = onForget, pressedScale = 0.96f).clip(CircleShape)
                    .background(Fluid.Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Remove from list", color = Fluid.Red, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
