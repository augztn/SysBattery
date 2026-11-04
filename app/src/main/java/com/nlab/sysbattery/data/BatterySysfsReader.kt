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

    data class Snapshot(
        val timestampMillis: Long = System.currentTimeMillis(),
        val levelPercent: Int?,
        val currentMicroAmps: Long?,
        val voltageMilliVolts: Long?,
        val chargeCounterMicroAmpHours: Long?,
        val temperatureTenthsCelsius: Int?,
        val health: Int? = null,
        val cycleCount: Int? = null,
        val status: Int? = null,
        val fullChargeCapacityMicroAmpHours: Long? = null,
        val estimatedFullChargeCapacityMicroAmpHours: Long? = null,
        val designCapacityMicroAmpHours: Long? = null,
        val source: String
    ) {
        val currentMilliAmpsSigned: Double?
            get() = currentMicroAmps?.div(1_000.0)
    }

    fun readSnapshot(): Snapshot {
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

        val rawCurrent = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/battery/current_avg"
            )
        ) ?: signedProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val current = rawCurrent?.let(::normalizeCurrentToMicroAmps)

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

        val chargeCounter = longProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0L }
            ?: readSysfs(
                listOf(
                    "/sys/class/power_supply/battery/charge_counter",
                    "/sys/class/power_supply/bms/charge_counter"
                )
            )?.takeIf { it > 0L }
        val fullChargeCapacity = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/bms/charge_full"
            )
        )?.takeIf { it in 100_000L..30_000_000L }
        val sysfsDesignCapacity = readSysfs(
            listOf(
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/bms/design_capacity"
            )
        )?.takeIf { it in 100_000L..30_000_000L }
        val configuredDesignCapacity = estimatePrefs
            .safeInt("design_capacity_mah", 0)
            .takeIf { it in 500..20_000 }
            ?.let { it * 1_000L }
        val fallbackCapacity = if (fullChargeCapacity == null && sysfsDesignCapacity == null && configuredDesignCapacity == null) {
            (if (chargeCounter != null && level != null && level in 1..100) {
                (chargeCounter * 100L / level).takeIf { it in 100_000L..30_000_000L }
            } else null) ?: 4_000_000L
        } else null
        val designCapacity = sysfsDesignCapacity ?: configuredDesignCapacity ?: fallbackCapacity
        val systemCycleCount = if (Build.VERSION.SDK_INT >= 34) {
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null
        val cycleCount = systemCycleCount ?: readSysfs(
            listOf(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count"
            )
        )?.toInt()?.takeIf { it >= 0 }

        if (level == 100 && chargeCounter != null && chargeCounter in 100_000L..30_000_000L &&
            current != null && abs(current) in 1_000L..25_000_000L
        ) {
            estimatePrefs.edit().putLong("estimated_full_charge_capacity_uah", chargeCounter).apply()
        }
        val estimatedFullChargeCapacity = estimatePrefs
            .safeLong("estimated_full_charge_capacity_uah", 0L)
            .takeIf { it in 100_000L..30_000_000L }
        val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it in 0..1_000 }
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)?.takeIf { it >= 0 }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?.takeIf { it >= 0 }
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
            levelPercent = level,
            currentMicroAmps = current,
            voltageMilliVolts = voltageMv,
            chargeCounterMicroAmpHours = chargeCounter,
            temperatureTenthsCelsius = temperature,
            health = health,
            cycleCount = cycleCount,
            status = status,
            fullChargeCapacityMicroAmpHours = fullChargeCapacity,
            estimatedFullChargeCapacityMicroAmpHours = estimatedFullChargeCapacity,
            designCapacityMicroAmpHours = designCapacity,
            source = sources.joinToString(",").ifBlank { "no-readable-telemetry" }
        )
    }

    /**
     * Some OEM fuel-gauge drivers report current in mA (e.g. -280) rather than µA (-280000).
     * Values with magnitude between 10 and 999 are normalized from mA to µA.
     */
    private fun normalizeCurrentToMicroAmps(raw: Long): Long {
        val magnitude = abs(raw)
        return if (magnitude in 10L..999L) raw * 1_000L else raw
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
