package com.batterypulse.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.batterypulse.app.data.BatteryMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Now("Now", "Battery", Icons.Outlined.BatteryChargingFull, Icons.Rounded.BatteryChargingFull),
    Health("Health", "Health", Icons.Outlined.HealthAndSafety, Icons.Rounded.HealthAndSafety),
    History("History", "History", Icons.Outlined.Timeline, Icons.Rounded.Timeline),
    Settings("Settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryPulseApp() {
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(tab.title, style = MaterialTheme.typography.headlineLarge) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tab = t
                        },
                        icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
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
                )
                Tab.Health -> HealthScreen(snapshot, health, sessions, padding)
                Tab.History -> HistoryScreen(history, padding)
                Tab.Settings -> SettingsScreen(snapshot, monitoring, padding)
            }
        }
    }
}
