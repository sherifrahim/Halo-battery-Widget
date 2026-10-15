package com.halo.btbattery.bt

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Short, filtered BLE scan that collects earbud battery adverts (AirPods/Beats and Fast Pair).
 * Individual bud and case levels are only available this way: Android's own API reports one number.
 */
@SuppressLint("MissingPermission")
object BleBatteryScanner {

    private val fastPairUuid = ParcelUuid.fromString(FastPairParser.SERVICE_UUID)

    // Android silently blocks apps that start more than 5 scans in 30 s.
    private const val MIN_GAP_MS = 7_000L
    @Volatile private var lastStart = 0L

    suspend fun scan(context: Context, durationMs: Long = 5_000, lowPower: Boolean = false): List<BudsAd> {
        if (!Permissions.canScan(context)) return emptyList()
        val scanner = SystemBluetooth.adapter(context)?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: return emptyList()
        val now = SystemClock.elapsedRealtime()
        if (now - lastStart < MIN_GAP_MS) return emptyList()
        lastStart = now

        val found = ConcurrentHashMap<String, BudsAd>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parse(result)?.let { ad ->
                    // Adverts use rotating random addresses; keep the strongest per address.
                    found.merge(result.device.address, ad) { old, new -> if (new.rssi >= old.rssi - 3) new else old }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onScanResult(0, it) }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(AppleProximityParser.APPLE_COMPANY_ID, byteArrayOf(0x07), byteArrayOf(0xFF.toByte()))
                .build(),
            ScanFilter.Builder().setServiceData(fastPairUuid, ByteArray(0)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val started = runCatching { scanner.startScan(filters, settings, callback) }.isSuccess
        if (!started) return emptyList()
        try {
            delay(durationMs)
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        return found.values.toList()
    }

    private fun parse(result: ScanResult): BudsAd? {
        val record = result.scanRecord ?: return null
        record.getManufacturerSpecificData(AppleProximityParser.APPLE_COMPANY_ID)?.let { data ->
            AppleProximityParser.parse(data, result.rssi)?.let { return it }
        }
        record.getServiceData(fastPairUuid)?.let { data ->
            FastPairParser.parse(data, result.rssi)?.let { return it }
        }
        return null
    }
}
