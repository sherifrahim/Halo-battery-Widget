package com.halo.btbattery.watch

import android.os.BatteryManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Answers the phone's battery request with "level,charging". Runs only when asked. */
class BatteryReplyService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_REQUEST) return
        val bm = getSystemService(BatteryManager::class.java) ?: return
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: return
        val payload = "$level,${if (bm.isCharging) 1 else 0}".toByteArray()
        Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, PATH_REPORT, payload)
    }

    companion object {
        const val PATH_REQUEST = "/halo/battery/request"
        const val PATH_REPORT = "/halo/battery/report"
    }
}
