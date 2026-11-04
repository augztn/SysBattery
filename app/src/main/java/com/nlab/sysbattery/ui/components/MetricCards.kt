package com.nlab.sysbattery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nlab.sysbattery.domain.BatteryStatsCalculator
import com.nlab.sysbattery.service.BatteryMonitorService
import com.nlab.sysbattery.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun CardSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            Box(
                modifier = Modifier.padding(start = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun SegmentedControlRow(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (value, label) ->
                val selected = selectedValue == value
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) }
                        .testTag("${testTagPrefix}_${label.lowercase(Locale.ROOT).replace(" ", "_")}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryHeroCard(
    level: Int?,
    direction: BatteryStatsCalculator.Direction?,
    current: String,
    temp: String,
    voltage: String,
    health: String
) {
    val isCharging = direction == BatteryStatsCalculator.Direction.CHARGING
    val animatedProgress by animateFloatAsState(
        targetValue = ((level ?: 0).coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 650),
        label = "hero_level_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_hero_card"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = SysBatteryIcons.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "BATTERY LEVEL",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = level?.toString() ?: "—",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.alignByBaseline()
                            )
                            if (level != null) {
                                Text(
                                    text = "%",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .alignByBaseline()
                                        .padding(start = 4.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCharging) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isCharging) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCharging) SysBatteryIcons.BatteryChargingFull else SysBatteryIcons.BatteryFull,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (direction) {
                                            BatteryStatsCalculator.Direction.CHARGING -> "Charging"
                                            BatteryStatsCalculator.Direction.DISCHARGING -> "Discharging"
                                            else -> "Monitoring"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCharging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (current != "—") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(30.dp)
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = current,
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                            )
                            if (animatedProgress > 0.005f) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(secondaryColor, primaryColor, primaryColor)
                                    ),
                                    startAngle = 135f,
                                    sweepAngle = 270f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = if (isCharging) SysBatteryIcons.ElectricBolt else SysBatteryIcons.BrandLogo,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickMetricPill(
                        icon = SysBatteryIcons.Thermostat,
                        label = "TEMP",
                        value = temp,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    QuickMetricPill(
                        icon = SysBatteryIcons.VoltageWave,
                        label = "VOLTAGE",
                        value = voltage,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    QuickMetricPill(
                        icon = SysBatteryIcons.Favorite,
                        label = "HEALTH",
                        value = health,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun QuickMetricPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TelemetrySessionCard(
    session: BatteryMonitorService.SessionTelemetry,
    isCharging: Boolean,
    readingMethod: String,
    onReadingMethodChange: (String) -> Unit
) {
    GlassCard(modifier = Modifier.testTag("telemetry_session_card")) {
        CardSectionHeader(
            icon = SysBatteryIcons.TrendingDown,
            title = "Drain & Sleep Summary",
            subtitle = if (isCharging) "Charging · Drain tracking paused"
            else "Session total: ${session.sessionTimeDropText}"
        )

        SegmentedControlRow(
            options = listOf(
                BatteryMonitorService.METHOD_BOTH to "Both",
                BatteryMonitorService.METHOD_TIME_DROP to "Time (%)",
                BatteryMonitorService.METHOD_RATE_ONLY to "Rate %/hr"
            ),
            selectedValue = readingMethod,
            onSelect = onReadingMethodChange,
            testTagPrefix = "reading_method"
        )

        // Equal-height 2-Column Analysis: Active Drain vs Idle Drain
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val activeHeadline = when (readingMethod) {
                BatteryMonitorService.METHOD_TIME_DROP -> session.activeTimeDropText
                BatteryMonitorService.METHOD_RATE_ONLY -> session.activeDrainRate
                else -> session.activeDrainRate
            }
            val activeSubtitle = when (readingMethod) {
                BatteryMonitorService.METHOD_TIME_DROP -> "Active: ${session.activeDrainRate}"
                BatteryMonitorService.METHOD_RATE_ONLY -> "Screen on: ${session.activeTimeDropText}"
                else -> "Screen on: ${session.activeTimeDropText}"
            }

            val idleHeadline = when (readingMethod) {
                BatteryMonitorService.METHOD_TIME_DROP -> session.idleTimeDropText
                BatteryMonitorService.METHOD_RATE_ONLY -> session.idleDrainRate
                else -> session.idleDrainRate
            }
            val idleSubtitle = when (readingMethod) {
                BatteryMonitorService.METHOD_TIME_DROP -> "Idle: ${session.idleDrainRate}"
                BatteryMonitorService.METHOD_RATE_ONLY -> "Screen off: ${session.idleTimeDropText}"
                else -> "Screen off: ${session.idleTimeDropText}"
            }

            DrainMetricTile(
                label = "ACTIVE DRAIN",
                icon = SysBatteryIcons.Smartphone,
                accentColor = MaterialTheme.colorScheme.primary,
                headline = activeHeadline,
                subtitle = activeSubtitle,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            DrainMetricTile(
                label = "IDLE DRAIN",
                icon = SysBatteryIcons.NightlightRound,
                accentColor = MaterialTheme.colorScheme.secondary,
                headline = idleHeadline,
                subtitle = idleSubtitle,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Deep Sleep vs Awake (Screen-Off Isolated CPU Efficiency Ratio)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IDLE SLEEP EFFICIENCY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Share of screen-off time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = SysBatteryIcons.NightlightRound,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Deep sleep",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = session.deepSleepTimeDropText,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = SysBatteryIcons.CpuAwake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Awake (screen off)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = session.awakeTimeDropText,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                val deepSleepFraction = (session.deepSleepShare / 100.0).toFloat().coerceIn(0f, 1f)
                val hasScreenOff = session.screenOffMillis > 0L
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    if (hasScreenOff) {
                        Row(Modifier.fillMaxSize()) {
                            if (deepSleepFraction > 0.005f) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .weight(deepSleepFraction.coerceAtLeast(0.01f))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (1f - deepSleepFraction > 0.005f) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .weight((1f - deepSleepFraction).coerceAtLeast(0.01f))
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrainMetricTile(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    headline: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = accentColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = headline,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BatteryHistoryCard(batteryHistory: List<Pair<Long, Int>>, currentLevel: Int?) {
    GlassCard {
        CardSectionHeader(
            icon = SysBatteryIcons.ShowChart,
            title = "Discharge Curve",
            subtitle = if (batteryHistory.size < 2) "Collecting telemetry points…"
            else "${batteryHistory.size} samples recorded in session",
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = currentLevel?.let { "$it%" } ?: "—",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )

        if (batteryHistory.size >= 2) {
            val chartLineColor = MaterialTheme.colorScheme.primary
            val chartFillBrush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
            val chartGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                val values = batteryHistory.map { it.second.toFloat() }
                val low = (values.minOrNull() ?: 0f).coerceAtMost(100f)
                val high = (values.maxOrNull() ?: 100f).coerceAtLeast(0f)
                val span = (high - low).coerceAtLeast(2f)
                val inset = 8.dp.toPx()
                val width = (size.width - inset * 2).coerceAtLeast(1f)
                val height = (size.height - inset * 2).coerceAtLeast(1f)

                repeat(3) { index ->
                    val y = inset + height * index / 2f
                    drawLine(
                        color = chartGridColor,
                        start = Offset(inset, y),
                        end = Offset(size.width - inset, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val points = values.mapIndexed { index, value ->
                    val x = inset + width * index / (values.size - 1).coerceAtLeast(1)
                    val y = inset + height * ((high - value) / span).coerceIn(0f, 1f)
                    Offset(x, y)
                }

                val fillPath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
                drawPath(path = fillPath, brush = chartFillBrush)

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = strokePath,
                    color = chartLineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val last = points.last()
                drawCircle(
                    color = chartLineColor.copy(alpha = 0.25f),
                    radius = 8.dp.toPx(),
                    center = last
                )
                drawCircle(
                    color = chartLineColor,
                    radius = 4.dp.toPx(),
                    center = last
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = SysBatteryIcons.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Live discharge curve plots automatically as battery readings arrive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    symbol: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = symbol,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                style = if (value.length > 13) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SessionHistoryCard(
    session: BatteryMonitorService.SessionTelemetry
) {
    GlassCard(modifier = Modifier.testTag("session_history_card")) {
        CardSectionHeader(
            icon = SysBatteryIcons.Timeline,
            title = "Session History",
            subtitle = "Screen-on & screen-off discharge log",
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (session.currentSessionState.startsWith("CHARGING")) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else if (session.currentSessionState == "ACTIVE") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = session.currentSessionState,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (session.currentSessionState.startsWith("CHARGING")) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else if (session.currentSessionState == "ACTIVE") {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
        )

        // Current Open Session Marker
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CURRENT SESSION",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val startLvl = session.currentSessionStartLevel?.let { "$it%" } ?: "—"
                    val currLvl = session.currentSessionCurrentLevel?.let { "$it%" } ?: "—"
                    val durText = BatteryMonitorService.formatDuration(session.currentSessionDurationMs)
                    Text(
                        text = "$startLvl → $currLvl · $durText",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "LIVE RATE",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.currentSessionRateText,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Recorded Session Timeline Table
        if (session.recentSessions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = SysBatteryIcons.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Lock or unlock your screen while unplugged to log active and idle drain sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SESSION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.15f)
                        )
                        Text(
                            text = "DROP · DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.35f)
                        )
                        Text(
                            text = "RATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                    session.recentSessions.take(8).forEach { record ->
                        val isActive = record.sessionType.dbValue == "ACTIVE"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1.15f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "#${record.id}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = record.sessionType.dbValue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "${record.startBatteryLevel}% → ${record.endBatteryLevel}% (${record.formattedDuration})",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.35f)
                            )

                            Text(
                                text = String.format(Locale.getDefault(), "%.2f%%/h", record.calculatedRatePerHour),
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}
