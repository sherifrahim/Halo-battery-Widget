package com.halo.btbattery.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Reads the standard BLE Battery Service (0x180F / 0x2A19). Many watches, bands, rings and
 * BLE peripherals expose it even when Android does not show their battery anywhere.
 */
@SuppressLint("MissingPermission")
object GattBatteryReader {
    private val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    suspend fun read(context: Context, device: BluetoothDevice, timeoutMs: Long = 8_000): Int? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                var gatt: BluetoothGatt? = null

                fun finish(value: Int?) {
                    if (!done.compareAndSet(false, true)) return
                    gatt?.let { g -> runCatching { g.disconnect(); g.close() } }
                    if (cont.isActive) cont.resume(value?.takeIf { it in 0..100 })
                }

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when {
                            newState == BluetoothProfile.STATE_CONNECTED -> if (!g.discoverServices()) finish(null)
                            newState == BluetoothProfile.STATE_DISCONNECTED -> finish(null)
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        val c = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
                        if (c == null || !g.readCharacteristic(c)) finish(null)
                    }

                    override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                        finish(if (status == BluetoothGatt.GATT_SUCCESS) value.firstOrNull()?.toInt()?.and(0xFF) else null)
                    }

                    @Deprecated("Pre-Android 13 callback")
                    override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                        @Suppress("DEPRECATION")
                        finish(if (status == BluetoothGatt.GATT_SUCCESS) c.value?.firstOrNull()?.toInt()?.and(0xFF) else null)
                    }
                }

                gatt = runCatching { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
                if (gatt == null) finish(null)
                cont.invokeOnCancellation { finish(null) }
            }
        }
}
