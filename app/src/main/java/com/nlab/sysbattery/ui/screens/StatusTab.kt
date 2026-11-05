package com.nlab.sysbattery.ui.screens

import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nlab.sysbattery.data.BatterySysfsReader
import com.nlab.sysbattery.data.safeBoolean
import com.nlab.sysbattery.domain.BatteryStatsCalculator
import com.nlab.sysbattery.service.BatteryMonitorService
import com.nlab.sysbattery.ui.components.BatteryHeroCard
import com.nlab.sysbattery.ui.components.BatteryHistoryCard
import com.nlab.sysbattery.ui.components.CardSectionHeader
import com.nlab.sysbattery.ui.components.GlassCard
import com.nlab.sysbattery.ui.components.MetricCard
import com.nlab.sysbattery.ui.components.SessionHistoryCard
import com.nlab.sysbattery.ui.components.SysBatteryIcons
import com.nlab.sysbattery.ui.components.TelemetrySessionCard
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatusTab(
    sample: BatterySysfsReader.Snapshot?,
    rates: BatteryStatsCalculator.Rates?,
    session: BatteryMonitorService.SessionTelemetry,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBackground: () -> Unit,
    batteryHistory: List<Pair<Long, Int>>,
    temperatureUnit: String,
    designCapacityMah: Double?,
    readingMethod: String,
    onReadingMethodChange: (String) -> Unit,
    prefs: SharedPreferences,
    preferencesRevision: Int,
    onPreferencesChanged: () -> Unit
) {
    var activeSubTab by rememberSaveable { mutableIntStateOf(0) }
    val showChargingPower = prefs.safeBoolean("show_charging_power", true)

    val level = sample?.levelPercent
    val current = rates?.currentMilliAmpsMagnitude?.let { magnitude ->
        val signed = when (rates.direction) {
            BatteryStatsCalculator.Direction.CHARGING -> magnitude
            BatteryStatsCalculator.Direction.DISCHARGING -> -magnitude
            BatteryStatsCalculator.Direction.UNKNOWN -> magnitude
        }
        val baseCurrent = String.format(Locale.getDefault(), "%+.0f mA", signed)
        if (rates.direction == BatteryStatsCalculator.Direction.CHARGING && showChargingPower) {
            val watts = rates.powerWattsMagnitude
            if (watts != null && watts > 0.05) {
                String.format(Locale.getDefault(), "%s · %.1f W", baseCurrent, watts)
            } else {
                baseCurrent
            }
        } else if (rates.direction == BatteryStatsCalculator.Direction.CHARGING && !showChargingPower) {
            "—"
        } else {
            baseCurrent
        }
    } ?: "—"

    val temp = sample?.temperatureTenthsCelsius?.let {
        val celsius = it / 10.0
        val fahrenheit = celsius * 9.0 / 5.0 + 32.0
        if (temperatureUnit == "Fahrenheit (°F)") String.format(Locale.getDefault(), "%.1f °F", fahrenheit)
        else String.format(Locale.getDefault(), "%.1f °C", celsius)
    } ?: "—"

    val voltage = sample?.voltageMilliVolts?.let { "$it mV" } ?: "—"
    val power = rates?.powerWattsMagnitude?.let {
        val signed = if (rates.direction == BatteryStatsCalculator.Direction.DISCHARGING) -it else it
        String.format(Locale.getDefault(), "%+.2f W", signed)
    } ?: "—"

    val cycleCount = sample?.cycleCount?.toString() ?: "Not reported"
    val reportedFullCapacity = sample?.fullChargeCapacityMicroAmpHours
    val estimatedFullCapacity = sample?.estimatedFullChargeCapacityMicroAmpHours
    val displayedFullCapacity = reportedFullCapacity ?: estimatedFullCapacity
    val fullCapacityText = displayedFullCapacity?.let {
        val prefix = if (reportedFullCapacity != null) "" else "~"
        prefix + String.format(Locale.getDefault(), "%.0f mAh", it / 1000.0)
    } ?: "Not reported"

    val healthEstimate = if (displayedFullCapacity != null && designCapacityMah != null && designCapacityMah > 0.0) {
        String.format(
            Locale.getDefault(),
            "%.0f%%",
            (displayedFullCapacity / 1000.0 * 100.0 / designCapacityMah).coerceIn(0.0, 200.0)
        )
    } else "Set capacity"

    val health = when (sample?.health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-volt"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }

    val currentSigned = sample?.currentMilliAmpsSigned
    val hourlyRateString = when {
        rates?.direction == BatteryStatsCalculator.Direction.CHARGING -> "Charging"
        rates?.batteryPercentPerHour != null -> String.format(Locale.getDefault(), "%.2f%%/h", rates.batteryPercentPerHour)
        sample != null && currentSigned != null && currentSigned < -1.0 -> {
            val capacityMah = (sample.fullChargeCapacityMicroAmpHours
                ?: sample.estimatedFullChargeCapacityMicroAmpHours
                ?: sample.designCapacityMicroAmpHours)
                ?.takeIf { it in 100_000L..30_000_000L }
                ?.div(1_000.0)
            val liveRate = capacityMah?.takeIf { it > 0.0 }
                ?.let { abs(currentSigned) * 100.0 / it }
            liveRate?.takeIf { it.isFinite() }?.let {
                String.format(Locale.getDefault(), "%.2f%%/h", it)
            } ?: "Capacity unavailable"
        }
        else -> session.activeDrainRate
    }

    val drainRateCardTitle = when (readingMethod) {
        BatteryMonitorService.METHOD_TIME_DROP -> "Session drop"
        else -> "Drain rate"
    }

    val drainRateText = when {
        rates?.direction == BatteryStatsCalculator.Direction.CHARGING -> "Charging"
        readingMethod == BatteryMonitorService.METHOD_TIME_DROP -> session.sessionTimeDropText
        else -> hourlyRateString
    }

    val chargeCounterText = sample?.chargeCounterMicroAmpHours?.let {
        String.format(Locale.getDefault(), "%.0f mAh", it / 1000.0)
    } ?: "Not reported"

    val isCharging = rates?.direction == BatteryStatsCalculator.Direction.CHARGING

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("STATS" to 0, "OPTIONS" to 1).forEach { (title, idx) ->
                    val selected = activeSubTab == idx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { activeSubTab = idx }
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(68.dp)
                                .height(2.5.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        if (activeSubTab == 1) {
            GlassCard {
                BatteryMonitorOptionsList(
                    prefs = prefs,
                    preferencesRevision = preferencesRevision,
                    onPreferencesChanged = onPreferencesChanged
                )
            }
            return@Column
        }

        BatteryHeroCard(
            level = level,
            direction = rates?.direction,
            current = current,
            temp = temp,
            voltage = voltage,
            health = health
        )

        TelemetrySessionCard(
            session = session,
            isCharging = isCharging,
            readingMethod = readingMethod,
            onReadingMethodChange = onReadingMethodChange
        )

        SessionHistoryCard(session = session)

        BatteryHistoryCard(
            batteryHistory = batteryHistory,
            currentLevel = level
        )

        SectionLabel("ELECTRICAL TELEMETRY")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            MetricCard(
                title = "Current",
                value = current,
                icon = SysBatteryIcons.ElectricBolt,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = "Power",
                value = power,
                icon = SysBatteryIcons.FlashOn,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            MetricCard(
                title = "Voltage",
                value = voltage,
                icon = SysBatteryIcons.VoltageWave,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = drainRateCardTitle,
                value = drainRateText,
                icon = SysBatteryIcons.TrendingDown,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        SectionLabel("CAPACITY & HEALTH")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            MetricCard(
                title = "Temperature",
                value = temp,
                icon = SysBatteryIcons.Thermostat,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = "Health status",
                value = health,
                icon = SysBatteryIcons.Favorite,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            MetricCard(
                title = "Cycle count",
                value = cycleCount,
                icon = SysBatteryIcons.Autorenew,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = "Charge counter",
                value = chargeCounterText,
                icon = SysBatteryIcons.BatteryChargingFull,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            MetricCard(
                title = "Full capacity",
                value = fullCapacityText,
                icon = SysBatteryIcons.BatteryFull,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = "Health estimate",
                value = healthEstimate,
                icon = SysBatteryIcons.HealthAndSafety,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        GlassCard {
            CardSectionHeader(
                icon = SysBatteryIcons.NotificationsActive,
                title = "Background Service",
                subtitle = if (running) "Active · Live status notification"
                else "Stopped · Enable for background tracking",
                trailingContent = {
                    Switch(
                        checked = running,
                        onCheckedChange = { if (it) onStart() else onStop() },
                        modifier = Modifier.testTag("background_service_switch")
                    )
                }
            )

            FilledTonalButton(
                onClick = onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("optimize_background_permission_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(SysBatteryIcons.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Optimize background permission",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "Readings depend on device fuel-gauge and kernel power_supply drivers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
