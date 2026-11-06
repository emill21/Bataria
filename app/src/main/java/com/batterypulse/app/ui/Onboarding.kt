package com.batterypulse.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.Appearance
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatteryOptimization
import com.batterypulse.app.data.DeviceBatteries
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGES = 7

/** First-launch tour: what the app is, how health works, then guided setup. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    appearance: Appearance,
    onFinish: () -> Unit,
    initialPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = initialPage) { PAGES }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        AnimatedBackground(appearance.background, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Step ${pager.currentPage + 1} of $PAGES",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (pager.currentPage < PAGES - 1) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }) { Text("Skip") }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> FeaturesPage()
                        2 -> HealthPage()
                        3 -> LookPage(appearance)
                        4 -> MonitoringPage()
                        5 -> FinalTouchesPage()
                        else -> DonePage()
                    }
                }
            }
            // Footer: dots + navigation
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PAGES) { i ->
                        val selected = i == pager.currentPage
                        val w by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                        val c by animateColorAsState(
                            if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f), label = "dotColor",
                        )
                        Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(c))
                    }
                }
                if (pager.currentPage > 0 && pager.currentPage < PAGES - 1) {
                    OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text("Back")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val last = pager.currentPage == PAGES - 1
                Button(onClick = {
                    if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) {
                    Text(
                        when {
                            last -> "Start"
                            pager.currentPage == 0 -> "Let's go"
                            else -> "Next"
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun PageTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 8.dp), content = content) }
}

@Composable
private fun WelcomePage() {
    val colors = MaterialTheme.colorScheme
    val demo = rememberInfiniteTransition(label = "demo")
    val level by demo.animateFloat(
        initialValue = 18f,
        targetValue = 92f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "demoLevel",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        LiquidGauge(
            level = level.roundToInt(),
            charging = true,
            liquid = colors.primary,
            onLiquid = colors.onPrimary,
            textColor = colors.onSurface,
            modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f),
        ) { textColor ->
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = textColor, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Welcome to", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        Text(
            "Bataria",
            fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
            color = colors.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "The honest story of your battery: live stats, real measured health and beautiful history, " +
                "without a permanent notification.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Takes about a minute to set up.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
    }
}

@Composable
private fun FeaturesPage() {
    PageTitle("What you get", "Everything Android knows about your battery, made readable.")
    val features = listOf(
        Triple(Icons.Rounded.Speed, "Live stats", "Current, power, voltage and temperature, with mini charts."),
        Triple(Icons.Rounded.HealthAndSafety, "True battery health", "Measured from your real charges, not guessed."),
        Triple(Icons.Rounded.BatteryChargingFull, "Charging insights", "Watts, mAh added, time to full and a rating for every charge."),
        Triple(Icons.Rounded.Timeline, "History", "A week of interactive charts and screen-on vs screen-off drain."),
        Triple(Icons.Rounded.NotificationsActive, "Alerts", "Unplug at 80%, low battery and overheating warnings."),
        Triple(Icons.Rounded.Widgets, "Widget", "Your battery at a glance on the home screen."),
    )
    features.forEachIndexed { i, (icon, title, body) ->
        Entrance(i) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HealthPage() {
    val colors = MaterialTheme.colorScheme
    PageTitle(
        "How health is measured",
        "Android doesn't let apps read your battery's real health, so Bataria measures it for you.",
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WavyArcGauge(
            progress = 0.88f, color = colors.primary, track = colors.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(180.dp), stroke = 9.dp, amplitude = 2.4.dp, wavelength = 30.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("88%", style = MaterialTheme.typography.displaySmall)
                Text("example", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    GlassCard {
        Step("1", "Charge as usual", "Each time you charge by 15% or more, the app notes how much charge flows into the battery.")
        Step("2", "Work out full capacity", "If 2,200 mAh went in while the level rose 50%, the battery holds about 4,400 mAh.")
        Step("3", "Compare with new", "That's compared to the capacity it had when new. After three charges, confidence is High.")
    }
}

@Composable
private fun Step(n: String, title: String, body: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LookPage(appearance: Appearance) {
    PageTitle("Make it yours", "Pick a color and a background. You can change these any time in Settings.")
    GlassCard {
        Column(Modifier.padding(horizontal = 16.dp)) {
            FieldLabel("Theme")
            Choice(
                options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                selected = appearance.themeMode,
            ) { BatteryMonitor.setAppearance(appearance.copy(themeMode = it)) }
            FieldLabel("Accent color", top = 18.dp)
        }
        AccentPicker(selected = appearance.accent) { BatteryMonitor.setAppearance(appearance.copy(accent = it)) }
        Column(Modifier.padding(horizontal = 16.dp)) { FieldLabel("Animated background", top = 18.dp) }
        BackgroundPicker(selected = appearance.background) { BatteryMonitor.setAppearance(appearance.copy(background = it)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MonitoringPage() {
    val context = LocalContext.current
    val withPermission = rememberWithNotificationPermission()
    val monitoring by BatteryMonitor.monitoring.collectAsState()
    val live by BatteryMonitor.liveNotification.collectAsState()
    val mode = when {
        !monitoring -> "off"
        live -> "live"
        else -> "quiet"
    }
    PageTitle(
        "Background monitoring",
        "To measure health, Bataria needs to notice your charges even when it's closed. Choose how:",
    )
    ModeCard(
        selected = mode == "quiet",
        icon = Icons.Rounded.NotificationsOff,
        title = "Quiet",
        badge = "Recommended",
        body = "No notification. Checks every 15 minutes. Health stays accurate; alerts may be a few minutes late.",
    ) {
        withPermission {
            BatteryMonitor.setLiveNotification(context, false)
            BatteryMonitor.setMonitoring(context, true)
        }
    }
    Spacer(Modifier.height(12.dp))
    ModeCard(
        selected = mode == "live",
        icon = Icons.Rounded.Speed,
        title = "Live",
        badge = null,
        body = "Checks every few seconds with instant alerts and live stats in a notification. " +
            "Appears in Android's Active apps; you can hide its notification later.",
    ) {
        withPermission {
            BatteryMonitor.setMonitoring(context, true)
            BatteryMonitor.setLiveNotification(context, true)
        }
    }
    Spacer(Modifier.height(12.dp))
    ModeCard(
        selected = mode == "off",
        icon = Icons.Rounded.PowerSettingsNew,
        title = "Off",
        badge = null,
        body = "Only measures while the app is open. Health will take much longer to work out.",
    ) { BatteryMonitor.setMonitoring(context, false) }
    if (monitoring) {
        Spacer(Modifier.height(20.dp))
        UnrestrictedCard()
    }
}

/** One-tap prompt to exempt Bataria from battery optimization, with live status. */
@Composable
fun UnrestrictedCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var unrestricted by remember { mutableStateOf(BatteryOptimization.isUnrestricted(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            unrestricted = BatteryOptimization.isUnrestricted(context)
        }
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unrestricted) colors.primaryContainer else colors.tertiaryContainer,
            contentColor = if (unrestricted) colors.onPrimaryContainer else colors.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (unrestricted) Icons.Rounded.CheckCircle else Icons.Rounded.BatterySaver, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (unrestricted) "Unrestricted battery: on" else "Allow unrestricted battery",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (unrestricted) "Android won't stop Bataria in the background, so no charge is missed."
                else "Some phones stop apps in the background to save power. This keeps Bataria's monitoring running. It uses almost no battery.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!unrestricted) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { BatteryOptimization.request(context) }) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    badge: String?,
    body: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.5f), label = "modeBorder")
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            IconBadge(icon, if (selected) colors.onPrimaryContainer else colors.primary, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimary,
                            modifier = Modifier.clip(CircleShape).background(colors.primary)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = colors.primary)
            }
        }
    }
}

@Composable
private fun FinalTouchesPage() {
    val prefs = BatteryMonitor.prefs
    val withPermission = rememberWithNotificationPermission()
    var chargeOn by remember { mutableStateOf(prefs.chargeAlarmOn) }
    var chargeLevel by remember { mutableFloatStateOf(prefs.chargeAlarmLevel.toFloat()) }
    var lowOn by remember { mutableStateOf(prefs.lowAlarmOn) }
    var showDesign by remember { mutableStateOf(false) }
    val design = remember(showDesign) { BatteryMonitor.design() }

    PageTitle("Final touches", "Optional extras that help your battery last longer.")
    GlassCard {
        SwitchItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "Remind me to unplug",
            sub = "Notify at ${chargeLevel.roundToInt()}%. Stopping at around 80% is kinder to the battery.",
            checked = chargeOn,
            onChange = { on -> withPermission { chargeOn = on; prefs.chargeAlarmOn = on; prefs.chargeAlarmFired = false } },
        )
        if (chargeOn) {
            LevelSlider(chargeLevel, 50f..100f, 9, { chargeLevel = it }) {
                prefs.chargeAlarmLevel = chargeLevel.roundToInt(); prefs.chargeAlarmFired = false
            }
        }
        SwitchItem(
            icon = Icons.Rounded.NotificationsActive,
            title = "Low battery warning",
            sub = "Notify at ${prefs.lowAlarmLevel}%",
            checked = lowOn,
            onChange = { on -> withPermission { lowOn = on; prefs.lowAlarmOn = on; prefs.lowAlarmFired = false } },
        )
    }
    Spacer(Modifier.height(16.dp))
    GlassCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.HealthAndSafety, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your phone: ${DeviceBatteries.phoneName()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Battery size when new", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatMah(design.first)} · ${design.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "If this looks wrong, enter your phone's rated capacity later in Settings › Capacity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DonePage() {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "done")
    val scale by pulse.animateFloat(
        0.92f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "doneScale",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .border(3.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary,
                modifier = Modifier.size((84 * scale).dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Bataria is watching your battery. Health appears after your first few charges, " +
                "so just use your phone as normal.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        GlassCard {
            Tip(Icons.Rounded.Widgets, "Add the widget: long-press your home screen › Widgets › Bataria.")
            Tip(Icons.Rounded.Timeline, "Press and drag on any chart to see exact values.")
            Tip(Icons.Rounded.Palette, "Replay this tour any time from Settings › About.")
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

