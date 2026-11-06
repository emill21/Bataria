package com.batterypulse.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.monitor.MonitorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: BatterySnapshot?, padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = BatteryMonitor.prefs

    var monitoring by remember { mutableStateOf(prefs.monitoringEnabled) }
    var unit by remember { mutableStateOf(prefs.currentUnit) }
    var sign by remember { mutableStateOf(prefs.currentSign) }
    var designOverride by remember { mutableStateOf(prefs.designCapacityOverride) }
    var showDesignDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun setMonitoring(on: Boolean) {
        monitoring = on
        prefs.monitoringEnabled = on
        if (on) MonitorService.start(context) else MonitorService.stop(context)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Monitoring works either way; without the permission the notification is just hidden.
        setMonitoring(true)
    }

    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Monitoring") {
                ListItem(
                    colors = itemColors,
                    leadingContent = { Icon(Icons.Rounded.Notifications, null) },
                    headlineContent = { Text("Background monitoring") },
                    supportingContent = {
                        Text("Records charges and history while the app is closed. Needed for accurate health.")
                    },
                    trailingContent = {
                        Switch(checked = monitoring, onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                setMonitoring(on)
                            }
                        })
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
                    leadingContent = { Icon(Icons.Rounded.BatterySaver, null) },
                    headlineContent = { Text("Battery optimisation") },
                    supportingContent = {
                        Text("If your phone stops monitoring in the background, set Battery Pulse to “Unrestricted”.")
                    },
                )
            }
        }

        item {
            SectionCard(title = "Capacity") {
                val design = BatteryMonitor.design()
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { showDesignDialog = true },
                    leadingContent = { Icon(Icons.Rounded.Edit, null) },
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
                        "phones; override it if the numbers look off by 1000× or have the wrong sign.",
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
                    s?.let { "Interpreted as ${it.currentUnitUsed} → ${formatCurrent(it.currentMa)}" },
                )
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { prefs.resetCalibration() },
                    leadingContent = { Icon(Icons.Rounded.Refresh, null) },
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
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Clear history and charge sessions") },
                )
            }
        }

        item {
            Text(
                "Battery Pulse reads Android's BatteryManager and fuel-gauge values directly. Android " +
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
            text = { Text("This deletes recorded history and all measured charge sessions. Health will need to be re-measured.") },
            confirmButton = {
                TextButton(onClick = { BatteryMonitor.clearData(); showClearDialog = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
        )
    }
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
