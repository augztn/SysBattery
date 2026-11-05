package com.nlab.sysbattery.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nlab.sysbattery.data.RootAccessReader
import com.nlab.sysbattery.data.RootBatteryStatsParser
import com.nlab.sysbattery.data.safeBoolean
import com.nlab.sysbattery.data.safeFloat
import com.nlab.sysbattery.data.safeInt
import com.nlab.sysbattery.data.safeString
import com.nlab.sysbattery.service.BatteryMonitorService
import com.nlab.sysbattery.ui.components.CardSectionHeader
import com.nlab.sysbattery.ui.components.GlassCard
import com.nlab.sysbattery.ui.components.SegmentedControlRow
import com.nlab.sysbattery.ui.components.SysBatteryIcons
import com.nlab.sysbattery.ui.theme.JetBrainsMonoFamily

@Composable
fun SettingsTab(
    prefs: SharedPreferences,
    running: Boolean,
    onMonitorEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    drainDisplayMode: String,
    onDrainDisplayModeChange: (String) -> Unit,
    onPreferencesChanged: () -> Unit,
    preferencesRevision: Int,
    usageAccessGranted: Boolean,
    onUsageAccess: () -> Unit,
    batteryStatsPermissionGranted: Boolean,
    shizukuStatus: String,
    shizukuAuthorized: Boolean,
    shizukuFindings: List<RootBatteryStatsParser.Finding>,
    shizukuLoading: Boolean,
    onRequestShizuku: () -> Unit,
    onRefreshAdvanced: () -> Unit,
    rootReport: RootAccessReader.RootReport?,
    rootLoading: Boolean,
    onRefreshRoot: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    showDiagnostics: Boolean,
    onDismissDiagnostics: () -> Unit,
    diagnosticText: String,
    sampleSource: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BatteryMonitorSettingsCard(
            prefs = prefs,
            running = running,
            onMonitorEnabledChange = onMonitorEnabledChange,
            onThemeModeChange = onThemeModeChange,
            drainDisplayMode = drainDisplayMode,
            onDrainDisplayModeChange = onDrainDisplayModeChange,
            onPreferencesChanged = onPreferencesChanged,
            preferencesRevision = preferencesRevision
        )

        GlassCard {
            CardSectionHeader(
                icon = SysBatteryIcons.Settings,
                title = "Access Overview",
                subtitle = "Permissions & privileged telemetry providers"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            AccessStatusRow("App Usage Access", if (usageAccessGranted) "Enabled" else "Optional", usageAccessGranted)
            AccessStatusRow("Shizuku Privilege", if (shizukuAuthorized) "Authorized" else "Not connected", shizukuAuthorized)
            AccessStatusRow("Root Diagnostics", if (rootReport?.rooted == true) "Available" else "Optional", rootReport?.rooted == true)
            if (!usageAccessGranted) {
                FilledTonalButton(
                    onClick = onUsageAccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(SysBatteryIcons.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Grant App Usage Access", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                text = "Live hardware telemetry works out-of-the-box without optional elevated permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassCard {
            CardSectionHeader(
                icon = SysBatteryIcons.Info,
                title = "OEM Background Guidance",
                subtitle = "Keep background monitoring active across sleep cycles"
            )
            Text(
                text = "Aggressive OEM battery management (MIUI/HyperOS, ColorOS, OxygenOS, One UI) may pause background monitors. Open Android's Battery optimization settings and exempt SysBattery if uninterrupted logging is important on your device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsExpandableCard(
            title = "ADB Diagnostic Tools",
            subtitle = "Commands for debugging and access setup via USB",
            icon = { Icon(SysBatteryIcons.Terminal, contentDescription = null, modifier = Modifier.size(20.dp)) },
            initiallyExpanded = false
        ) {
            Text(
                text = "Optional commands for advanced telemetry. Connect via ADB with USB debugging enabled.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (batteryStatsPermissionGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (batteryStatsPermissionGranted) "Granted" else "Not granted",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (batteryStatsPermissionGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("BATTERY_STATS Permission", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            AdbCommandItem(
                title = "Check Usage Access",
                description = "Read-only check for GET_USAGE_STATS permission.",
                command = "adb shell appops get com.nlab.sysbattery GET_USAGE_STATS"
            )
            AdbCommandItem(
                title = "Grant BATTERY_STATS",
                description = "May be restricted on production ROMs.",
                command = "adb shell pm grant com.nlab.sysbattery android.permission.BATTERY_STATS"
            )
            AdbCommandItem(
                title = "Allow background in Doze",
                description = "Add SysBattery to device power allowlist.",
                command = "adb shell cmd deviceidle whitelist +com.nlab.sysbattery"
            )
            AdbCommandItem(
                title = "Read Android battery dumpsys",
                description = "Inspect system kernel and fuel-gauge dump.",
                command = "adb shell dumpsys battery"
            )
        }

        SettingsExpandableCard(
            title = "Shizuku Access",
            subtitle = shizukuStatus,
            icon = { Icon(SysBatteryIcons.Security, contentDescription = null, modifier = Modifier.size(20.dp)) },
            initiallyExpanded = false
        ) {
            Text(
                text = "Shizuku provides elevated shell access without root to query privileged BatteryStats.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onRequestShizuku,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    when {
                        shizukuAuthorized -> "Shizuku Authorized"
                        shizukuStatus.contains("not installed", ignoreCase = true) -> "Install Shizuku"
                        else -> "Enable Shizuku Access"
                    }
                )
            }
            if (shizukuAuthorized) {
                OutlinedButton(
                    onClick = onRefreshAdvanced,
                    enabled = !shizukuLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (shizukuLoading) "Querying BatteryStats…" else "Read Privileged BatteryStats")
                }
                shizukuFindings.take(15).forEach { finding ->
                    Text(
                        text = "${finding.label}: ${finding.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsExpandableCard(
            title = "Root Diagnostics",
            subtitle = if (rootReport?.rooted == true) "Root available" else "Optional · Rooted devices only",
            icon = { Icon(SysBatteryIcons.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(20.dp)) },
            initiallyExpanded = false
        ) {
            Text(
                text = "For rooted devices. SysBattery queries read-only battery statistics via su.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = onRefreshRoot,
                enabled = !rootLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (rootLoading) "Checking root…" else "Check Root & Read System Stats")
            }
            rootReport?.let { report ->
                Text(
                    text = if (report.rooted) "Root granted · ${report.identity}" else "Root unavailable · ${report.identity}",
                    color = if (report.rooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                report.findings.forEach { finding ->
                    Text("${finding.label}: ${finding.value}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = "Source: ${sampleSource ?: "waiting for first sample"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsExpandableCard(
            title = "Diagnostics & Logs",
            subtitle = "View and export local app diagnostics",
            icon = { Icon(SysBatteryIcons.Description, contentDescription = null, modifier = Modifier.size(20.dp)) },
            initiallyExpanded = false
        ) {
            Text(
                text = "Local diagnostic logs for troubleshooting. Stored on-device only.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View Logs")
                }
                OutlinedButton(
                    onClick = onExportDiagnostics,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export…")
                }
            }
            OutlinedButton(
                onClick = onClearDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Logs")
            }
        }

        if (showDiagnostics) {
            AlertDialog(
                onDismissRequest = onDismissDiagnostics,
                title = { Text("App Diagnostics") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = diagnosticText.ifBlank { "No logs recorded yet." },
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onExportDiagnostics) { Text("Export") } },
                dismissButton = { TextButton(onClick = onDismissDiagnostics) { Text("Close") } }
            )
        }
    }
}

@Composable
fun BatteryMonitorOptionsList(
    prefs: SharedPreferences,
    preferencesRevision: Int,
    onPreferencesChanged: () -> Unit
) {
    val context = LocalContext.current

    var resetAtThreshold by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("reset_at_threshold", true))
    }
    var threshold by remember(preferencesRevision) {
        mutableFloatStateOf(prefs.safeFloat("reset_threshold_percent", 100f).coerceIn(50f, 100f))
    }
    var resetOnPlug by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("reset_on_plug", true))
    }
    var resetOnReboot by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("reset_on_reboot", true))
    }
    var idleWarning by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("idle_warning", true))
    }
    var warning by remember(preferencesRevision) {
        mutableFloatStateOf(prefs.safeFloat("idle_warning_percent", 5f).coerceIn(1f, 20f))
    }
    var temperatureUnit by remember(preferencesRevision) {
        mutableStateOf(prefs.safeString("temperature_unit", "Celsius (°C)"))
    }
    var showBatteryIcon by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("show_battery_icon", true))
    }
    var showChargingPower by remember(preferencesRevision) {
        mutableStateOf(prefs.safeBoolean("show_charging_power", true))
    }

    var showThresholdDialog by remember { mutableStateOf(false) }
    var showIdleWarningDialog by remember { mutableStateOf(false) }
    var showTempUnitDialog by remember { mutableStateOf(false) }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        onPreferencesChanged()
        BatteryMonitorService.refresh(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OptionToggleRow(
            title = "Reset the stats when the battery reaches at or above a certain percentage",
            checked = resetAtThreshold,
            onCheckedChange = {
                resetAtThreshold = it
                prefs.edit()
                    .putBoolean("reset_at_threshold", it)
                    .putBoolean("threshold_reset_triggered", false)
                    .apply()
                onPreferencesChanged()
                BatteryMonitorService.refresh(context)
            }
        )

        OptionValueRow(
            title = "Battery percentage for stats reset",
            value = "${threshold.toInt()}%",
            enabled = true,
            onClick = { showThresholdDialog = true }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        OptionToggleRow(
            title = "Reset the stats when the device is plugged in",
            checked = resetOnPlug,
            onCheckedChange = {
                resetOnPlug = it
                saveBoolean("reset_on_plug", it)
            }
        )

        OptionToggleRow(
            title = "Reset the stats when the device reboots or shuts down",
            checked = resetOnReboot,
            onCheckedChange = {
                resetOnReboot = it
                saveBoolean("reset_on_reboot", it)
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        OptionToggleRow(
            title = "Notify me if idle drain is higher than a certain percentage",
            checked = idleWarning,
            onCheckedChange = {
                idleWarning = it
                saveBoolean("idle_warning", it)
            }
        )

        OptionValueRow(
            title = "Idle drain percentage warning",
            value = "${warning.toInt()}%",
            enabled = true,
            onClick = { showIdleWarningDialog = true }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        OptionValueRow(
            title = "Battery temperature unit",
            value = temperatureUnit,
            enabled = true,
            onClick = { showTempUnitDialog = true }
        )

        OptionToggleRow(
            title = "Show battery % as a notification icon",
            checked = showBatteryIcon,
            onCheckedChange = {
                showBatteryIcon = it
                saveBoolean("show_battery_icon", it)
            }
        )

        OptionToggleRow(
            title = "Show charging amperage and wattage",
            description = "Amperage and voltage depend on the charger and cable that you're using. These charging values can also be affected by your usage, device heat and/or battery temperature. This feature simply reads the values reported by the kernel.",
            checked = showChargingPower,
            onCheckedChange = {
                showChargingPower = it
                saveBoolean("show_charging_power", it)
            }
        )
    }

    if (showThresholdDialog) {
        var tempThreshold by remember(threshold) { mutableFloatStateOf(threshold) }
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("Battery percentage for stats reset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${tempThreshold.toInt()}%",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = tempThreshold,
                        onValueChange = { tempThreshold = it },
                        valueRange = 50f..100f,
                        steps = 49
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(80, 90, 95, 100).forEach { preset ->
                            FilterChip(
                                selected = tempThreshold.toInt() == preset,
                                onClick = { tempThreshold = preset.toFloat() },
                                label = { Text("$preset%") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        threshold = tempThreshold
                        prefs.edit()
                            .putInt("reset_threshold_percent", tempThreshold.toInt())
                            .putBoolean("threshold_reset_triggered", false)
                            .apply()
                        showThresholdDialog = false
                        onPreferencesChanged()
                        BatteryMonitorService.refresh(context)
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showIdleWarningDialog) {
        var tempWarning by remember(warning) { mutableFloatStateOf(warning) }
        AlertDialog(
            onDismissRequest = { showIdleWarningDialog = false },
            title = { Text("Idle drain percentage warning") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${tempWarning.toInt()}%",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = tempWarning,
                        onValueChange = { tempWarning = it },
                        valueRange = 1f..20f,
                        steps = 18
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 3, 5, 10).forEach { preset ->
                            FilterChip(
                                selected = tempWarning.toInt() == preset,
                                onClick = { tempWarning = preset.toFloat() },
                                label = { Text("$preset%") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        warning = tempWarning
                        prefs.edit().putInt("idle_warning_percent", tempWarning.toInt()).apply()
                        showIdleWarningDialog = false
                        onPreferencesChanged()
                        BatteryMonitorService.refresh(context)
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showIdleWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTempUnitDialog) {
        val units = listOf("Celsius (°C)", "Fahrenheit (°F)")
        AlertDialog(
            onDismissRequest = { showTempUnitDialog = false },
            title = { Text("Battery temperature unit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    units.forEach { unit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    temperatureUnit = unit
                                    prefs.edit().putString("temperature_unit", unit).apply()
                                    showTempUnitDialog = false
                                    onPreferencesChanged()
                                    BatteryMonitorService.refresh(context)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = temperatureUnit == unit,
                                onClick = {
                                    temperatureUnit = unit
                                    prefs.edit().putString("temperature_unit", unit).apply()
                                    showTempUnitDialog = false
                                    onPreferencesChanged()
                                    BatteryMonitorService.refresh(context)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = unit, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTempUnitDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun OptionToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = if (description != null) Alignment.Top else Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatteryMonitorSettingsCard(
    prefs: SharedPreferences,
    running: Boolean,
    onMonitorEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    drainDisplayMode: String,
    onDrainDisplayModeChange: (String) -> Unit,
    onPreferencesChanged: () -> Unit,
    preferencesRevision: Int
) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(running) }
    LaunchedEffect(running) { serviceEnabled = running }

    var designCapacityInput by remember(preferencesRevision) {
        mutableStateOf(prefs.safeInt("design_capacity_mah", 0).takeIf { it > 0 }?.toString() ?: "")
    }
    var theme by remember(preferencesRevision) {
        mutableStateOf(prefs.safeString("theme_mode", "System default"))
    }
    var showResetConfirmation by remember { mutableStateOf(false) }

    GlassCard {
        CardSectionHeader(
            icon = SysBatteryIcons.NotificationsActive,
            title = "Battery Monitor Options",
            subtitle = "Session resets, idle drain alerts, and notification options"
        )

        OptionToggleRow(
            title = "Battery monitor service",
            description = "Keep collecting telemetry while the app is in the background.",
            checked = serviceEnabled,
            onCheckedChange = {
                serviceEnabled = it
                prefs.edit().putBoolean("monitor_enabled", it).apply()
                BatteryMonitorService.refresh(context)
                onMonitorEnabledChange(it)
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        BatteryMonitorOptionsList(
            prefs = prefs,
            preferencesRevision = preferencesRevision,
            onPreferencesChanged = onPreferencesChanged
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        Text("Battery Design Capacity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Enter the manufacturer rated capacity in mAh to calculate battery health estimate.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = designCapacityInput,
            onValueChange = { if (it.length <= 5 && it.all { ch -> ch.isDigit() }) designCapacityInput = it },
            label = { Text("Design Capacity (mAh)") },
            placeholder = { Text("e.g. 5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    prefs.edit().remove("design_capacity_mah").apply()
                    designCapacityInput = ""
                    onPreferencesChanged()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Clear") }

            Button(
                onClick = {
                    val value = designCapacityInput.toIntOrNull()
                    if (value != null && value in 500..20000) {
                        prefs.edit().putInt("design_capacity_mah", value).apply()
                        onPreferencesChanged()
                        Toast.makeText(context, "Design capacity saved", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = designCapacityInput.toIntOrNull()?.let { it in 500..20000 } == true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        Text("Dashboard Drain Display", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SegmentedControlRow(
            options = listOf(
                BatteryMonitorService.METHOD_BOTH to "Both",
                BatteryMonitorService.METHOD_TIME_DROP to "Time (%)",
                BatteryMonitorService.METHOD_RATE_ONLY to "%/hr"
            ),
            selectedValue = drainDisplayMode,
            onSelect = { modeValue ->
                prefs.edit().putString(BatteryMonitorService.PREF_DRAIN_DISPLAY_MODE, modeValue).apply()
                onDrainDisplayModeChange(modeValue)
                onPreferencesChanged()
                BatteryMonitorService.refresh(context)
            },
            testTagPrefix = "settings_drain_mode"
        )

        Text("Theme Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SegmentedControlRow(
            options = listOf(
                "System default" to "System",
                "Light" to "Light",
                "Dark" to "Dark"
            ),
            selectedValue = theme,
            onSelect = { modeValue ->
                theme = modeValue
                prefs.edit().putString("theme_mode", modeValue).apply()
                onThemeModeChange(modeValue)
                onPreferencesChanged()
            },
            testTagPrefix = "settings_theme_mode"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        OutlinedButton(
            onClick = { showResetConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(SysBatteryIcons.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset Session Stats", style = MaterialTheme.typography.labelLarge)
        }

        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = { Text("Reset Session Stats?") },
                text = { Text("This will clear SysBattery's local session telemetry. Android system history is preserved.") },
                confirmButton = {
                    TextButton(onClick = {
                        BatteryMonitorService.resetStoredCounters(context)
                        showResetConfirmation = false
                        onPreferencesChanged()
                    }) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun AccessStatusRow(label: String, status: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (enabled) SysBatteryIcons.CheckCircle else SysBatteryIcons.Info,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsExpandableCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                icon()
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = if (expanded) SysBatteryIcons.ExpandLess else SysBatteryIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

@Composable
private fun AdbCommandItem(
    title: String,
    description: String,
    command: String
) {
    val context = LocalContext.current
    var copied by rememberSaveable(command) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(title, command))
                        copied = true
                        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(SysBatteryIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Text(
                    text = command,
                    fontFamily = JetBrainsMonoFamily,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
