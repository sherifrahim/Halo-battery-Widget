package com.halo.btbattery.bt

import com.halo.btbattery.model.Source

/** Battery read from a BLE advertisement. Levels are 0–100 or null when that part is absent. */
data class BudsAd(
    val source: Source,
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val single: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val singleCharging: Boolean = false,
    val model: String? = null,
    val rssi: Int = -127,
) {
    val hasAnything get() = left != null || right != null || case != null || single != null

    /** A comparable number for matching against Android's own (single) level. */
    val representative: Int?
        get() = single ?: listOfNotNull(left, right).takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
}

private fun Byte.u() = toInt() and 0xFF

/**
 * Apple "proximity pairing" message (manufacturer 0x004C, type 0x07) broadcast by AirPods and
 * Beats while out of the case or with the lid open. Batteries are 4-bit values in steps of 10%.
 */
object AppleProximityParser {
    const val APPLE_COMPANY_ID = 0x004C

    private val models = mapOf(
        0x0220 to "AirPods",
        0x0F20 to "AirPods (2nd gen)",
        0x1320 to "AirPods (3rd gen)",
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x2420 to "AirPods Pro 2",
        0x0A20 to "AirPods Max",
        0x0B20 to "Powerbeats Pro",
    )
    private val singleBattery = setOf(0x0A20)

    /** [data] is the manufacturer-specific payload for company 0x004C, i.e. without the ID. */
    fun parse(data: ByteArray, rssi: Int = -127): BudsAd? {
        if (data.size < 9 || data[0].u() != 0x07 || data[1].u() != 0x19) return null
        val model = (data[3].u() shl 8) or data[4].u()
        val status = data[5].u()
        // Which nibble is left depends on which bud is "primary"; bit 5 of the status byte tells us.
        val flipped = status and 0x20 == 0
        val buds = data[6].u()
        val hi = buds shr 4
        val lo = buds and 0x0F
        val leftRaw = if (flipped) hi else lo
        val rightRaw = if (flipped) lo else hi
        val flags = data[7].u() shr 4
        val caseRaw = data[7].u() and 0x0F

        val leftCh = flags and (if (flipped) 0b010 else 0b001) != 0
        val rightCh = flags and (if (flipped) 0b001 else 0b010) != 0
        val caseCh = flags and 0b100 != 0

        val name = models[model]
        val ad = if (model in singleBattery) {
            BudsAd(Source.APPLE, single = level(leftRaw) ?: level(rightRaw), singleCharging = leftCh || rightCh, model = name, rssi = rssi)
        } else {
            BudsAd(
                Source.APPLE,
                left = level(leftRaw), right = level(rightRaw), case = level(caseRaw),
                leftCharging = leftCh, rightCharging = rightCh, caseCharging = caseCh,
                model = name, rssi = rssi,
            )
        }
        return ad.takeIf { it.hasAnything }
    }

    /** 0–9 → middle of that 10% band, 10 → full, 15 → unknown. */
    private fun level(raw: Int): Int? = when (raw) {
        10 -> 100
        in 0..9 -> raw * 10 + 5
        else -> null
    }
}

/**
 * Google Fast Pair "not discoverable" advertisement (service data for UUID 0xFE2C). Used by
 * Pixel Buds, OnePlus/OPPO Enco, Sony, JBL, Jabra, Nothing and many others. It can carry a
 * battery notification: one byte per component, bit 7 = charging, bits 0–6 = percent.
 */
object FastPairParser {
    const val SERVICE_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"

    private const val TYPE_BATTERY_SHOW_UI = 0x3
    private const val TYPE_BATTERY_HIDE_UI = 0x4

    fun parse(serviceData: ByteArray, rssi: Int = -127): BudsAd? {
        // 3 bytes is a discoverable advert holding only the model ID: no battery.
        if (serviceData.size <= 3) return null
        var i = 1 // byte 0: version + flags
        while (i < serviceData.size) {
            val header = serviceData[i].u()
            val length = header shr 4
            val type = header and 0x0F
            val end = i + length
            if (end >= serviceData.size) break
            if ((type == TYPE_BATTERY_SHOW_UI || type == TYPE_BATTERY_HIDE_UI) && length in 1..3) {
                val values = (1..length).map { serviceData[i + it].u() }
                return fromValues(values, rssi)
            }
            i = end + 1
        }
        return null
    }

    private fun fromValues(values: List<Int>, rssi: Int): BudsAd? {
        fun lvl(b: Int) = (b and 0x7F).takeIf { it <= 100 }
        fun chg(b: Int) = b and 0x80 != 0
        val ad = if (values.size == 1) {
            BudsAd(Source.FAST_PAIR, single = lvl(values[0]), singleCharging = chg(values[0]), rssi = rssi)
        } else {
            BudsAd(
                Source.FAST_PAIR,
                left = lvl(values[0]), right = lvl(values[1]), case = values.getOrNull(2)?.let(::lvl),
                leftCharging = chg(values[0]), rightCharging = chg(values[1]),
                caseCharging = values.getOrNull(2)?.let(::chg) ?: false,
                rssi = rssi,
            )
        }
        return ad.takeIf { it.hasAnything }
    }
}
