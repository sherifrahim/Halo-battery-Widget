package com.halo.btbattery.widget

import android.content.Context
import android.os.Build
import com.halo.btbattery.model.AccentStyle

/**
 * Level colours, shared by the widget and the app. Healthy batteries glow mint→aqua in the
 * Fluid style; low and critical switch to amber and coral whatever the accent.
 */
object BatteryPalette {
    const val MINT = 0xFF2FE6A6.toInt()
    const val AQUA = 0xFF2BC8FF.toInt()
    const val CHARGE_A = 0xFF3BEA7C.toInt()
    const val CHARGE_B = 0xFF2FE6A6.toInt()
    const val AMBER_A = 0xFFFFC145.toInt()
    const val AMBER_B = 0xFFFF8A3D.toInt()
    const val CORAL_A = 0xFFFF6A5F.toInt()
    const val CORAL_B = 0xFFFF3B6B.toInt()
    const val UNKNOWN = 0xFF8E939E.toInt()

    /** Gradient start/end for a level. */
    fun gradient(context: Context, level: Int?, charging: Boolean, accent: AccentStyle): Pair<Int, Int> = when {
        level == null -> UNKNOWN to UNKNOWN
        charging -> CHARGE_A to CHARGE_B
        level <= 10 -> CORAL_A to CORAL_B
        level <= 20 -> AMBER_A to AMBER_B
        else -> healthy(context, accent)
    }

    fun healthy(context: Context, accent: AccentStyle): Pair<Int, Int> = when (accent) {
        AccentStyle.FLUID -> MINT to AQUA
        AccentStyle.MONO -> 0xFF9AA0AB.toInt() to 0xFFC9CDD4.toInt()
        AccentStyle.WALLPAPER ->
            if (Build.VERSION.SDK_INT >= 31) {
                context.getColor(android.R.color.system_accent1_300) to context.getColor(android.R.color.system_accent3_300)
            } else MINT to AQUA
    }
}
