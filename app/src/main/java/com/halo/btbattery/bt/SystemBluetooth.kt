package com.halo.btbattery.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What Android itself knows: which devices are connected, and the battery level the headset
 * reported over HFP (the same number shown in the status bar and Bluetooth settings).
 */
@SuppressLint("MissingPermission")
object SystemBluetooth {

    /** Broadcast Android sends when a headset reports a new battery level. Hidden in the SDK. */
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private const val PROFILE_HID_HOST = 4 // BluetoothProfile.HID_HOST, hidden constant

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isOn(context: Context) = runCatching { adapter(context)?.isEnabled == true }.getOrDefault(false)

    suspend fun connectedDevices(context: Context): List<BluetoothDevice> {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        val found = LinkedHashMap<String, BluetoothDevice>()
        var reflectionWorks = true
        for (device in adapter.bondedDevices.orEmpty()) {
            when (isConnected(device)) {
                true -> found[device.address] = device
                false -> Unit
                null -> reflectionWorks = false
            }
        }
        if (!reflectionWorks) {
            for (device in profileConnected(context, adapter)) found[device.address] = device
        }
        // BLE-only wearables (watches, bands) that are bonded and connected over GATT.
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }.getOrDefault(emptyList())
            .filter { it.bondState == BluetoothDevice.BOND_BONDED }
            .forEach { found.putIfAbsent(it.address, it) }
        return found.values.toList()
    }

    /** Level from the hidden BluetoothDevice.getBatteryLevel(); null when the device never reported one. */
    fun batteryLevel(device: BluetoothDevice): Int? = runCatching {
        device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun isConnected(device: BluetoothDevice): Boolean? = runCatching {
        device.javaClass.getMethod("isConnected").invoke(device) as Boolean
    }.getOrNull()

    fun safeName(device: BluetoothDevice): String =
        (if (Build.VERSION.SDK_INT >= 30) runCatching { device.alias }.getOrNull() else null)?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: device.address

    /** Fallback when reflection is blocked: ask each audio/input profile who is connected. */
    private suspend fun profileConnected(context: Context, adapter: BluetoothAdapter): List<BluetoothDevice> {
        val profiles = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            add(PROFILE_HID_HOST)
            if (Build.VERSION.SDK_INT >= 29) add(BluetoothProfile.HEARING_AID)
            if (Build.VERSION.SDK_INT >= 33) add(BluetoothProfile.LE_AUDIO)
        }
        return profiles.flatMap { profile ->
            withTimeoutOrNull(1500) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                            val devices = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                            runCatching { adapter.closeProfileProxy(p, proxy) }
                            if (cont.isActive) cont.resume(devices)
                        }

                        override fun onServiceDisconnected(p: Int) = Unit
                    }
                    val ok = runCatching { adapter.getProfileProxy(context, listener, profile) }.getOrDefault(false)
                    if (!ok && cont.isActive) cont.resume(emptyList())
                }
            } ?: emptyList()
        }
    }
}
