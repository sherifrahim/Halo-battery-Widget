package com.halo.btbattery.model

import androidx.annotation.DrawableRes
import com.halo.btbattery.R

enum class DeviceType(val label: String, @DrawableRes val icon: Int) {
    EARBUDS("Earbuds", R.drawable.ic_earbuds),
    HEADPHONES("Headphones", R.drawable.ic_headphones),
    SPEAKER("Speaker", R.drawable.ic_speaker),
    WATCH("Watch", R.drawable.ic_watch),
    KEYBOARD("Keyboard", R.drawable.ic_keyboard),
    MOUSE("Mouse", R.drawable.ic_mouse),
    GAMEPAD("Controller", R.drawable.ic_gamepad),
    PHONE("Phone", R.drawable.ic_phone),
    OTHER("Device", R.drawable.ic_bluetooth);

    val isAudio get() = this == EARBUDS || this == HEADPHONES || this == SPEAKER || this == OTHER
}

/** Where a reading came from. */
enum class Source(val label: String) {
    SYSTEM("Android"),
    FAST_PAIR("Fast Pair"),
    APPLE("AirPods"),
    GATT("Battery service"),
}

/** One battery: a level (0–100), whether it is charging, and when it was read. */
data class Cell(
    val level: Int,
    val charging: Boolean = false,
    val at: Long = System.currentTimeMillis(),
) {
    fun isStale(now: Long = System.currentTimeMillis()) = now - at > STALE_AFTER_MS

    companion object {
        /** Case and bud readings come from BLE adverts that stop when the case closes. */
        const val STALE_AFTER_MS = 30 * 60_000L
    }
}

data class DeviceBattery(
    val address: String,
    val name: String,
    val type: DeviceType,
    val connected: Boolean,
    val main: Cell? = null,
    val left: Cell? = null,
    val right: Cell? = null,
    val case: Cell? = null,
    val sources: Set<Source> = emptySet(),
    val lastSeen: Long = System.currentTimeMillis(),
) {
    val hasBuds get() = left != null || right != null

    /** The single number that best represents the device: the weaker bud, or the main battery. */
    val headline: Cell?
        get() = if (hasBuds) listOfNotNull(left, right).minByOrNull { it.level } else main
}

data class Snapshot(
    val devices: List<DeviceBattery> = emptyList(),
    val updatedAt: Long = 0L,
    val bluetoothOn: Boolean = true,
    val permissionMissing: Boolean = false,
)

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }
enum class SurfaceStyle(val label: String) { SOLID("Solid"), GLASS("Translucent") }
enum class AccentStyle(val label: String) { FLUID("Green"), WALLPAPER("Wallpaper"), MONO("Mono") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val surface: SurfaceStyle = SurfaceStyle.SOLID,
    val accent: AccentStyle = AccentStyle.FLUID,
    val showPhone: Boolean = true,
    val showRecent: Boolean = true,
    val liveMode: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val typeOverrides: Map<String, DeviceType> = emptyMap(),
)
