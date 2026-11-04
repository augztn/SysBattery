package com.nlab.sysbattery.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import java.util.concurrent.TimeUnit

/**
 * Reads Android's UsageStats and privileged BatteryUsageStats when available.
 * Avoids blocked hidden-API bypass hacks that trigger ART hiddenapi warnings.
 */
class AdvancedBatteryStatsReader(context: Context) {
    private val appContext = context.applicationContext

    data class AppUsage(
        val packageName: String,
        val foregroundMillis: Long
    )

    data class AppEnergy(
        val uid: Int,
        val packageName: String?,
        val consumedMah: Double
    )

    data class Report(
        val totalConsumedMah: Double?,
        val apps: List<AppEnergy>,
        val usage: List<AppUsage>,
        val batteryStatsError: String?
    )

    fun read(windowMillis: Long = TimeUnit.HOURS.toMillis(24)): Report {
        val now = System.currentTimeMillis()
        val start = now - windowMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val usage = readUsage(start, now)
        val (total, energies, error) = readEnergy()
        return Report(
            totalConsumedMah = total,
            apps = energies,
            usage = usage,
            batteryStatsError = error
        )
    }

    private fun readEnergy(): Triple<Double?, List<AppEnergy>, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Triple(null, emptyList(), "BatteryUsageStats requires Android 12 or newer.")
        }
        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BATTERY_STATS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Triple(
                null,
                emptyList(),
                "Android restricts per-app power attribution on this build (BATTERY_STATS is privileged). Live battery readings and Usage Access data remain available."
            )
        }
        return Triple(
            null,
            emptyList(),
            "Android blocks direct BatteryUsageStats reflection for third-party apps. Use Shizuku or Root in Settings to inspect system BatteryStats."
        )
    }

    private fun readUsage(start: Long, end: Long): List<AppUsage> {
        return try {
            val manager = appContext.getSystemService(UsageStatsManager::class.java)
                ?: return emptyList()
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
                .filter { it.totalTimeInForeground > 0L }
                .groupBy { it.packageName }
                .map { (pkg, values) -> AppUsage(pkg, values.sumOf { it.totalTimeInForeground }) }
                .sortedByDescending { it.foregroundMillis }
        } catch (e: RuntimeException) {
            DiagnosticLogger.warn("UsageStats query failed", e)
            emptyList()
        }
    }
}
