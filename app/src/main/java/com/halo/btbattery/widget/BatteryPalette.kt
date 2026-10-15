package com.halo.btbattery.widget

import android.content.Context
import android.os.Build
import com.halo.btbattery.model.AccentStyle

/**
 * Level colours, shared by widget and app: green while healthy or charging, amber at 20 %,
 * red at 10 %. Each is a start/end pair so a custom accent can still use a gradient.
 */
object BatteryPalette {
    // Solid tones, as ColorOS draws its device rings (green sampled from the system widget).
    const val GREEN_A = 0xFF26B235.toInt()
    const val GREEN_B = 0xFF26B235.toInt()
    const val AMBER_A = 0xFFFF9500.toInt()
    const val AMBER_B = 0xFFFF9500.toInt()
    const val RED_A = 0xFFFF3B30.toInt()
    const val RED_B = 0xFFFF3B30.toInt()
    const val UNKNOWN = 0xFFA0A4AB.toInt()

    fun gradient(context: Context, level: Int?, charging: Boolean, accent: AccentStyle): Pair<Int, Int> = when {
        level == null -> UNKNOWN to UNKNOWN
        charging -> GREEN_A to GREEN_B
        level <= 10 -> RED_A to RED_B
        level <= 20 -> AMBER_A to AMBER_B
        else -> healthy(context, accent)
    }

    fun healthy(context: Context, accent: AccentStyle): Pair<Int, Int> = when (accent) {
        AccentStyle.FLUID -> GREEN_A to GREEN_B
        AccentStyle.MONO -> 0xFFA7ABB2.toInt() to 0xFF7D828B.toInt()
        AccentStyle.WALLPAPER ->
            if (Build.VERSION.SDK_INT >= 31) {
                context.getColor(android.R.color.system_accent1_300) to context.getColor(android.R.color.system_accent1_500)
            } else GREEN_A to GREEN_B
    }
}
