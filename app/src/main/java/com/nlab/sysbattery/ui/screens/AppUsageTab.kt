package com.nlab.sysbattery.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nlab.sysbattery.data.AdvancedBatteryStatsReader
import com.nlab.sysbattery.ui.components.CardSectionHeader
import com.nlab.sysbattery.ui.components.GlassCard
import com.nlab.sysbattery.ui.components.SysBatteryIcons
import com.nlab.sysbattery.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun AppUsageTab(
    usageAccessGranted: Boolean,
    onUsageAccess: () -> Unit,
    onRefreshAdvanced: () -> Unit,
    shizukuLoading: Boolean,
    advancedReport: AdvancedBatteryStatsReader.Report?,
    advancedError: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard {
            CardSectionHeader(
                icon = SysBatteryIcons.Apps,
                title = "App Usage & Power",
                subtitle = "Foreground activity & system attribution estimates"
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

        advancedReport?.let { report ->
            GlassCard {
                CardSectionHeader(
                    icon = SysBatteryIcons.Smartphone,
                    title = "Foreground App Activity (24h)",
                    subtitle = if (report.usage.isEmpty()) "No apps recorded yet"
                    else "${report.usage.size} apps active in the last 24h"
                )

                if (report.usage.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
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
                                    }
                                    Text(
                                        text = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "System Power Attribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = report.totalConsumedMah?.let { String.format(Locale.getDefault(), "%.1f mAh", it) }
                                ?: "Privileged API Restricted",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        report.batteryStatsError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
