package com.halo.btbattery.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.halo.btbattery.model.AccentStyle
import kotlin.math.roundToInt

/**
 * App widgets can't draw arcs or gradients, so rings and capsules are painted into bitmaps.
 * Each gets a soft outer glow, the "luminous" edge from the Fluid Fusion look.
 */
object GaugeRenderer {

    fun ring(
        context: Context,
        sizeDp: Float,
        level: Int?,
        charging: Boolean,
        stale: Boolean,
        accent: AccentStyle,
        strokeRatio: Float = 0.105f,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceIn(24, 360)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = px * strokeRatio
        val inset = stroke / 2f + stroke * 0.55f // leave room for the glow
        val rect = RectF(inset, inset, px - inset, px - inset)
        val (c1, c2) = BatteryPalette.gradient(context, level, charging, accent)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = withAlpha(c1, 0x30)
        }
        canvas.drawOval(rect, track)

        if (level != null && level > 0) {
            val sweep = 360f * level.coerceIn(0, 100) / 100f
            val cx = px / 2f
            val radius = rect.width() / 2f
            // Rotate the gradient back by the round cap so the cap isn't painted with the end colour.
            val capDegrees = Math.toDegrees((stroke / 2f / radius).toDouble()).toFloat()
            val shader = SweepGradient(cx, cx, intArrayOf(c1, c2, c2), floatArrayOf(0f, (sweep / 360f).coerceAtLeast(0.01f), 1f))
                .apply { setLocalMatrix(Matrix().apply { setRotate(-90f - capDegrees, cx, cx) }) }
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                this.shader = shader
                if (!stale) setShadowLayer(stroke * 0.55f, 0f, 0f, withAlpha(c2, 0x8C))
                if (stale) alpha = 0x70
            }
            canvas.drawArc(rect, -90f, sweep, false, arc)
        }
        return bmp
    }

    fun capsule(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        level: Int?,
        charging: Boolean,
        stale: Boolean,
        accent: AccentStyle,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt().coerceIn(24, 1400)
        val h = (heightDp * density).roundToInt().coerceIn(6, 120)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = h * 0.18f
        val barH = h - pad * 2
        val r = barH / 2f
        val (c1, c2) = BatteryPalette.gradient(context, level, charging, accent)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(c1, 0x2A) }
        canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), r, r, track)

        if (level != null && level > 0) {
            val full = w - pad * 2
            val fillW = (full * level.coerceIn(0, 100) / 100f).coerceAtLeast(barH)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(pad, 0f, pad + fillW, 0f, c1, c2, Shader.TileMode.CLAMP)
                if (!stale) setShadowLayer(pad * 0.9f, 0f, 0f, withAlpha(c2, 0x80))
                if (stale) alpha = 0x70
            }
            canvas.drawRoundRect(RectF(pad, pad, pad + fillW, h - pad), r, r, fill)
        }
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
