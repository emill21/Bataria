package com.batterypulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/** Index of the point nearest to time [t] (points sorted by time). */
fun nearestIndex(points: List<Pair<Long, Float>>, t: Long): Int {
    if (points.isEmpty()) return -1
    var lo = 0
    var hi = points.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (points[mid].first < t) lo = mid + 1 else hi = mid
    }
    if (lo > 0 && t - points[lo - 1].first < points[lo].first - t) return lo - 1
    return lo
}

/**
 * Time-series chart with smooth curves, gradient fill, shaded ranges (e.g. charging) and
 * press-and-drag scrubbing. The caller shows the selected value; this draws the cursor.
 */
@Composable
fun InteractiveChart(
    points: List<Pair<Long, Float>>,
    startT: Long,
    endT: Long,
    minY: Float,
    maxY: Float,
    color: Color,
    modifier: Modifier = Modifier,
    shaded: List<Pair<Long, Long>> = emptyList(),
    shadeColor: Color = color,
    baseline: Float? = null,
    gapMs: Long = 10 * 60_000L,
    selectedT: Long? = null,
    onSelect: (Long?) -> Unit = {},
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val select by rememberUpdatedState(onSelect)
    val span = (endT - startT).coerceAtLeast(1L)
    fun xToT(x: Float, width: Int): Long = startT + (x / width.coerceAtLeast(1) * span).toLong()

    Canvas(
        modifier
            .pointerInput(startT, endT) {
                detectTapGestures(
                    onPress = { o ->
                        select(xToT(o.x, size.width))
                        tryAwaitRelease()
                        select(null)
                    },
                )
            }
            .pointerInput(startT, endT) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> select(xToT(o.x, size.width)) },
                    onDragEnd = { select(null) },
                    onDragCancel = { select(null) },
                ) { change, _ ->
                    change.consume()
                    select(xToT(change.position.x, size.width))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val ySpan = (maxY - minY).takeIf { it > 0f } ?: 1f
        fun px(t: Long) = (t - startT).toFloat() / span * w
        fun py(v: Float) = h - ((v - minY) / ySpan).coerceIn(0f, 1f) * h

        // Shaded ranges
        for ((a, b) in shaded) {
            val x0 = px(a).coerceIn(0f, w)
            val x1 = px(b).coerceIn(0f, w)
            if (x1 > x0) drawRect(shadeColor.copy(alpha = 0.10f), Offset(x0, 0f), Size(x1 - x0, h))
        }

        // Grid
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(grid.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        if (baseline != null && baseline > minY && baseline < maxY) {
            drawLine(grid, Offset(0f, py(baseline)), Offset(w, py(baseline)), strokeWidth = 1.5.dp.toPx())
        }
        if (points.size < 2) return@Canvas

        // Segments, broken at gaps
        val segments = mutableListOf<MutableList<Offset>>()
        var prevT = Long.MIN_VALUE
        for ((t, v) in points) {
            if (v.isNaN()) continue
            if (segments.isEmpty() || t - prevT > gapMs) segments.add(mutableListOf())
            segments.last().add(Offset(px(t), py(v)))
            prevT = t
        }
        val fillTo = baseline?.let { py(it.coerceIn(minY, maxY)) } ?: h
        val fillBrush = Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.0f)))
        for (seg in segments) {
            if (seg.size < 2) continue
            val line = smoothPath(seg)
            val fill = Path().apply {
                addPath(line)
                lineTo(seg.last().x, fillTo)
                lineTo(seg.first().x, fillTo)
                close()
            }
            drawPath(fill, fillBrush)
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Cursor
        val sel = selectedT?.let { nearestIndex(points, it) } ?: -1
        val cursor = if (sel >= 0) points[sel] else points.last()
        if (cursor.second.isNaN()) return@Canvas
        val c = Offset(px(cursor.first), py(cursor.second))
        if (sel >= 0) {
            drawLine(color.copy(alpha = 0.5f), Offset(c.x, 0f), Offset(c.x, h), strokeWidth = 1.5.dp.toPx())
        }
        drawCircle(surface, radius = 7.dp.toPx(), center = c)
        drawCircle(color, radius = 5.dp.toPx(), center = c)
    }
}

/** Contiguous time ranges where [predicate] holds, for shading. */
fun <T> ranges(items: List<T>, time: (T) -> Long, gapMs: Long = 10 * 60_000L, predicate: (T) -> Boolean): List<Pair<Long, Long>> {
    val out = mutableListOf<Pair<Long, Long>>()
    var start: Long? = null
    var last = 0L
    for (item in items) {
        val t = time(item)
        val on = predicate(item)
        if (start != null && (t - last > gapMs || !on)) {
            out.add(start to last); start = null
        }
        if (on && start == null) start = t
        last = t
    }
    if (start != null) out.add(start to last)
    return out
}


/**
 * Averages points into [buckets] time buckets so dense, noisy minute data draws as a clean
 * line. Empty buckets are skipped, so real gaps stay gaps.
 */
fun downsample(points: List<Pair<Long, Float>>, start: Long, end: Long, buckets: Int = 140): List<Pair<Long, Float>> {
    if (points.size <= buckets || end <= start) return points
    val w = (end - start).toDouble() / buckets
    val out = ArrayList<Pair<Long, Float>>(buckets)
    var i = 0
    while (i < points.size && points[i].first < start) i++
    for (b in 0 until buckets) {
        val bEnd = start + ((b + 1) * w).toLong()
        var sum = 0.0
        var tSum = 0.0
        var n = 0
        while (i < points.size && (points[i].first < bEnd || b == buckets - 1)) {
            val v = points[i].second
            if (!v.isNaN()) {
                sum += v; tSum += points[i].first.toDouble(); n++
            }
            i++
        }
        if (n > 0) out.add((tSum / n).toLong() to (sum / n).toFloat())
    }
    return out
}
