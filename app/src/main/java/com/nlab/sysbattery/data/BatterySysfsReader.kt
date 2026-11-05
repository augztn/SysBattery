package com.nlab.sysbattery.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Reads battery telemetry using Android's public API first and read-only sysfs fallbacks.
 * Normalizes OEM fuel-gauge current into signed microamps (µA).
 */
class BatterySysfsReader(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BatteryManager::class.java)
    private val estimatePrefs = appContext.getSharedPreferences("sysbattery", Context.MODE_PRIVATE)

    @Volatile
    private var sysfsUsesMicroAmps: Boolean? = null

    @Volatile
    private var propUsesMicroAmps: Boolean? = null

    @Volatile
    private var lastValidCurrentMicroAmps: Long? = null

    @Volatile
    private var lastValidCurrentTimestampMs: Long = 0L

    data class Snapshot(
        val timestampMillis: Long = System.currentTimeMillis(),
        val levelPercent: Int?,
        val currentMicroAmps: Long?,
        val voltageMilliVolts: Long?,
        val chargeCounterMicroAmpHours: Long?,
        val temperatureTenthsCelsius: Int?,
        val health: Int? = null,
        val cycleCount: Int? = null,
        val fullChargeCapacityMicroAmpHours: Long? = null,
        val estimatedFullChargeCapacityMicroAmpHours: Long? = null,
        val designCapacityMicroAmpHours: Long? = null,
        val source: String
    ) {
        val currentMilliAmpsSigned: Double?
            get() = currentMicroAmps?.div(1_000.0)
    }

    fun readSnapshot(): Snapshot {
        val now = System.currentTimeMillis()
        val batteryIntent = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) {
            null
        }
        val level = intProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                ?.takeIf { it in 0..100 }
            ?: readSysfs(listOf("/sys/class/power_supply/battery/capacity"))?.toInt()?.takeIf { it in 0..100 }

        val rawPropCurrent = signedProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?: signedProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val rawSysfsCurrent = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/battery/current_avg"
            )
        )

        val normSysfsCurrent = rawSysfsCurrent?.let { normalizeCurrentToMicroAmps(it, isSysfs = true) }
        val normPropCurrent = rawPropCurrent?.let { normalizeCurrentToMicroAmps(it, isSysfs = false) }

        val primaryCurrent = when {
            normSysfsCurrent != null && abs(normSysfsCurrent) >= 10_000L -> normSysfsCurrent
            normPropCurrent != null && abs(normPropCurrent) >= 10_000L -> normPropCurrent
            normSysfsCurrent != null && abs(normSysfsCurrent) >= 2_000L -> normSysfsCurrent
            normPropCurrent != null && abs(normPropCurrent) >= 2_000L -> normPropCurrent
            else -> null
        }

        val current = if (primaryCurrent != null) {
            lastValidCurrentMicroAmps = primaryCurrent
            lastValidCurrentTimestampMs = now
            primaryCurrent
        } else if (lastValidCurrentMicroAmps != null && (now - lastValidCurrentTimestampMs) <= 5_000L) {
            lastValidCurrentMicroAmps
        } else {
            normSysfsCurrent ?: normPropCurrent
        }

        val batteryVoltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it in 2_000..6_000 }?.toLong()
        val voltageUv = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/bms/voltage_now"
            )
        )
        val voltageMv = batteryVoltageMv
            ?: voltageUv?.takeIf { it in 2_000_000L..30_000_000L }?.div(1_000L)

        val chargeCounter = (longProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0L }
            ?: readSysfs(
                listOf(
                    "/sys/class/power_supply/battery/charge_counter",
                    "/sys/class/power_supply/bms/charge_counter"
                )
            )?.takeIf { it > 0L })
            ?.let(::normalizeCapacityToMicroAmpHours)

        val fullChargeCapacity = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/bms/charge_full"
            )
        )?.let(::normalizeCapacityToMicroAmpHours)?.takeIf { it in 500_000L..30_000_000L }

        val sysfsDesignCapacity = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/bms/design_capacity"
            )
        )?.let(::normalizeCapacityToMicroAmpHours)?.takeIf { it in 500_000L..30_000_000L }

        val configuredDesignCapacity = estimatePrefs
            .safeInt("design_capacity_mah", 0)
            .takeIf { it in 500..20_000 }
            ?.let { it * 1_000L }
        // Design capacity is only a device-reported or user-configured value.
        // A single charge-counter/percentage ratio is not a reliable design-capacity measurement.
        val designCapacity = sysfsDesignCapacity ?: configuredDesignCapacity
        val systemCycleCount = if (Build.VERSION.SDK_INT >= 34) {
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null
        val cycleCount = systemCycleCount ?: readSysfs(
            listOf(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count"
            )
        )?.toInt()?.takeIf { it >= 0 }

        if (level == 100 && chargeCounter != null && chargeCounter in 1_000_000L..30_000_000L &&
            current != null && abs(current) in 1_000L..25_000_000L
        ) {
            estimatePrefs.edit().putLong("estimated_full_charge_capacity_uah", chargeCounter).apply()
        }
        val estimatedFullChargeCapacity = estimatePrefs
            .safeLong("estimated_full_charge_capacity_uah", 0L)
            .takeIf { it in 1_000_000L..30_000_000L }
        val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it in 0..1_000 }
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)?.takeIf { it >= 0 }
        val sources = mutableListOf<String>()
        if (level != null) sources += "level"
        if (current != null) sources += "current"
        if (voltageMv != null) sources += "voltage"
        if (chargeCounter != null) sources += "charge-counter"
        if (health != null) sources += "health"
        if (cycleCount != null) sources += "cycle-count"
        if (fullChargeCapacity != null) sources += "full-charge-capacity"
        if (designCapacity != null) sources += "design-capacity"
        if (estimatedFullChargeCapacity != null) sources += "estimated-full-charge-capacity"

        return Snapshot(
            timestampMillis = now,
            levelPercent = level,
            currentMicroAmps = current,
            voltageMilliVolts = voltageMv,
            chargeCounterMicroAmpHours = chargeCounter,
            temperatureTenthsCelsius = temperature,
            health = health,
            cycleCount = cycleCount,
            fullChargeCapacityMicroAmpHours = fullChargeCapacity,
            estimatedFullChargeCapacityMicroAmpHours = estimatedFullChargeCapacity,
            designCapacityMicroAmpHours = designCapacity,
            source = sources.joinToString(",").ifBlank { "no-readable-telemetry" }
        )
    }

    /**
     * Many OEM fuel-gauge drivers (Realme/Oplus, Xiaomi, MediaTek) report current in mA
     * (e.g. -834 or -1250) rather than µA (-834000 or -1250000).
     * Any source that reports > 20,000 in magnitude is confirmed to be using µA;
     * otherwise values in 2..20,000 are in mA and scaled by 1,000 to µA.
     * Raw dummy values of -1, 0, or 1 are ignored as 0L.
     */
    private fun normalizeCurrentToMicroAmps(raw: Long, isSysfs: Boolean): Long {
        val magnitude = abs(raw)
        if (magnitude > 20_000L) {
            if (isSysfs) sysfsUsesMicroAmps = true else propUsesMicroAmps = true
        }
        val usesMicroAmps = if (isSysfs) sysfsUsesMicroAmps == true else propUsesMicroAmps == true
        return normalizeRawCurrentToMicroAmps(raw, usesMicroAmps)
    }

    private fun normalizeCapacityToMicroAmpHours(raw: Long): Long {
        return if (raw in 500L..25_000L) raw * 1_000L else raw
    }

    companion object {
        fun normalizeRawCurrentToMicroAmps(raw: Long, sourceKnownMicroAmps: Boolean = false): Long {
            val magnitude = abs(raw)
            if (magnitude <= 1L) return 0L
            if (magnitude > 20_000L || sourceKnownMicroAmps) return raw
            return raw * 1_000L
        }
    }

    private fun intProperty(id: Int): Int? = try {
        manager?.getIntProperty(id)?.takeIf { it >= 0 }
    } catch (_: RuntimeException) {
        null
    }

    private fun longProperty(id: Int): Long? = try {
        manager?.getLongProperty(id)?.takeIf { it >= 0L && it != Long.MIN_VALUE }
    } catch (_: RuntimeException) {
        null
    }

    private fun signedProperty(id: Int): Long? = try {
        manager?.getLongProperty(id)?.takeIf { it != Long.MIN_VALUE && it != 0L }
    } catch (_: RuntimeException) {
        null
    }

    private fun readSysfs(paths: List<String>): Long? {
        for (path in paths) {
            try {
                val file = File(path)
                if (file.isFile && file.canRead()) {
                    file.readText(Charsets.US_ASCII).trim().toLongOrNull()?.let { return it }
                }
            } catch (_: IOException) {
            } catch (_: SecurityException) {
            }
        }
        return null
    }
}
