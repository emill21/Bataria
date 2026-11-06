package com.batterypulse.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

private fun DrawScope.liquidRadius() = size.minDimension / 2f - 16.dp.toPx()

/** Waterline y for a given fill inside the liquid circle. */
private fun DrawScope.waterLine(fill: Float): Float {
    val r = liquidRadius()
    return center.y + r - fill * 2f * r
}

private fun DrawScope.wavePath(fill: Float, phase: Float, ampMul: Float, cycles: Float): Path {
    val y0 = waterLine(fill)
    // Calm the surface when nearly empty or full so it doesn't poke out of the circle.
    val edge = (minOf(fill, 1f - fill) * 8f).coerceIn(0.2f, 1f)
    val amp = 7.dp.toPx() * ampMul * edge
    val path = Path()
    path.moveTo(0f, size.height)
    path.lineTo(0f, y0)
    var x = 0f
    val step = 3.dp.toPx()
    while (x <= size.width + step) {
        val y = y0 + sin(x / size.width * cycles * TWO_PI + phase) * amp
        path.lineTo(x, y)
        x += step
    }
    path.lineTo(size.width, size.height)
    path.close()
    return path
}

/**
 * Animated liquid battery: two layered waves fill a circle to [level], with rising bubbles
 * while charging. [content] is drawn twice — once normally and once clipped to the liquid in
 * [onLiquid] — so the number stays readable as the water passes behind it.
 */
@Composable
fun LiquidGauge(
    level: Int,
    charging: Boolean,
    liquid: Color,
    onLiquid: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val fill by animateFloatAsState(
        targetValue = (level / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "fill",
    )
    val transition = rememberInfiniteTransition(label = "liquid")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(if (charging) 1700 else 4200, easing = LinearEasing)),
        label = "phase",
    )
    val bubbleT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "bubbles",
    )
    val track = liquid.copy(alpha = 0.16f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            val ringStroke = 7.dp.toPx()
            val ringR = outer - ringStroke / 2f
            val r = liquidRadius()

            // Soft glow behind everything
            drawCircle(
                Brush.radialGradient(
                    listOf(liquid.copy(alpha = 0.20f), liquid.copy(alpha = 0.0f)),
                    center = center, radius = outer,
                ),
                radius = outer,
            )

            // Level ring: faint track, gradient progress, knob at the end
            drawCircle(track, radius = ringR, style = Stroke(ringStroke))
            if (fill > 0.005f) {
                rotate(-90f, center) {
                    drawArc(
                        color = liquid,
                        startAngle = 0f,
                        sweepAngle = 360f * fill,
                        useCenter = false,
                        topLeft = Offset(center.x - ringR, center.y - ringR),
                        size = Size(ringR * 2, ringR * 2),
                        style = Stroke(ringStroke, cap = StrokeCap.Round),
                    )
                }
                val a = Math.toRadians((-90.0 + 360.0 * fill))
                val knob = Offset(center.x + ringR * cos(a).toFloat(), center.y + ringR * sin(a).toFloat())
                drawCircle(onLiquid, radius = ringStroke * 0.28f, center = knob)
            }

            // Glass bowl
            drawCircle(liquid.copy(alpha = 0.08f), radius = r)
            val circle = Path().apply { addOval(Rect(center, r)) }
            clipPath(circle) {
                val top = waterLine(fill)
                drawPath(wavePath(fill, phase * -1f + 2.2f, 0.75f, 1.7f), liquid.copy(alpha = 0.30f))
                drawPath(
                    wavePath(fill, phase, 1f, 1.15f),
                    Brush.verticalGradient(
                        listOf(lerp(liquid, Color.White, 0.16f), liquid, lerp(liquid, Color.Black, 0.12f)),
                        startY = top,
                        endY = center.y + r,
                    ),
                )
                if (charging && fill > 0.05f) {
                    val bottom = center.y + r
                    for (i in 0 until 9) {
                        val offset = (i * 0.137f + i * i * 0.071f) % 1f
                        val prog = (bubbleT + offset) % 1f
                        val lane = ((i * 37) % 100) / 100f - 0.5f
                        val x = center.x + lane * r * 1.3f + sin(prog * 9f + i) * 4.dp.toPx()
                        val y = bottom - prog * (bottom - top)
                        val br = (2.5f + (i % 3) * 1.8f).dp.toPx()
                        drawCircle(onLiquid.copy(alpha = 0.40f * (1f - prog)), radius = br, center = Offset(x, y))
                        drawCircle(
                            onLiquid.copy(alpha = 0.55f * (1f - prog)), radius = br,
                            center = Offset(x, y), style = Stroke(1.dp.toPx()),
                        )
                    }
                }
                // Glossy highlight on the liquid only
                clipPath(wavePath(fill, phase, 1f, 1.15f)) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.0f)),
                            center = Offset(center.x - r * 0.35f, top + (center.y + r - top) * 0.25f),
                            radius = r * 0.7f,
                        ),
                        radius = r,
                    )
                }
            }
            drawCircle(liquid.copy(alpha = 0.30f), radius = r, style = Stroke(1.5.dp.toPx()))
        }
        content(textColor)
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val r = liquidRadius()
                    val circle = Path().apply { addOval(Rect(center, r)) }
                    val wave = wavePath(fill, phase, 1f, 1.15f)
                    val clip = Path().apply { op(circle, wave, PathOperation.Intersect) }
                    clipPath(clip) { this@drawWithContent.drawContent() }
                },
            contentAlignment = Alignment.Center,
        ) {
            content(onLiquid)
        }
    }
}

/** Material 3 Expressive-style gauge: a wavy progress arc over a flat track. */
@Composable
fun WavyArcGauge(
    progress: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 30.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "arc",
    )
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "wavePhase",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val amp = amplitude.toPx()
            val wl = wavelength.toPx()
            val r = size.minDimension / 2f - sw / 2f - amp
            val start = 135f
            val sweep = 270f
            val progSweep = sweep * p
            val gapDeg = Math.toDegrees(((sw * 1.6f) / r).toDouble()).toFloat()

            val trackStart = if (p > 0.005f) start + progSweep + gapDeg else start
            val trackSweep = (start + sweep) - trackStart
            if (trackSweep > 0f) {
                drawArc(
                    color = track,
                    startAngle = trackStart,
                    sweepAngle = trackSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            if (p > 0.005f) {
                val path = Path()
                val arcLen = r * Math.toRadians(progSweep.toDouble()).toFloat()
                val n = (arcLen / 1.5.dp.toPx()).toInt().coerceAtLeast(2)
                for (k in 0..n) {
                    val sLen = arcLen * k / n
                    val a = Math.toRadians(start.toDouble()) + sLen / r
                    // Taper the wave in at both ends so the caps sit on the circle.
                    val taper = minOf(1f, sLen / wl, (arcLen - sLen) / wl).coerceAtLeast(0f)
                    val rr = r + amp * taper * sin(sLen / wl * TWO_PI - phase)
                    val x = center.x + rr * cos(a).toFloat()
                    val y = center.y + rr * sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        content()
    }
}

/** Smooth, monotone-in-x cubic path through points. */
fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val mid = (a.x + b.x) / 2f
        path.cubicTo(mid, a.y, mid, b.y, b.x, b.y)
    }
    return path
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val v = values.filter { !it.isNaN() }
        if (v.size < 2) {
            drawLine(
                color.copy(alpha = 0.25f),
                Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            )
            return@Canvas
        }
        var lo = v.min()
        var hi = v.max()
        if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
        val pad = 2.dp.toPx()
        val pts = v.mapIndexed { i, y ->
            Offset(
                i / (v.size - 1f) * size.width,
                pad + (1f - (y - lo) / (hi - lo)) * (size.height - pad * 2),
            )
        }
        val line = smoothPath(pts)
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = pts.last())
        drawCircle(color, radius = 3.5.dp.toPx(), center = pts.last())
    }
}
