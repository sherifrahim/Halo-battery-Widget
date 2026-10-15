package com.halo.btbattery.bt

import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Source
import kotlin.math.abs

/**
 * BLE adverts use random addresses, so they can't be tied to a paired device by MAC. We match
 * them by vendor, signal strength and how close their level is to the one Android reports —
 * which keeps a stranger's AirPods on the next table from showing up as yours.
 */
object AdMatcher {
    private const val MIN_RSSI = -78
    private const val MAX_LEVEL_GAP = 25

    fun match(ads: List<BudsAd>, devices: List<DeviceBattery>): Map<String, BudsAd> {
        val result = LinkedHashMap<String, BudsAd>()
        val used = HashSet<BudsAd>()
        val candidates = devices.filter {
            it.connected && (it.type == DeviceType.EARBUDS || it.type == DeviceType.HEADPHONES || it.type == DeviceType.OTHER)
        }
        val strong = ads.filter { it.rssi >= MIN_RSSI }

        fun pick(device: DeviceBattery, pool: List<BudsAd>): BudsAd? {
            val system = device.main?.level
            return pool.filter { it !in used }
                .filter { ad -> system == null || ad.representative == null || abs(ad.representative!! - system) <= MAX_LEVEL_GAP }
                .minByOrNull { ad ->
                    val gap = if (system != null && ad.representative != null) abs(ad.representative!! - system) else 15
                    gap * 2 - ad.rssi / 4
                }
        }

        val apple = strong.filter { it.source == Source.APPLE }
        val fastPair = strong.filter { it.source == Source.FAST_PAIR }

        // Named Apple devices take Apple adverts first.
        for (d in candidates.filter { DeviceClassifier.isAppleAudioName(it.name) }) {
            pick(d, apple)?.let { result[d.address] = it; used += it }
        }
        // Everyone else takes Fast Pair adverts.
        val others = candidates.filter { it.address !in result && !DeviceClassifier.isAppleAudioName(it.name) }
        for (d in others) {
            // With several unknown devices and no system level to compare, a match is a guess: skip it.
            if (others.size > 1 && d.main == null) continue
            pick(d, fastPair)?.let { result[d.address] = it; used += it }
        }
        // A renamed pair of AirPods: only when it is the one earbud device left and the advert is close.
        val leftover = others.filter { it.address !in result && it.type == DeviceType.EARBUDS }
        if (leftover.size == 1) {
            pick(leftover[0], apple.filter { it.rssi >= -60 })?.let { result[leftover[0].address] = it }
        }
        return result
    }
}
