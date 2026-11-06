package com.nlab.sysbattery.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import androidx.core.content.ContextCompat
import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import java.util.concurrent.TimeUnit

/**
 * Combines public UsageStats activity data with optional Android BatteryStats UID energy estimates.
 * Background transitions are activity lifecycle signals, not measurements of background CPU time.
 */
class AdvancedBatteryStatsReader(context: Context) {
    private val appContext = context.applicationContext

    data class AppUsage(
        val packageName: String,
        val appLabel: String,
        val foregroundMillis: Long,
        val backgroundTransitions: Int,
        val lastBackgroundAtMillis: Long?,
        val batteryMah: Double?
    )

    data class Report(
        val usage: List<AppUsage>,
        val batteryStatsNotice: String
    )

    fun read(
        windowMillis: Long = TimeUnit.HOURS.toMillis(24),
        uidPowerEstimates: List<RootBatteryStatsParser.UidPowerEstimate> = emptyList()
    ): Report {
        val now = System.currentTimeMillis()
        val start = now - windowMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val foreground = readForegroundUsage(start, now)
        val background = readBackgroundTransitions(start, now)
        val powerByPackage = resolveUidPower(uidPowerEstimates)
        val packageNames = (foreground.keys + background.keys + powerByPackage.keys).toSet()

        val usage = packageNames.map { packageName ->
            val appInfo = runCatching { appContext.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
            val label = appInfo?.let {
                runCatching { appContext.packageManager.getApplicationLabel(it).toString() }.getOrNull()
            }?.takeIf { it.isNotBlank() } ?: packageName
            val activity = background[packageName]
            AppUsage(
                packageName = packageName,
                appLabel = label,
                foregroundMillis = foreground[packageName] ?: 0L,
                backgroundTransitions = activity?.count ?: 0,
                lastBackgroundAtMillis = activity?.lastAtMillis,
                batteryMah = powerByPackage[packageName]
            )
        }.sortedWith(
            compareByDescending<AppUsage> { it.batteryMah ?: -1.0 }
                .thenByDescending { it.foregroundMillis }
                .thenBy { it.appLabel.lowercase() }
        )

        return Report(usage = usage, batteryStatsNotice = readEnergyNotice(uidPowerEstimates.isNotEmpty()))
    }

    private data class BackgroundActivity(val count: Int, val lastAtMillis: Long)

    private fun readEnergyNotice(hasBatteryStats: Boolean): String {
        if (hasBatteryStats) {
            return "Per-app mAh values are Android BatteryStats UID estimates supplied by Shizuku. Shared Android UIDs are grouped; these are estimates, not direct electrical measurements."
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "Per-app energy estimates depend on Android BatteryStats support. Enable Shizuku or root for system-reported UID estimates."
        }
        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BATTERY_STATS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Android restricts per-app power attribution for ordinary installs. Usage Access provides foreground and background-transition activity; Shizuku or root can add Android-reported UID mAh estimates."
        }
        return "Direct BatteryUsageStats access is not exposed to this app. Use Shizuku or root for Android-reported UID mAh estimates."
    }

    private fun readForegroundUsage(start: Long, end: Long): Map<String, Long> {
        return try {
            val manager = appContext.getSystemService(UsageStatsManager::class.java)
                ?: return emptyMap()
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
                .filter { it.totalTimeInForeground > 0L && it.packageName.isNotBlank() }
                .groupBy { it.packageName }
                .mapValues { (_, values) -> values.sumOf { it.totalTimeInForeground }.coerceAtLeast(0L) }
        } catch (e: RuntimeException) {
            DiagnosticLogger.warn("UsageStats query failed", e)
            emptyMap()
        }
    }

    private fun readBackgroundTransitions(start: Long, end: Long): Map<String, BackgroundActivity> {
        return try {
            val manager = appContext.getSystemService(UsageStatsManager::class.java)
                ?: return emptyMap()
            val events = manager.queryEvents(start, end) ?: return emptyMap()
            val counts = mutableMapOf<String, Int>()
            val lastAt = mutableMapOf<String, Long>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val packageName = event.packageName?.takeIf { it.isNotBlank() } ?: continue
                if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    counts[packageName] = (counts[packageName] ?: 0) + 1
                    lastAt[packageName] = event.timeStamp
                }
            }
            counts.mapValues { (packageName, count) ->
                BackgroundActivity(count, lastAt[packageName] ?: end)
            }
        } catch (e: RuntimeException) {
            DiagnosticLogger.warn("UsageEvents query failed", e)
            emptyMap()
        }
    }

    private fun resolveUidPower(
        estimates: List<RootBatteryStatsParser.UidPowerEstimate>
    ): Map<String, Double> {
        if (estimates.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Double>()
        estimates.forEach { estimate ->
            val uid = parseAndroidUid(estimate.uid) ?: return@forEach
            val packages = runCatching { appContext.packageManager.getPackagesForUid(uid).orEmpty() }
                .getOrDefault(emptyArray())
            if (packages.isEmpty()) return@forEach

            // A shared UID is one Android accounting bucket. Divide its estimate evenly so
            // the app list does not double-count the same UID total.
            val perPackageMah = estimate.powerMah / packages.size
            packages.forEach { packageName ->
                result[packageName] = (result[packageName] ?: 0.0) + perPackageMah
            }
        }
        return result
    }

    private fun parseAndroidUid(rawUid: String): Int? {
        val normalized = rawUid.trim().removePrefix("UID").trim()
        normalized.toIntOrNull()?.let { return it.takeIf { value -> value >= 0 } }
        val appUid = Regex("""^u(\\d+)a(\\d+)$""", RegexOption.IGNORE_CASE).matchEntire(normalized)
            ?: return null
        val userId = appUid.groupValues[1].toIntOrNull() ?: return null
        val appId = appUid.groupValues[2].toIntOrNull() ?: return null
        if (appId !in 0..99_999 || userId !in 0..99_999) return null
        return UserHandle.getUid(userId, 10_000 + appId)
    }
}
