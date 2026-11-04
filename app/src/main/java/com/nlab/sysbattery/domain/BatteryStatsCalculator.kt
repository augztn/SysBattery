package com.nlab.sysbattery.domain

import com.nlab.sysbattery.data.BatterySysfsReader
import kotlin.math.abs

/**
 * Estimates battery current, power, and discharge rates from a trailing sample window.
 */
object BatteryStatsCalculator {
    enum class Direction { CHARGING, DISCHARGING, UNKNOWN }

    data class PreviousSample(
        val timestampMillis: Long,
        val levelPercent: Int?,
        val currentMicroAmps: Long?,
        val chargeCounterMicroAmpHours: Long?
    )

    data class Rates(
        val currentMilliAmpsMagnitude: Double?,
        val batteryPercentPerHour: Double?,
        val direction: Direction,
        val powerWattsMagnitude: Double? = null
    )

    data class TimedSample(
        val timestampMillis: Long,
        val levelPercent: Int?,
        val currentMicroAmps: Long?,
        val chargeCounterMicroAmpHours: Long?
    )

    private const val RATE_WINDOW_MS = 10 * 60 * 1000L
    private const val MIN_LEVEL_RATE_SPAN_MS = 3 * 60 * 1000L
    private const val MIN_CURRENT_SPAN_MS = 15 * 1000L
    private const val MIN_SAMPLE_SPACING_MS = 1_000L
    private const val MAX_RATE_SAMPLES = 60
    private const val MAX_PLAUSIBLE_CURRENT_MA = 15_000.0
    private const val MAX_PLAUSIBLE_RATE = 500.0

    fun calculate(
        previous: PreviousSample?,
        current: BatterySysfsReader.Snapshot,
        isCharging: Boolean?
    ): Rates = calculateWindowed(
        previous?.let {
            listOf(TimedSample(it.timestampMillis, it.levelPercent, it.currentMicroAmps, it.chargeCounterMicroAmpHours))
        }.orEmpty() + TimedSample(
            current.timestampMillis,
            current.levelPercent,
            current.currentMicroAmps,
            current.chargeCounterMicroAmpHours
        ),
        current,
        isCharging
    )

    fun calculateWindowed(
        samples: List<TimedSample>,
        current: BatterySysfsReader.Snapshot,
        isCharging: Boolean?
    ): Rates {
        val direction = when (isCharging) {
            true -> Direction.CHARGING
            false -> Direction.DISCHARGING
            null -> Direction.UNKNOWN
        }
        val now = current.timestampMillis
        val window = samples.asSequence()
            .filter { it.timestampMillis <= now && now - it.timestampMillis <= RATE_WINDOW_MS }
            .sortedBy { it.timestampMillis }
            .fold(mutableListOf<TimedSample>()) { result, sample ->
                if (result.isEmpty() || sample.timestampMillis - result.last().timestampMillis >= MIN_SAMPLE_SPACING_MS) {
                    result.add(sample)
                }
                result
            }.takeLast(MAX_RATE_SAMPLES)

        val validCurrentSamples = window.mapNotNull { sample ->
            sample.currentMicroAmps?.let { raw ->
                val ma = abs(raw) / 1_000.0
                if (ma in 1.0..MAX_PLAUSIBLE_CURRENT_MA) sample.timestampMillis to ma else null
            }
        }
        val liveCurrentMa = current.currentMicroAmps?.let { raw ->
            (abs(raw) / 1_000.0).takeIf { it in 1.0..MAX_PLAUSIBLE_CURRENT_MA }
        }
        val usableCurrent = validCurrentSamples.map { it.second }
        val currentMa = liveCurrentMa ?: usableCurrent.takeIf { it.isNotEmpty() }?.average()
        val voltageMv = current.voltageMilliVolts?.takeIf { it in 2_500L..5_000L }
        val powerW = if (currentMa != null && voltageMv != null) currentMa * voltageMv / 1_000_000.0 else null

        val first = window.firstOrNull()
        val last = window.lastOrNull()
        val span = if (first != null && last != null) last.timestampMillis - first.timestampMillis else 0L
        val levelDrop = if (first?.levelPercent != null && last?.levelPercent != null) {
            first.levelPercent - last.levelPercent
        } else null
        val levelRate = if (
            direction == Direction.DISCHARGING &&
            span >= MIN_LEVEL_RATE_SPAN_MS &&
            levelDrop != null && levelDrop in 1..10
        ) {
            levelDrop * 3_600_000.0 / span
        } else null

        val fullCapacityMah = (current.fullChargeCapacityMicroAmpHours
            ?: current.estimatedFullChargeCapacityMicroAmpHours
            ?: current.designCapacityMicroAmpHours)
            ?.takeIf { it in 100_000L..30_000_000L }?.div(1_000.0)
        val currentDerivedRate = if (
            direction == Direction.DISCHARGING &&
            currentMa != null && fullCapacityMah != null && fullCapacityMah > 0.0
        ) currentMa * 100.0 / fullCapacityMah else null

        val counterDerivedRate = if (
            direction == Direction.DISCHARGING &&
            first?.chargeCounterMicroAmpHours != null &&
            last?.chargeCounterMicroAmpHours != null &&
            span >= MIN_CURRENT_SPAN_MS &&
            fullCapacityMah != null && fullCapacityMah > 0.0
        ) {
            val dischargedMah = (first.chargeCounterMicroAmpHours - last.chargeCounterMicroAmpHours) / 1_000.0
            if (dischargedMah > 0.0) dischargedMah * 100.0 / fullCapacityMah * 3_600_000.0 / span else null
        } else null

        val rate = currentDerivedRate?.takeIf { it.isFinite() && it in 0.0..MAX_PLAUSIBLE_RATE }
            ?: counterDerivedRate?.takeIf { it.isFinite() && it in 0.0..MAX_PLAUSIBLE_RATE }
            ?: levelRate?.takeIf { it.isFinite() && it in 0.0..MAX_PLAUSIBLE_RATE }

        return Rates(
            currentMilliAmpsMagnitude = currentMa,
            batteryPercentPerHour = rate,
            direction = direction,
            powerWattsMagnitude = powerW?.takeIf { it.isFinite() }
        )
    }
}
