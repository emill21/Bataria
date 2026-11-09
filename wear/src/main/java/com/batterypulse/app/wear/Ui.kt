package com.batterypulse.app.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** Bataria's green Material You palette, tuned for an OLED watch: true black behind everything. */
object Palette {
    val primary = Color(0xFF93D69F)
    val primaryContainer = Color(0xFF1F5130)
    val onPrimary = Color(0xFF003919)
    val surface = Color(0xFF1B211C)
    val surfaceHigh = Color(0xFF262B26)
    val onSurface = Color(0xFFE0E4DC)
    val muted = Color(0xFFC1C9BF)
    val tertiary = Color(0xFFA2CEDA)
    val amber = Color(0xFFF3C46B)
    val red = Color(0xFFFFB4AB)
    val track = Color(0x33AEF2BA)
}

@Composable
fun BatariaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Palette.primary,
            primaryVariant = Palette.primaryContainer,
            secondary = Palette.tertiary,
            secondaryVariant = Palette.primaryContainer,
            background = Color.Black,
            surface = Palette.surface,
            error = Palette.red,
            onPrimary = Palette.onPrimary,
            onSecondary = Color(0xFF003640),
            onBackground = Palette.onSurface,
            onSurface = Palette.onSurface,
            onSurfaceVariant = Palette.muted,
            onError = Color(0xFF690005),
        ),
        content = content,
    )
}

fun levelColor(level: Int, charging: Boolean): Color = when {
    charging -> Palette.primary
    level <= 15 -> Palette.red
    level <= 30 -> Palette.amber
    else -> Palette.primary
}

/** The level ring with a liquid fill inside, like the app icon. */
@Composable
fun LevelOrb(level: Int, charging: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = levelColor(level, charging)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                accent, -90f, 360f * level.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Liquid inside the ring.
            val r = size.minDimension / 2 - stroke * 2.2f
            val c = Offset(size.width / 2, size.height / 2)
            val top = c.y + r - 2 * r * level.coerceIn(0, 100) / 100f
            val wave = Path().apply {
                moveTo(c.x - r, top)
                cubicTo(c.x - r / 2, top - r * 0.12f, c.x, top + r * 0.12f, c.x + r / 2, top)
                cubicTo(c.x + r * 0.7f, top - r * 0.06f, c.x + r * 0.9f, top - r * 0.06f, c.x + r, top)
                lineTo(c.x + r, c.y + r)
                lineTo(c.x - r, c.y + r)
                close()
            }
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
            drawPath(circle, Color.White.copy(alpha = 0.05f))
            val clipped = Path().apply { op(wave, circle, androidx.compose.ui.graphics.PathOperation.Intersect) }
            drawPath(clipped, Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)), top, c.y + r))
        }
        content()
    }
}

@Composable
fun StatPill(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, tint: Color = Palette.primary) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.onSurface, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Palette.muted, maxLines = 1)
    }
}

@Composable
fun PillRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A label/value line inside a rounded card. */
@Composable
fun InfoCard(title: String, lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 12.sp, color = Palette.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
        }
        lines.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, fontSize = 12.sp, color = Palette.muted, modifier = Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(v, fontSize = 12.sp, color = Palette.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 2)
            }
        }
    }
}

@Composable
fun PageTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        textAlign = TextAlign.Center,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Palette.onSurface,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Palette.muted) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        color = color,
    )
}

/** Small dots at the bottom showing which page you're on. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Palette.onSurface else Palette.muted.copy(alpha = 0.4f))
            )
        }
    }
}

/** Level over time, with charging stretches shaded. */
@Composable
fun LevelChart(points: List<Point>, fromMs: Long, toMs: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val span = (toMs - fromMs).coerceAtLeast(1).toFloat()
        fun x(t: Long) = ((t - fromMs) / span).coerceIn(0f, 1f) * w
        fun y(l: Int) = h - l / 100f * h
        // Grid at 0 / 50 / 100 %.
        listOf(0, 50, 100).forEach { l ->
            drawLine(Palette.muted.copy(alpha = 0.15f), Offset(0f, y(l)), Offset(w, y(l)), 1.dp.toPx())
        }
        val pts = points.filter { it.t in fromMs..toMs }
        if (pts.size < 2) return@Canvas
        for (i in 1 until pts.size) {
            if (pts[i - 1].charging) {
                drawRect(Palette.primary.copy(alpha = 0.12f), Offset(x(pts[i - 1].t), 0f), Size(x(pts[i].t) - x(pts[i - 1].t), h))
            }
        }
        val line = Path()
        val fill = Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) { line.moveTo(x(p.t), y(p.level)); fill.moveTo(x(p.t), h); fill.lineTo(x(p.t), y(p.level)) }
            else { line.lineTo(x(p.t), y(p.level)); fill.lineTo(x(p.t), y(p.level)) }
        }
        fill.lineTo(x(pts.last().t), h)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Palette.primary.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, Palette.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
