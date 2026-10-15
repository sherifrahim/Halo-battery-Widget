package com.halo.btbattery

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.halo.btbattery.work.RefreshWorker

class HaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_LIVE, getString(R.string.channel_live), NotificationManager.IMPORTANCE_MIN)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        RefreshWorker.schedulePeriodic(this)
    }

    companion object {
        const val CHANNEL_LIVE = "live"
    }
}
