package com.batterypulse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

val CardShape = RoundedCornerShape(28.dp)
val HeroShape = RoundedCornerShape(36.dp)

/** Adds page margins on top of the Scaffold's insets. */
@Composable
fun PaddingValues.withPage(horizontal: Dp = 16.dp, vertical: Dp = 8.dp): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir) + horizontal,
        end = calculateEndPadding(dir) + horizontal,
        top = calculateTopPadding() + vertical,
        bottom = calculateBottomPadding() + 24.dp,
    )
}

/**
 * Runs an action once notification permission is settled (granted or denied) on Android 13+.
 * Features still work without it; notifications just stay hidden.
 */
@Composable
fun rememberWithNotificationPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending.value?.invoke()
        pending.value = null
    }
    return remember(context, launcher) {
        { action: () -> Unit ->
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pending.value = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                action()
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
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
fun InfoRow(label: String, value: String, sub: String? = null, icon: ImageVector? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun IconBadge(icon: ImageVector, accent: Color, size: Dp = 40.dp) {
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(size * 0.24f))
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
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Stat tile with an inline sparkline of the last few minutes. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    spark: List<Float> = emptyList(),
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Sparkline(spark, accent, Modifier.fillMaxWidth().height(32.dp))
        }
    }
}

@Composable
fun BigNumber(value: String, unit: String, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = color)
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
    }
}
