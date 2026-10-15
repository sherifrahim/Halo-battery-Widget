package com.halo.btbattery

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.halo.btbattery.widget.HaloWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.halo.btbattery.work.RefreshWorker

class HaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_LIVE, getString(R.string.channel_live), NotificationManager.IMPORTANCE_MIN)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        RefreshWorker.schedulePeriodic(this)
        // Frosted widgets are tinted with the wallpaper's colours: redraw them when it changes.
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                WallpaperManager.getInstance(this).addOnColorsChangedListener(
                    { _, _ -> appScope.launch { HaloWidget.updateAll(this@HaloApp) } },
                    Handler(Looper.getMainLooper()),
                )
            }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val CHANNEL_LIVE = "live"
    }
}
