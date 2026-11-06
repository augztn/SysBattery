package com.nlab.sysbattery.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nlab.sysbattery.data.AdvancedBatteryStatsReader
import com.nlab.sysbattery.data.RootBatteryStatsParser
import com.nlab.sysbattery.data.BatterySysfsReader
import com.nlab.sysbattery.service.BatteryMonitorService
import com.nlab.sysbattery.ui.components.CardSectionHeader
import com.nlab.sysbattery.ui.components.GlassCard
import com.nlab.sysbattery.ui.components.SysBatteryIcons
import com.nlab.sysbattery.ui.theme.JetBrainsMonoFamily
import com.nlab.sysbattery.ui.theme.LocalLiquidTheme
import com.nlab.sysbattery.ui.theme.liquidBorderBrush
import java.util.Locale

@Composable
fun AppUsageTab(
    usageAccessGranted: Boolean,
    onUsageAccess: () -> Unit,
    onRefreshAdvanced: () -> Unit,
    shizukuLoading: Boolean,
    advancedReport: AdvancedBatteryStatsReader.Report?,
    advancedError: String?,
    batteryStatsFindings: List<RootBatteryStatsParser.Finding>,
    sessionTelemetry: BatteryMonitorService.SessionTelemetry,
    batterySample: BatterySysfsReader.Snapshot?
) {
    val isLiquid = LocalLiquidTheme.current
    val selectedSection = remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard {
            CardSectionHeader(
                icon = SysBatteryIcons.Apps,
                title = "App Usage & Power",
                subtitle = "Foreground activity, background transitions & Android power estimates"
            )

            if (!usageAccessGranted) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = SysBatteryIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Usage Access is required to inspect per-app foreground screen time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = onUsageAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(SysBatteryIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enable App Usage Access", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onRefreshAdvanced,
                enabled = !shizukuLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (shizukuLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading App Usage…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(SysBatteryIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh App Usage", style = MaterialTheme.typography.labelLarge)
                }
            }

            advancedError?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        TabRow(selectedTabIndex = selectedSection.intValue) {
            Tab(selected = selectedSection.intValue == 0, onClick = { selectedSection.intValue = 0 }, text = { Text("App Usage") })
            Tab(selected = selectedSection.intValue == 1, onClick = { selectedSection.intValue = 1 }, text = { Text("Background Activity") })
        }

        advancedReport?.let { report ->
            GlassCard {
                if (selectedSection.intValue == 0) {
                CardSectionHeader(
                    icon = SysBatteryIcons.Smartphone,
                    title = "App Activity & Background Use (24h)",
                    subtitle = if (report.usage.isEmpty()) "No apps recorded yet"
                    else "${report.usage.size} apps with recorded activity or power use"
                )

                if (report.usage.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(if (isLiquid) 16.dp else 14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isLiquid) {
                            BorderStroke(1.dp, liquidBorderBrush(0.7f))
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    ) {
                        Text(
                            text = if (!usageAccessGranted) "Enable Usage Access above to inspect foreground time."
                            else "No foreground app usage recorded in the last 24 hours.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val maxForeground = (report.usage.maxOfOrNull { it.foregroundMillis } ?: 1L)
                        .coerceAtLeast(1L)
                        .toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.usage.take(12).forEach { app ->
                            val minutes = app.foregroundMillis / 60_000L
                            val fraction = (app.foregroundMillis.toFloat() / maxForeground).coerceIn(0f, 1f)
                            val backgroundSummary = buildString {
                                append("Foreground ${formatDuration(app.foregroundMillis)} · Backgrounded ${app.backgroundTransitions}×")
                                app.lastBackgroundAtMillis?.let {
                                    append(" · last ")
                                    append(DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(if (isLiquid) 16.dp else 14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = if (isLiquid) {
                                    BorderStroke(1.dp, liquidBorderBrush(0.75f))
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = app.packageName.substringAfterLast('.').take(1).uppercase(Locale.getDefault()),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Foreground ${formatDuration(app.foregroundMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = app.backgroundBatteryMah?.let { String.format(Locale.US, "%.1f bg mAh", it) }
                                                ?: app.batteryMah?.let { String.format(Locale.US, "%.1f mAh", it) }
                                                ?: "—",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (app.batteryMah != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Backgrounded counts are app transitions, not background CPU time. Some OEM BatteryStats rows expose a separate background mAh estimate; otherwise the value shown is total UID mAh since the last charge. Shared UIDs are divided across their packages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                }

                if (selectedSection.intValue == 1) {
                    CardSectionHeader(
                        icon = SysBatteryIcons.Info,
                        title = "Background Activity (24h)",
                        subtitle = "App background transitions, standby state and system activity"
                    )
                    val backgroundApps = report.usage
                        .filter { it.backgroundTransitions > 0 || it.backgroundBatteryMah != null || it.standbyBucket != null }
                        .sortedWith(
                            compareByDescending<AdvancedBatteryStatsReader.AppUsage> {
                                it.backgroundBatteryMah ?: it.batteryMah ?: -1.0
                            }.thenByDescending { it.backgroundTransitions }
                        )
                    if (backgroundApps.isEmpty()) {
                        Text(
                            text = "No background activity records are available. Enable Usage Access and refresh; Shizuku may provide additional BatteryStats data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        backgroundApps.take(20).forEach { app ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(app.appLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${app.packageName} · Backgrounded ${app.backgroundTransitions}×" +
                                        (app.lastBackgroundAtMillis?.let {
                                            " · last " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                        } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = app.standbyBucket?.let { "Last recorded standby change: ${standbyBucketLabel(it)}" }
                                        ?: "Standby state: unavailable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = app.backgroundBatteryMah?.let { String.format(Locale.US, "Background estimate %.1f mAh", it) }
                                        ?: app.batteryMah?.let { String.format(Locale.US, "Total UID estimate %.1f mAh (background split unavailable)", it) }
                                        ?: "Battery estimate unavailable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "Backgrounded counts are app transitions, not background CPU time. OEM background mAh is shown only when Android exposes it; otherwise total UID mAh is clearly labeled and is not treated as background-only usage.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                val screenOffSignals = batteryStatsFindings.filter {
                    it.label == "Wake lock" || it.label == "History wake-lock event" || it.label == "History screen state"
                }.distinctBy { "${it.label}:${it.value}" }.take(8)
                if (screenOffSignals.isNotEmpty()) {
                    Text(
                        text = "Screen-off activity signals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(if (isLiquid) 16.dp else 14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isLiquid) BorderStroke(1.dp, liquidBorderBrush(0.75f))
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            screenOffSignals.forEach { finding ->
                                Text(
                                    text = "${finding.label}: ${finding.value}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "These are system history signals. A wake-lock name is not always enough to identify the responsible app.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val componentPower = batteryStatsFindings
                    .filter { it.label == "Component estimate (mAh)" }
                    .mapNotNull { finding ->
                        Regex("""^([a-z_ ]+):\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
                            .find(finding.value)
                            ?.let { match ->
                                val mah = match.groupValues[2].toDoubleOrNull() ?: return@let null
                                match.groupValues[1].replace('_', ' ').trim() to mah
                            }
                    }
                    .distinctBy { it.first.lowercase() }
                    .sortedByDescending { it.second }

                Text(
                    text = "Battery Drain Attribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Surface(
                    shape = RoundedCornerShape(if (isLiquid) 16.dp else 14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isLiquid) BorderStroke(1.dp, liquidBorderBrush(0.75f))
                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "System components",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (componentPower.isEmpty()) {
                            Text(
                                text = "Component-level mAh estimates are unavailable. Connect Shizuku or use a supported root BatteryStats source, then refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maxMah = componentPower.maxOf { it.second }.coerceAtLeast(0.001)
                            componentPower.forEach { (name, mah) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f mAh", mah),
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (mah / maxMah).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(5.dp),
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Includes available CPU, screen, Wi-Fi, mobile radio, Bluetooth, GPS, sensors and wakelock component estimates. Android/OEM BatteryStats decides which components are reported.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Wake-lock activity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        val wakeLocks = batteryStatsFindings.filter {
                            it.label == "Wake lock" || it.label == "History wake-lock event"
                        }.distinctBy { it.value }.take(10)
                        if (wakeLocks.isEmpty()) {
                            Text(
                                text = "No wake-lock entries were available in the current BatteryStats output.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            wakeLocks.forEach { finding ->
                                Text(
                                    text = finding.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (report.usage.any { it.batteryMah != null }) report.batteryStatsNotice
                            else "Per-app mAh attribution is unavailable without authorized BatteryStats access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "These are Android model-based estimates and activity correlations, not direct measurements or proof that a component/app caused the drain. Wake-lock names and history events are not always attributable to a package.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // These are independent diagnostics: public monitor telemetry works without Shizuku,
                // while alarm/job and detailed wake-lock records depend on system BatteryStats access.
                GlassCard {
                    CardSectionHeader(
                        icon = SysBatteryIcons.Info,
                        title = "Advanced Diagnostics",
                        subtitle = "Screen-off behavior, background activity and system scheduling"
                    )

                    val screenOff = sessionTelemetry.screenOffMillis.coerceAtLeast(0L)
                    val deepSleep = sessionTelemetry.deepSleepMillis.coerceIn(0L, screenOff)
                    val sleepShare = if (screenOff > 0L) deepSleep.toDouble() / screenOff else 0.0
                    Text(
                        text = "Deep sleep analyzer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (screenOff > 0L) {
                            "Screen off ${formatDuration(screenOff)} · deep sleep ${formatDuration(deepSleep)} · estimated sleep share ${String.format(Locale.US, "%.0f%%", sleepShare * 100)}"
                        } else "Collecting screen-off samples. Keep monitoring enabled while the display is off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deep sleep is inferred from elapsed time versus uptime deltas while monitoring; OEM kernel behavior can affect the estimate.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val wakeLocks = batteryStatsFindings.filter {
                        it.label == "Wake lock" || it.label == "History wake-lock event"
                    }.distinctBy { it.value }.take(12)
                    Text(
                        text = "Wakelock inspector",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (wakeLocks.isEmpty()) {
                        Text(
                            text = "No wake-lock records available. Shizuku or root BatteryStats history may be required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else wakeLocks.forEach { finding ->
                        Text(finding.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val networkComponents = batteryStatsFindings.filter {
                        it.label == "Component estimate (mAh)" &&
                            (it.value.contains("wifi", true) || it.value.contains("mobile_radio", true) ||
                             it.value.contains("bluetooth", true) || it.value.contains("network", true))
                    }
                    Text(
                        text = "Network & radio drain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (networkComponents.isEmpty()) {
                        Text(
                            text = "No radio-specific mAh estimates were exposed by this device. Android restricts per-app network byte history on ordinary installs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else networkComponents.forEach { finding ->
                        Text(finding.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val schedulerItems = batteryStatsFindings.filter {
                        it.label == "Alarm activity" || it.label == "Scheduled job" ||
                            it.label == "Alarm data unavailable" || it.label == "Job data unavailable"
                    }.take(20)
                    Text(
                        text = "Alarm & job inspector",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (schedulerItems.isEmpty()) {
                        Text(
                            text = "No scheduler records available. Refresh with Shizuku authorized to inspect dumpsys alarm and jobscheduler.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else schedulerItems.forEach { finding ->
                        Text(
                            text = "${finding.label}: ${finding.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Idle drain & anomaly checks",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "Measured idle drain: ${String.format(Locale.US, "%.2f%%/h", sessionTelemetry.idleDrainRateValue.coerceAtLeast(0.0))} · deep-sleep drain: ${sessionTelemetry.deepSleepTimeDropText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val temperatureC = batterySample?.temperatureTenthsCelsius?.div(10.0)
                    val anomalies = buildList {
                        if (temperatureC != null && temperatureC >= 45.0) add("Battery temperature is high (${String.format(Locale.US, "%.1f°C", temperatureC)}).")
                        if (sessionTelemetry.idleDrainRateValue >= 3.0) add("Idle drain is elevated (${String.format(Locale.US, "%.2f%%/h", sessionTelemetry.idleDrainRateValue)}).")
                        if (sessionTelemetry.activeDrainRateValue >= 10.0) add("Active discharge is unusually high (${String.format(Locale.US, "%.2f%%/h", sessionTelemetry.activeDrainRateValue)}).")
                    }
                    if (anomalies.isEmpty()) {
                        Text(
                            text = "No threshold-based battery anomalies detected from the available samples.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else anomalies.forEach { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "Anomaly thresholds are heuristic alerts, not battery fault diagnoses. Network byte attribution and per-package alarm/job ownership vary by Android version and OEM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
        }
    }
}
private fun standbyBucketLabel(bucket: Int): String = when (bucket) {
    10 -> "Active"
    20 -> "Working set"
    30 -> "Frequent"
    40 -> "Rare"
    45 -> "Restricted"
    50 -> "Never"
    else -> "Unknown ($bucket)"
}

private fun formatDuration(durationMillis: Long): String {
    val minutes = durationMillis.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
