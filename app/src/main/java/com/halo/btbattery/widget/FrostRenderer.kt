package com.halo.btbattery.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import kotlin.math.roundToInt
import kotlin.random.Random

/** Your wallpaper's dominant colours, which Android shares with any app without a permission. */
object WallpaperTint {
    /** Lets previews and tests supply colours; null means "ask the system". */
    @Volatile internal var override: List<Int>? = null

    private val fallback = listOf(0xFF3B4A66.toInt(), 0xFF5E4A63.toInt(), 0xFF2F5552.toInt())

    fun colors(context: Context): List<Int> {
        override?.let { return it }
        if (Build.VERSION.SDK_INT < 27) return fallback
        val wc = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
            ?: return fallback
        val list = listOfNotNull(wc.primaryColor.toArgb(), wc.secondaryColor?.toArgb(), wc.tertiaryColor?.toArgb())
        return (list + fallback).take(3)
    }
}

/**
 * The "blurred wallpaper" under a frosted widget. Widgets can't blur what's behind them (only the
 * system launcher can), but a heavily blurred wallpaper is essentially a soft field of its main
 * colours, which we can paint. A frost tint goes on top as a day/night drawable, so it follows the
 * system theme without re-rendering.
 */
object FrostRenderer {

    fun wash(context: Context, widthDp: Float, heightDp: Float, radiusDp: Float = 24f): Bitmap {
        // One pixel per dp is plenty for something this soft, and keeps widget memory tiny.
        val w = widthDp.roundToInt().coerceIn(24, 900)
        val h = heightDp.roundToInt().coerceIn(24, 900)
        val (c1, c2, c3) = WallpaperTint.colors(context)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.clipPath(Path().apply { addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radiusDp, radiusDp, Path.Direction.CW) })
        canvas.drawColor(blend(c1, c2, 0.5f))
        val span = maxOf(w, h).toFloat()
        fun bloom(color: Int, x: Float, y: Float, r: Float) = canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        bloom(c1, w * 0.18f, h * 0.05f, span * 0.95f)
        bloom(c2, w * 1.0f, h * 0.45f, span * 0.8f)
        bloom(c3, w * 0.35f, h * 1.15f, span * 0.75f)
        addGrain(bmp)
        return bmp
    }

    /** Fine luminance noise, the texture that makes flat colour read as frosted glass. */
    private fun addGrain(bmp: Bitmap) {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val rnd = Random(7)
        for (i in px.indices) {
            val p = px[i]
            if (p ushr 24 == 0) continue
            val n = rnd.nextInt(-7, 8)
            val r = ((p shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((p shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((p and 0xFF) + n).coerceIn(0, 255)
            px[i] = (p and -0x1000000) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
