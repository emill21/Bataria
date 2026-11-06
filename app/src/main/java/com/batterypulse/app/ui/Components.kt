package com.batterypulse.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(28.dp)

/** Adds page margins on top of the Scaffold's insets. */
@Composable
fun PaddingValues.withPage(horizontal: Dp = 16.dp, vertical: Dp = 8.dp): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir) + horizontal,
        end = calculateEndPadding(dir) + horizontal,
        top = calculateTopPadding() + vertical,
        bottom = calculateBottomPadding() + 16.dp,
    )
}

/** 270° gauge ring, animated. */
@Composable
fun LevelRing(
    level: Int,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(900),
        label = "level",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = color, startAngle = 135f, sweepAngle = 270f * progress, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Minimal time-series line chart. Breaks the line wherever samples are more than
 * [gapMs] apart so missing data isn't drawn as a straight interpolation.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Float>>,
    startT: Long,
    endT: Long,
    minY: Float,
    maxY: Float,
    color: Color,
    modifier: Modifier = Modifier,
    gapMs: Long = 10 * 60_000L,
    baseline: Float? = null,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val gridStroke = 1.dp.toPx()
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = gridStroke)
        }
        val xSpan = (endT - startT).coerceAtLeast(1L).toFloat()
        val ySpan = (maxY - minY).takeIf { it > 0f } ?: 1f
        fun px(t: Long) = (t - startT) / xSpan * size.width
        fun py(v: Float) = size.height - ((v - minY) / ySpan).coerceIn(0f, 1f) * size.height

        if (baseline != null && baseline > minY && baseline < maxY) {
            drawLine(grid, Offset(0f, py(baseline)), Offset(size.width, py(baseline)), strokeWidth = gridStroke * 1.5f)
        }
        if (points.size < 2) return@Canvas

        val segments = mutableListOf<MutableList<Offset>>()
        var prevT = Long.MIN_VALUE
        for ((t, v) in points) {
            if (v.isNaN()) continue
            if (segments.isEmpty() || t - prevT > gapMs) segments.add(mutableListOf())
            segments.last().add(Offset(px(t), py(v)))
            prevT = t
        }
        val fillBrush = Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent))
        val fillTo = baseline?.let { py(it.coerceIn(minY, maxY)) } ?: size.height
        for (seg in segments) {
            if (seg.size < 2) continue
            val line = Path().apply {
                moveTo(seg[0].x, seg[0].y)
                for (k in 1 until seg.size) lineTo(seg[k].x, seg[k].y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(seg.last().x, fillTo)
                lineTo(seg.first().x, fillTo)
                close()
            }
            drawPath(fill, fillBrush)
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
