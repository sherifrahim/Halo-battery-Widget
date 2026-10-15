package com.halo.btbattery.bt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /** What the app asks for on the welcome card. */
    val required: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
            // Before Android 12, BLE scan results need location.
            else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun canConnect(context: Context) =
        Build.VERSION.SDK_INT < 31 || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    fun canScan(context: Context) =
        if (Build.VERSION.SDK_INT >= 31) granted(context, Manifest.permission.BLUETOOTH_SCAN)
        else granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
