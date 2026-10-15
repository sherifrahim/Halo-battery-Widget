package com.halo.btbattery.widget

import android.content.Context
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import com.halo.btbattery.R
import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot

/** One battery as the widget draws it. */
data class Gauge(
    val key: String,
    val label: String,
    @DrawableRes val icon: Int,
    val level: Int?,
    val charging: Boolean,
    val stale: Boolean,
)

/** One device row (list layout): a title plus one gauge, or three for earbuds. */
data class DeviceRow(
    val key: String,
    val title: String,
    val status: String,
    @DrawableRes val icon: Int,
    val connected: Boolean,
    val gauges: List<Gauge>,
)

object WidgetModel {

    fun rows(context: Context, snapshot: Snapshot, settings: Settings, now: Long = System.currentTimeMillis()): List<DeviceRow> {
        val rows = mutableListOf<DeviceRow>()
        if (settings.showPhone) phoneRow(context)?.let(rows::add)
        if (!snapshot.bluetoothOn || snapshot.permissionMissing) return rows

        val visible = snapshot.devices
            .filter { it.address !in settings.hidden }
            .filter { it.connected || (settings.showRecent && it.headline != null) }
            .sortedWith(compareByDescending<DeviceBattery> { it.connected }.thenByDescending { it.lastSeen })
        visible.forEach { rows += row(it, now) }
        return rows
    }

    fun gauges(rows: List<DeviceRow>): List<Gauge> = rows.flatMap { it.gauges }

    private fun row(d: DeviceBattery, now: Long): DeviceRow {
        val offline = !d.connected
        fun gauge(suffix: String, label: String, icon: Int, cell: Cell?) = Gauge(
            key = d.address + suffix, label = label, icon = icon, level = cell?.level,
            charging = cell?.charging == true && !offline,
            stale = offline || cell == null || cell.isStale(now),
        )
        val gauges = if (d.hasBuds) {
            listOf(
                gauge(":L", "Left", R.drawable.ic_earbud_left, d.left),
                gauge(":R", "Right", R.drawable.ic_earbud_right, d.right),
                gauge(":C", "Case", R.drawable.ic_case, d.case),
            )
        } else {
            listOf(gauge("", shortName(d.name), d.type.icon, d.main))
        }
        val status = when {
            offline -> "Seen ${ago(d.lastSeen, now)}"
            gauges.any { it.charging } -> "Charging"
            else -> "Connected"
        }
        return DeviceRow(d.address, d.name, status, d.type.icon, d.connected, gauges)
    }

    private fun phoneRow(context: Context): DeviceRow? {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: return null
        val charging = bm.isCharging
        return DeviceRow(
            key = "phone", title = "This phone", status = if (charging) "Charging" else "Battery",
            icon = DeviceType.PHONE.icon, connected = true,
            gauges = listOf(Gauge("phone", "Phone", DeviceType.PHONE.icon, level, charging, stale = false)),
        )
    }

    fun shortName(name: String): String =
        name.replace(Regex("^(the |my )", RegexOption.IGNORE_CASE), "").let { if (it.length > 14) it.take(13).trimEnd() + "…" else it }

    fun ago(then: Long, now: Long = System.currentTimeMillis()): String {
        val m = ((now - then) / 60_000).coerceAtLeast(0)
        return when {
            m < 1 -> "just now"
            m < 60 -> "${m}m ago"
            m < 24 * 60 -> "${m / 60}h ago"
            else -> "${m / (24 * 60)}d ago"
        }
    }
}
