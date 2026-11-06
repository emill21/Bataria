package com.batterypulse.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatterySnapshot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: BatterySnapshot?, monitoring: Boolean, padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = BatteryMonitor.prefs
    val appearance by BatteryMonitor.appearance.collectAsStateWithLifecycle()
    val withPermission = rememberWithNotificationPermission()

    var unit by remember { mutableStateOf(prefs.currentUnit) }
    var sign by remember { mutableStateOf(prefs.currentSign) }
    var designOverride by remember { mutableStateOf(prefs.designCapacityOverride) }
    var showDesignDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var chargeOn by remember { mutableStateOf(prefs.chargeAlarmOn) }
    var chargeLevel by remember { mutableFloatStateOf(prefs.chargeAlarmLevel.toFloat()) }
    var lowOn by remember { mutableStateOf(prefs.lowAlarmOn) }
    var lowLevel by remember { mutableFloatStateOf(prefs.lowAlarmLevel.toFloat()) }
    var tempOn by remember { mutableStateOf(prefs.tempAlarmOn) }
    var tempLevel by remember { mutableFloatStateOf(prefs.tempAlarmC.toFloat()) }

    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Monitoring") {
                SwitchItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Background monitoring",
                    sub = "Records charges and history while the app is closed. Powers alerts and the widget.",
                    checked = monitoring,
                    onChange = { on ->
                        if (on) withPermission { BatteryMonitor.setMonitoring(context, true) }
                        else BatteryMonitor.setMonitoring(context, false)
                    },
                )
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e: Exception) {
                        }
                    },
                    leadingContent = { IconBadge(Icons.Rounded.BatterySaver, MaterialTheme.colorScheme.primary, 36.dp) },
                    headlineContent = { Text("Battery optimisation") },
                    supportingContent = { Text("If monitoring stops in the background, set Battery Pulse to “Unrestricted”.") },
                )
            }
        }

        item {
            SectionCard(title = "Alerts") {
                if (!monitoring) {
                    Text(
                        "Alerts need background monitoring to work while the app is closed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                SwitchItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Charge limit",
                    sub = "Notify when charged to ${chargeLevel.roundToInt()}% so you can unplug",
                    checked = chargeOn,
                    onChange = { on ->
                        withPermission { chargeOn = on; prefs.chargeAlarmOn = on; prefs.chargeAlarmFired = false }
                    },
                )
                if (chargeOn) {
                    LevelSlider(chargeLevel, 50f..100f, 9, { chargeLevel = it }) {
                        prefs.chargeAlarmLevel = chargeLevel.roundToInt(); prefs.chargeAlarmFired = false
                    }
                }
                SwitchItem(
                    icon = Icons.Rounded.BatteryAlert,
                    title = "Low battery",
                    sub = "Notify at ${lowLevel.roundToInt()}%",
                    checked = lowOn,
                    onChange = { on ->
                        withPermission { lowOn = on; prefs.lowAlarmOn = on; prefs.lowAlarmFired = false }
                    },
                )
                if (lowOn) {
                    LevelSlider(lowLevel, 5f..50f, 8, { lowLevel = it }) {
                        prefs.lowAlarmLevel = lowLevel.roundToInt(); prefs.lowAlarmFired = false
                    }
                }
                SwitchItem(
                    icon = Icons.Rounded.Thermostat,
                    title = "High temperature",
                    sub = "Notify above ${tempLevel.roundToInt()} °C",
                    checked = tempOn,
                    onChange = { on ->
                        withPermission { tempOn = on; prefs.tempAlarmOn = on; prefs.tempAlarmFired = false }
                    },
                )
                if (tempOn) {
                    LevelSlider(tempLevel, 35f..50f, 14, { tempLevel = it }) {
                        prefs.tempAlarmC = tempLevel.roundToInt(); prefs.tempAlarmFired = false
                    }
                }
            }
        }

        item {
            SectionCard(title = "Appearance") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row { IconBadge(Icons.Rounded.DarkMode, MaterialTheme.colorScheme.primary, 36.dp) }
                    Text(
                        "Theme", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    )
                    Choice(
                        options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                        selected = appearance.themeMode,
                    ) { BatteryMonitor.setAppearance(appearance.copy(themeMode = it)) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(
                        icon = Icons.Rounded.Palette,
                        title = "Dynamic color",
                        sub = "Match your wallpaper (Material You)",
                        checked = appearance.dynamicColor,
                        onChange = { BatteryMonitor.setAppearance(appearance.copy(dynamicColor = it)) },
                    )
                }
                SwitchItem(
                    icon = Icons.Rounded.Contrast,
                    title = "Pure black",
                    sub = "True black backgrounds in dark mode, ideal for OLED screens",
                    checked = appearance.pureBlack,
                    onChange = { BatteryMonitor.setAppearance(appearance.copy(pureBlack = it)) },
                )
            }
        }

        item {
            SectionCard(title = "Capacity") {
                val design = BatteryMonitor.design()
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { showDesignDialog = true },
                    leadingContent = { IconBadge(Icons.Rounded.Edit, MaterialTheme.colorScheme.primary, 36.dp) },
                    headlineContent = { Text("Design capacity") },
                    supportingContent = {
                        Text("${formatMah(design.first)} · ${design.second}. Tap to enter your phone's rated capacity.")
                    },
                )
            }
        }

        item {
            SectionCard(title = "Current reading") {
                Text(
                    "Manufacturers report current in different units and signs. Auto-detect handles most " +
                        "phones; override it if numbers look off by 1000× or have the wrong sign.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Unit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
                    Choice(
                        options = listOf("auto" to "Auto", "ua" to "µA", "ma" to "mA"),
                        selected = unit,
                    ) { unit = it; prefs.currentUnit = it }
                    Text(
                        "Sign", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    Choice(
                        options = listOf("auto" to "Auto", "normal" to "Normal", "inverted" to "Inverted"),
                        selected = sign,
                    ) { sign = it; prefs.currentSign = it }
                }
                InfoRow(
                    "Raw reading",
                    s?.rawCurrent?.toString() ?: "Not supported",
                    s?.let { "Read as ${it.currentUnitUsed} → ${formatCurrent(it.currentMa)}" },
                )
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { prefs.resetCalibration() },
                    leadingContent = { IconBadge(Icons.Rounded.Refresh, MaterialTheme.colorScheme.primary, 36.dp) },
                    headlineContent = { Text("Reset auto-detection") },
                    supportingContent = { Text("Re-learn unit and sign from new readings") },
                )
            }
        }

        item {
            SectionCard(title = "Data") {
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { showClearDialog = true },
                    leadingContent = { IconBadge(Icons.Rounded.Delete, MaterialTheme.colorScheme.error, 36.dp) },
                    headlineContent = { Text("Clear history and charges") },
                    supportingContent = { Text("Health will need to be re-measured") },
                )
            }
        }

        item {
            Text(
                "Battery Pulse 2.0 · Reads Android's BatteryManager and fuel-gauge values directly. Android " +
                    "doesn't let regular apps read the battery's internal health figure, so health is " +
                    "measured from your real charges against the design capacity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }

    if (showDesignDialog) {
        var text by remember { mutableStateOf(if (designOverride > 0) designOverride.toString() else "") }
        AlertDialog(
            onDismissRequest = { showDesignDialog = false },
            title = { Text("Design capacity") },
            text = {
                Column {
                    Text("Enter the rated capacity from your phone's spec sheet. Leave empty to auto-detect.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                        label = { Text("mAh") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = text.toIntOrNull()?.takeIf { it in 500..30_000 } ?: 0
                    designOverride = v
                    prefs.designCapacityOverride = v
                    BatteryMonitor.invalidateDesign()
                    showDesignDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDesignDialog = false }) { Text("Cancel") } },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all data?") },
            text = { Text("This deletes recorded history and all measured charges. Health will need to be re-measured.") },
            confirmButton = {
                TextButton(onClick = { BatteryMonitor.clearData(); showClearDialog = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { IconBadge(icon, MaterialTheme.colorScheme.primary, 36.dp) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun LevelSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onChange,
        onValueChangeFinished = onDone,
        valueRange = range,
        steps = steps,
        modifier = Modifier.padding(start = 72.dp, end = 20.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = key == selected,
                onClick = { onSelect(key) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
}
