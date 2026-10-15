package com.halo.btbattery.preview

import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.model.Source

object SampleData {
    private val now = System.currentTimeMillis()
    private const val MIN = 60_000L

    val snapshot = Snapshot(
        devices = listOf(
            DeviceBattery(
                "AA:01", "OnePlus Buds Pro 3", DeviceType.EARBUDS, connected = true,
                main = Cell(80, at = now), left = Cell(85, at = now), right = Cell(80, at = now),
                case = Cell(42, charging = true, at = now - 2 * MIN),
                sources = setOf(Source.SYSTEM, Source.FAST_PAIR),
            ),
            DeviceBattery(
                "AA:02", "OPPO Watch X", DeviceType.WATCH, connected = true,
                main = Cell(67, at = now), sources = setOf(Source.GATT),
            ),
            DeviceBattery(
                "AA:03", "JBL Flip 6", DeviceType.SPEAKER, connected = true,
                main = Cell(18, at = now), sources = setOf(Source.SYSTEM),
            ),
            DeviceBattery(
                "AA:04", "Sony WH-1000XM5", DeviceType.HEADPHONES, connected = false,
                main = Cell(54, at = now - 180 * MIN), sources = setOf(Source.SYSTEM), lastSeen = now - 180 * MIN,
            ),
        ),
        updatedAt = now - MIN,
    )
}
