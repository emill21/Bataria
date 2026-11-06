package com.batterypulse.app.ui

import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.batterypulse.app.data.BatteryMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    Now("Now", "Battery", Icons.Rounded.BatteryChargingFull),
    Health("Health", "Battery health", Icons.Rounded.HealthAndSafety),
    History("History", "History", Icons.Rounded.Timeline),
    Settings("Settings", "Settings", Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryPulseApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Now) }

    val snapshot by BatteryMonitor.snapshot.collectAsStateWithLifecycle()
    val health by BatteryMonitor.health.collectAsStateWithLifecycle()
    val time by BatteryMonitor.timeEstimate.collectAsStateWithLifecycle()
    val history by BatteryMonitor.history.collectAsStateWithLifecycle()
    val sessions by BatteryMonitor.sessions.collectAsStateWithLifecycle()

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
                title = { Text(tab.title) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.Now -> NowScreen(snapshot, health, time, padding) { tab = Tab.Health }
            Tab.Health -> HealthScreen(snapshot, health, sessions, padding)
            Tab.History -> HistoryScreen(history, padding)
            Tab.Settings -> SettingsScreen(snapshot, padding)
        }
    }
}
