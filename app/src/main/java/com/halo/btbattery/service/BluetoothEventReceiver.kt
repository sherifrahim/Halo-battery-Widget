package com.halo.btbattery.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.halo.btbattery.bt.SystemBluetooth
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Headsets report battery a few seconds after connecting, and buds advertise
                // their case level once they settle — so look now and again shortly after.
                RefreshWorker.enqueue(context, scan = true, delaySeconds = 4, tag = "connect")
                RefreshWorker.enqueue(context, scan = true, delaySeconds = 30, tag = "connect-followup")
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                RefreshWorker.enqueue(context, scan = false, delaySeconds = 2, tag = "disconnect")
            SystemBluetooth.ACTION_BATTERY_LEVEL_CHANGED,
            BluetoothAdapter.ACTION_STATE_CHANGED,
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" ->
                RefreshWorker.enqueue(context, scan = false, tag = "event")
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RefreshWorker.schedulePeriodic(context)
                RefreshWorker.enqueue(context, scan = false, tag = "boot")
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (HaloStore.get(context).settings.first().liveMode) LiveMonitorService.start(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
