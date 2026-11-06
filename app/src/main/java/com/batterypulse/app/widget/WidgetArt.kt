package com.batterypulse.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.batterypulse.app.data.Sample

/** Colors for widget bitmaps: wallpaper accent on Android 12+, brand green otherwise. */
data class WidgetColors(val accent: Int, val track: Int, val charging: Int, val low: Int)

fun widgetColors(context: Context): WidgetColors {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return if (Build.VERSION.SDK_INT >= 31) {
        val accent = context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        val tertiary = context.getColor(if (night) android.R.color.system_accent3_200 else android.R.color.system_accent3_600)
        WidgetColors(accent, withAlpha(accent, 60), tertiary, if (night) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt())
    } else {
        val accent = if (night) 0xFF93D69F.toInt() else 0xFF2B6A3F.toInt()
        val tertiary = if (night) 0xFFA2CDDA.toInt() else 0xFF3A646F.toInt()
        WidgetColors(accent, withAlpha(accent, 60), tertiary, if (night) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt())
    }
}

private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

/** Circular level ring with a rounded progress arc. */
fun ringBitmap(level: Int, color: Int, track: Int, size: Int = 320): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val stroke = size * 0.085f
    val rect = RectF(stroke, stroke, size - stroke, size - stroke)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
    }
    p.color = track
    c.drawArc(rect, 0f, 360f, false, p)
    p.color = color
    val sweep = 360f * level.coerceIn(0, 100) / 100f
    if (sweep > 0f) c.drawArc(rect, -90f, sweep, false, p)
    return bmp
}

/** Smooth level line with gradient fill for the last [hours] hours of history. */
fun chartBitmap(history: List<Sample>, now: Long, color: Int, width: Int = 720, height: Int = 220, hours: Int = 24): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val start = now - hours * 3_600_000L
    val pts = history.filter { it.t >= start }
    val pad = height * 0.06f
    // faint gridlines
    val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, 40); strokeWidth = 2f }
    for (i in 0..2) {
        val y = pad + (height - 2 * pad) * i / 2f
        c.drawLine(0f, y, width.toFloat(), y, grid)
    }
    if (pts.size < 2) return bmp
    // bucket to ~90 points
    val buckets = 90
    val w = (now - start).toDouble() / buckets
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var i = 0
    for (b in 0 until buckets) {
        val end = start + ((b + 1) * w).toLong()
        var sum = 0.0
        var n = 0
        var tSum = 0.0
        while (i < pts.size && pts[i].t < end) {
            sum += pts[i].level; tSum += pts[i].t.toDouble(); n++; i++
        }
        if (n > 0) {
            xs.add(((tSum / n - start) / (now - start) * width).toFloat())
            ys.add((pad + (1 - sum / n / 100.0) * (height - 2 * pad)).toFloat())
        }
    }
    if (xs.size < 2) return bmp
    val line = Path().apply {
        moveTo(xs[0], ys[0])
        for (k in 1 until xs.size) {
            val mx = (xs[k - 1] + xs[k]) / 2f
            cubicTo(mx, ys[k - 1], mx, ys[k], xs[k], ys[k])
        }
    }
    val fill = Path(line).apply {
        lineTo(xs.last(), height.toFloat()); lineTo(xs.first(), height.toFloat()); close()
    }
    c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), withAlpha(color, 110), withAlpha(color, 0), Shader.TileMode.CLAMP)
    })
    c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = height * 0.03f; this.color = color
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    })
    c.drawCircle(xs.last(), ys.last(), height * 0.045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    return bmp
}
