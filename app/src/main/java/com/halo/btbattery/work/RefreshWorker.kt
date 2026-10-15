package com.halo.btbattery.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.halo.btbattery.HaloApp
import com.halo.btbattery.R
import com.halo.btbattery.data.BatteryRepository
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        BatteryRepository.refresh(applicationContext, scan = inputData.getBoolean(KEY_SCAN, true))
        return Result.success()
    }

    // Only used on Android 11 and below, where expedited work runs as a foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n: Notification = NotificationCompat.Builder(applicationContext, HaloApp.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("Updating battery levels")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val KEY_SCAN = "scan"
        private const val NOTIFICATION_ID = 41
        private const val PERIODIC = "halo-periodic"

        /** Coalesces bursts: a new request with the same [tag] replaces a pending one. */
        fun enqueue(context: Context, scan: Boolean = true, delaySeconds: Long = 0, tag: String = "now") {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_SCAN to scan))
                .apply {
                    if (delaySeconds > 0) setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    else setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("halo-$tag", ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_SCAN to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
