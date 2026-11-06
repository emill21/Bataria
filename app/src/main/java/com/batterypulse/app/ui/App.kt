package com.batterypulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.batterypulse.app.BuildConfig
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.Changelog
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Now("Now", "Battery", Icons.Outlined.BatteryChargingFull, Icons.Rounded.BatteryChargingFull),
    Health("Health", "Health", Icons.Outlined.HealthAndSafety, Icons.Rounded.HealthAndSafety),
    History("History", "History", Icons.Outlined.Timeline, Icons.Rounded.Timeline),
    Apps("Apps", "App usage", Icons.Outlined.Apps, Icons.Rounded.Apps),
    Settings("Settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatariaApp(onReplayTour: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.Now) }
    val haptics = LocalHapticFeedback.current

    val snapshot by BatteryMonitor.snapshot.collectAsStateWithLifecycle()
    val recent by BatteryMonitor.recent.collectAsStateWithLifecycle()
    val health by BatteryMonitor.health.collectAsStateWithLifecycle()
    val time by BatteryMonitor.timeEstimate.collectAsStateWithLifecycle()
    val live by BatteryMonitor.liveCharge.collectAsStateWithLifecycle()
    val history by BatteryMonitor.history.collectAsStateWithLifecycle()
    val sessions by BatteryMonitor.sessions.collectAsStateWithLifecycle()
    val monitoring by BatteryMonitor.monitoring.collectAsStateWithLifecycle()

    // Live sampling while the app is visible.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                withContext(Dispatchers.Default) { BatteryMonitor.sample() }
                delay(2_000)
            }
        }
    }

    val appearance by BatteryMonitor.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTester by rememberSaveable { mutableStateOf(false) }
    val releases = remember { Changelog.load(context) }
    var showWhatsNew by remember {
        mutableStateOf(BatteryMonitor.prefs.lastSeenVersion in 1 until BuildConfig.VERSION_CODE)
    }
    if (showWhatsNew && releases.isNotEmpty()) {
        ChangelogSheet(releases, latestOnly = true) {
            BatteryMonitor.prefs.lastSeenVersion = BuildConfig.VERSION_CODE
            showWhatsNew = false
        }
    }
    Box(Modifier.fillMaxSize()) {
    AppScaffold(tab = tab, background = appearance.background, onTab = { t ->
        if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tab = t
    }) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Now -> NowScreen(
                    s = snapshot, recent = recent, health = health, time = time, live = live,
                    history = history, monitoring = monitoring, padding = padding,
                    onOpenHealth = { tab = Tab.Health },
                    onOpenCharger = { showTester = true },
                )
                Tab.Apps -> AppsScreen(history, health.estimatedMah ?: health.designMah, padding)
                Tab.Health -> HealthScreen(snapshot, health, sessions, padding)
                Tab.History -> HistoryScreen(history, padding)
                Tab.Settings -> SettingsScreen(snapshot, monitoring, padding, onReplayTour)
            }
        }
    }
    AnimatedVisibility(
        visible = showTester,
        enter = slideInVertically(tween(380)) { it } + fadeIn(),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(),
    ) {
        BackHandler { showTester = false }
        ChargerTesterScreen(live = snapshot, onClose = { showTester = false })
    }
    }
}

/** Shell shared by the app and the screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    background: String = "none",
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedBackground(background, Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                LargeTopAppBar(
                    title = { Text(tab.title, style = MaterialTheme.typography.headlineLarge) },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            bottomBar = { FloatingNavBar(tab, onTab) },
            content = content,
        )
    }
}

/** Floating pill navigation: the selected tab expands to show its label. */
@Composable
fun FloatingNavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(colors.surface.copy(alpha = 0f), colors.surface.copy(alpha = 0.72f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh.let { if (it.alpha > 0.82f) it.copy(alpha = 0.82f) else it },
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { t ->
                    NavPill(t, selected = t == tab, onClick = { onTab(t) })
                }
            }
        }
    }
}

@Composable
private fun NavPill(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primary else Color.Transparent, tween(250), label = "navBg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, tween(250), label = "navFg")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) t.selectedIcon else t.icon, contentDescription = t.label, tint = fg)
        if (selected) {
            Text(
                t.label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
