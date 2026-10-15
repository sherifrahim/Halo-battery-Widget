package com.halo.btbattery.service

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.halo.btbattery.HaloApp
import com.halo.btbattery.R
import com.halo.btbattery.data.BatteryRepository
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.bt.SystemBluetooth
import com.halo.btbattery.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional "Live updates" mode. Keeps a quiet notification so the widget follows battery changes
 * within seconds and keeps the earbud case level fresh, instead of every 15 minutes.
 */
class LiveMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val scan = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            scope.launch {
                if (scan) delay(4_000)
                BatteryRepository.refresh(applicationContext, scan = scan)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, HaloApp.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("Live battery is on")
            .setContentText("Your Halo widget updates as levels change")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { stopSelf(); return }

        val filter = IntentFilter().apply {
            addAction(SystemBluetooth.ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        loop = scope.launch {
            while (isActive) {
                val snapshot = HaloStore.get(applicationContext).snapshot.first()
                val budsConnected = snapshot.devices.any { it.connected && it.type.isAudio }
                BatteryRepository.refresh(applicationContext, scan = budsConnected)
                // Scan often while earbuds are around; otherwise just poll lightly.
                delay(if (budsConnected) 60_000 else 5 * 60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LiveMonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveMonitorService::class.java))
        }
    }
}
