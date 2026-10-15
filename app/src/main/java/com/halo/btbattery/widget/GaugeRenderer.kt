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
 * Solid level colour with a faint light→base sweep; a soft halo only while charging.
 */
object GaugeRenderer {

    fun ring(
        context: Context,
        sizeDp: Float,
        level: Int?,
        charging: Boolean,
        stale: Boolean,
        accent: AccentStyle,
        strokeRatio: Float = 0.12f,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceIn(24, 360)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = px * strokeRatio
        val inset = stroke / 2f + stroke * 0.5f // same room for every ring so sizes match
        val rect = RectF(inset, inset, px - inset, px - inset)
        val (c1, c2) = BatteryPalette.gradient(context, level, charging, accent)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = withAlpha(c2, 0x29)
        }
        canvas.drawOval(rect, track)

        if (level != null && level > 0) {
            val sweep = 360f * level.coerceIn(0, 100) / 100f
            val cx = px / 2f
            val radius = rect.width() / 2f
            // Start the gradient a cap-width early so the round start cap keeps the start colour.
            val capDegrees = Math.toDegrees((stroke / 2f / radius).toDouble()).toFloat()
            val end = ((sweep + capDegrees) / 360f).coerceIn(0.02f, 1f)
            val shader = SweepGradient(cx, cx, intArrayOf(c1, c2, c2), floatArrayOf(0f, end, 1f))
                .apply { setLocalMatrix(Matrix().apply { setRotate(-90f - capDegrees, cx, cx) }) }
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                this.shader = shader
                if (charging && !stale) setShadowLayer(stroke * 0.5f, 0f, 0f, withAlpha(c2, 0x80))
                if (stale) alpha = 0x6B
            }
            canvas.drawArc(rect, -90f, sweep, false, arc)
        }
        return bmp
    }

    /** Thick capsule bar with a glossy top sheen. */
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
        val top = pad
        val bottom = h - pad
        val r = (bottom - top) / 2f
        val (c1, c2) = BatteryPalette.gradient(context, level, charging, accent)

        canvas.drawRoundRect(RectF(pad, top, w - pad, bottom), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(c2, 0x24) })

        if (level != null && level > 0) {
            val full = w - pad * 2
            val fillW = (full * level.coerceIn(0, 100) / 100f).coerceAtLeast(bottom - top)
            val bar = RectF(pad, top, pad + fillW, bottom)
            canvas.drawRoundRect(bar, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, bottom, c1, c2, Shader.TileMode.CLAMP)
                if (charging && !stale) setShadowLayer(pad * 0.9f, 0f, 0f, withAlpha(c2, 0x80))
                if (stale) alpha = 0x6B
            })
            // Gloss: a thin bright band across the top half.
            if (!stale) canvas.drawRoundRect(
                RectF(bar.left + r * 0.6f, top + (bottom - top) * 0.12f, bar.right - r * 0.6f, top + (bottom - top) * 0.45f),
                r, r,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, top, 0f, top + (bottom - top) * 0.45f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                },
            )
        }
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
