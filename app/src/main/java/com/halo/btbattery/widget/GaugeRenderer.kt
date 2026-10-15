package com.halo.btbattery.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.halo.btbattery.model.AccentStyle
import kotlin.math.roundToInt

/** App widgets can't draw arcs, so battery rings are painted into bitmaps. */
object GaugeRenderer {

    /** Thin solid ring on a faint neutral track, like the ColorOS device widget. */
    fun ring(
        context: Context,
        sizeDp: Float,
        level: Int?,
        charging: Boolean,
        stale: Boolean,
        accent: AccentStyle,
        strokeRatio: Float = 0.07f,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceIn(24, 360)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = (px * strokeRatio).coerceAtLeast(2f * density)
        val inset = stroke / 2f + 0.5f
        val rect = RectF(inset, inset, px - inset, px - inset)
        val (_, levelColor) = BatteryPalette.gradient(context, level, charging, accent)

        // Neutral grey track reads correctly on both the light and the dark tile.
        canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = 0x3D8E8E93
        })
        if (level != null && level > 0) {
            canvas.drawArc(rect, -90f, 360f * level.coerceIn(0, 100) / 100f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = levelColor
                if (stale) alpha = 0x73
            })
        }
        return bmp
    }
}
