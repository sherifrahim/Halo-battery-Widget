package com.halo.btbattery.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.halo.btbattery.model.DeviceType
import kotlin.math.roundToInt

/**
 * Stand-in product images for the "Selected device" widget. ColorOS shows a photo of the actual
 * model; we can't know the model, so earbuds get a rendered white charging case and everything
 * else a large softly shaded glyph.
 */
object ProductArt {

    fun render(context: Context, type: DeviceType, sizeDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceIn(32, 420)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (type == DeviceType.EARBUDS) drawCase(c, px.toFloat()) else drawGlyph(context, c, px, type)
        return bmp
    }

    private fun drawCase(c: Canvas, s: Float) {
        val body = RectF(s * 0.1f, s * 0.26f, s * 0.9f, s * 0.8f)
        val r = s * 0.2f
        // Soft contact shadow.
        c.drawOval(RectF(s * 0.16f, s * 0.76f, s * 0.84f, s * 0.88f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(s * 0.5f, s * 0.82f, s * 0.36f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        })
        // Glossy white body.
        c.drawRoundRect(body, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, body.top, 0f, body.bottom,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2F2F4.toInt(), 0xFFD6D7DA.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        // Lid seam.
        val seam = body.top + body.height() * 0.4f
        c.drawLine(body.left + r * 0.18f, seam, body.right - r * 0.18f, seam, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB9BABD.toInt(); strokeWidth = s * 0.012f
        })
        // Specular highlight on the lid.
        c.drawRoundRect(RectF(body.left + s * 0.08f, body.top + s * 0.03f, body.right - s * 0.08f, seam - s * 0.04f), r * 0.7f, r * 0.7f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, body.top, 0f, seam, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        // Status LED.
        c.drawCircle(s * 0.5f, body.top + body.height() * 0.62f, s * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2BB033.toInt() })
    }

    private fun drawGlyph(context: Context, c: Canvas, px: Int, type: DeviceType) {
        val d = ContextCompat.getDrawable(context, type.icon)?.mutate() ?: return
        val inset = (px * 0.14f).roundToInt()
        d.setTint(0xFFE6E6E8.toInt())
        d.setBounds(inset, inset, px - inset, px - inset)
        d.draw(c)
    }
}
