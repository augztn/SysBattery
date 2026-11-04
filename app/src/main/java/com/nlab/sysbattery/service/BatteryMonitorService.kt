package com.nlab.sysbattery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.nlab.sysbattery.MainActivity
import com.nlab.sysbattery.R
import com.nlab.sysbattery.data.BatterySessionDbHelper
import com.nlab.sysbattery.data.BatterySysfsReader
import com.nlab.sysbattery.data.safeBoolean
import com.nlab.sysbattery.data.safeDouble
import com.nlab.sysbattery.data.safeInt
import com.nlab.sysbattery.data.safeLong
import com.nlab.sysbattery.data.safeString
import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import com.nlab.sysbattery.domain.BatteryStatsCalculator
import com.nlab.sysbattery.receiver.NotificationDismissedReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * State-Driven Battery Monitor Service:
 * 1. Hardware State Tracking (ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED)
 * 2. Monotonic clock differential math (SystemClock.elapsedRealtime() & SystemClock.uptimeMillis())
 * 3. Screen-off isolated Deep Sleep vs Awake wakelock ratio engine + kernel cpuidle residency hooks
 * 4. Low-overhead multi-row status notification with 30s / integer-% / state-transition throttling
 */
class BatteryMonitorService : Service() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sampleLock = Any()
    private val screenStateLock = Any()

    private lateinit var reader: BatterySysfsReader
    private lateinit var sessionDb: BatterySessionDbHelper
    private var previousLevel: Int? = null
    private var previousSampleElapsedRealtime = 0L
    private var previousSampleUptimeMillis = 0L
    private var previousChargeCounterMicroAmpHours: Long? = null
    private var monitorJob: Job? = null
    private var screenOnMillis = 0L
    private var screenOffMillis = 0L
    private var awakeMillis = 0L
    private var deepSleepMillis = 0L
    private var screenOnSinceSampleMillis = 0L
    private var screenOffSinceSampleMillis = 0L
    private var activeDrainPercent = 0.0
    private var idleDrainPercent = 0.0
    private var activeDrainMillis = 0L
    private var idleDrainMillis = 0L
    private var previousCharging: Boolean? = null
    private var thresholdResetTriggered = false
    private var screenStateInteractive: Boolean? = null
    private var screenStateSinceElapsed = 0L
    private var screenStateSinceUptime = 0L
    private var screenStateSinceKernelIdleUs = 0L
    private val rateSamples = mutableListOf<BatteryStatsCalculator.TimedSample>()
    private var lastRateDirection: BatteryStatsCalculator.Direction? = null
    private var screenReceiverRegistered = false

    // Notification Throttle Tracking (30s timer, integer % drop, or state transition)
    private var lastNotificationUpdateElapsedMs = 0L
    private var lastNotifiedBatteryLevel: Int? = null
    private var lastNotifiedCharging: Boolean? = null

    // State-Driven Open Session Marker
    private var powerConnected: Boolean = false
    private var openSessionActive: Boolean = false
    private var openSessionType: BatterySessionDbHelper.SessionType = BatterySessionDbHelper.SessionType.ACTIVE
    private var openStartElapsedMs: Long = 0L
    private var openStartBatteryLevel: Int = -1
    private var openSubPercentDrop: Double = 0.0

    /**
     * Structured Data Transport Object (NotificationStatePayload) for the Multi-Row Notification.
     */
    data class NotificationStatePayload(
        val batteryLevelPercent: Int?,
        val temperatureText: String?,
        val chargingDirectionLabel: String,
        val instantaneousCurrentMa: Int?,
        val headerLine: String,
        val activeDrainRatePerHour: Double,
        val idleDrainRatePerHour: Double,
        val rateLine: String,
        val screenOnDurationMillis: Long,
        val screenOnDropPercent: Int,
        val screenOnLine: String,
        val screenOffDurationMillis: Long,
        val screenOffDropPercent: Int,
        val screenOffLine: String,
        val deepSleepDurationMillis: Long,
        val deepSleepRatioPercent: Double,
        val deepSleepLine: String,
        val awakeScreenOffDurationMillis: Long,
        val awakeScreenOffRatioPercent: Double,
        val awakeLine: String,
        val bodyText: String
    )

    private val hardwareStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val nowElapsed = SystemClock.elapsedRealtime()
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    updateScreenState(true, nowElapsed)
                    handleScreenTransition(interactive = true, nowElapsed = nowElapsed)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    updateScreenState(false, nowElapsed)
                    handleScreenTransition(interactive = false, nowElapsed = nowElapsed)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    handlePowerConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handlePowerDisconnected(nowElapsed)
                }
            }
        }
    }

    data class SessionTelemetry(
        val activeDrainRate: String = "0.00%/hr",
        val idleDrainRate: String = "0.00%/hr",
        val activeDrainRateValue: Double = 0.0,
        val idleDrainRateValue: Double = 0.0,
        val activeTimeDropText: String = "0s (0%)",
        val idleTimeDropText: String = "0s (0%)",
        val sessionTimeDropText: String = "0s (0%)",
        val awakeTimeDropText: String = "0s (0.00%)",
        val deepSleepTimeDropText: String = "0s (0.00%)",
        val activeLossPercent: Double = 0.0,
        val idleLossPercent: Double = 0.0,
        val totalLossPercent: Double = 0.0,
        val screenOnMillis: Long = 0L,
        val screenOffMillis: Long = 0L,
        val deepSleepMillis: Long = 0L,
        val awakeMillis: Long = 0L,
        val deepSleepShare: Double = 0.0,
        val awakeShare: Double = 0.0,
        val currentSessionState: String = "ACTIVE",
        val currentSessionStartLevel: Int? = null,
        val currentSessionCurrentLevel: Int? = null,
        val currentSessionDurationMs: Long = 0L,
        val currentSessionRateText: String = "0.00%/hr",
        val rolling24hActiveRate: String = "0.00%/hr",
        val rolling24hIdleRate: String = "0.00%/hr",
        val recentSessions: List<BatterySessionDbHelper.SessionRecord> = emptyList()
    )

    data class MonitorState(
        val sample: BatterySysfsReader.Snapshot? = null,
        val rates: BatteryStatsCalculator.Rates? = null,
        val session: SessionTelemetry = SessionTelemetry()
    )

    private var currentLoopIntervalMs = DEFAULT_INTERVAL_MS

    companion object {
        const val ACTION_START = "com.nlab.sysbattery.action.START"
        const val ACTION_STOP = "com.nlab.sysbattery.action.STOP"
        const val ACTION_REFRESH = "com.nlab.sysbattery.action.REFRESH"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        private const val DEFAULT_INTERVAL_MS = 2_000L
        private const val SCREEN_OFF_INTERVAL_MS = 60_000L
        private const val NOTIFICATION_THROTTLE_SCREEN_ON_MS = 2_000L
        private const val NOTIFICATION_THROTTLE_SCREEN_OFF_MS = 60_000L
        private const val CHANNEL_ID = "sysbattery_monitor"
        const val PREFS_NAME = "sysbattery"
        const val PREF_MONITOR_ENABLED = "monitor_enabled"
        const val PREF_DRAIN_READING_METHOD = "drain_reading_method"
        const val PREF_DRAIN_DISPLAY_MODE = PREF_DRAIN_READING_METHOD
        const val METHOD_BOTH = "Both (Time - % & %/hr)"
        const val METHOD_TIME_DROP = "Time - % Drop (20m - 5%)"
        const val METHOD_RATE_ONLY = "Hourly Rate (%/hr)"
        const val DRAIN_MODE_BOTH = METHOD_BOTH
        const val DRAIN_MODE_ELAPSED = METHOD_TIME_DROP
        const val DRAIN_MODE_RATE = METHOD_RATE_ONLY
        private const val NOTIFICATION_ID = 4102

        @Volatile
        private var isRunningInForeground = false

        private val mutableState = MutableStateFlow(MonitorState())
        val state: StateFlow<MonitorState> = mutableState.asStateFlow()

        fun resetStoredCounters(context: Context, notifyRunningService: Boolean = true) {
            val appCtx = context.applicationContext
            runCatching { BatterySessionDbHelper.getInstance(appCtx).clearAllSessions() }
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("screen_on_ms", 0L)
                .putLong("screen_off_ms", 0L)
                .putLong("awake_ms", 0L)
                .putLong("deep_sleep_ms", 0L)
                .putFloat("active_drain_percent", 0f)
                .putFloat("idle_drain_percent", 0f)
                .putLong("active_drain_ms", 0L)
                .putLong("idle_drain_ms", 0L)
                .putBoolean("open_session_active", false)
                .putLong("open_start_elapsed_ms", 0L)
                .putInt("open_start_battery_level", -1)
                .putFloat("open_sub_percent_drop", 0f)
                .putBoolean("reset_counters_requested", true)
                .putString("rate_samples_v1", "")
                .putString("rate_direction_v1", "UNKNOWN")
                .apply()
            if (notifyRunningService) {
                refresh(appCtx)
            }
        }

        fun start(context: Context, intervalMs: Long = DEFAULT_INTERVAL_MS) {
            val appCtx = context.applicationContext
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_MONITOR_ENABLED, true).apply()
            val intent = Intent(appCtx, BatteryMonitorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs.coerceIn(2_000L, 60_000L))
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }.onFailure { e ->
                DiagnosticLogger.warn("Could not start BatteryMonitorService from current state", e)
            }
        }

        fun refresh(context: Context) {
            if (!isRunningInForeground) return
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.safeBoolean(PREF_MONITOR_ENABLED, true)) return
            val intent = Intent(appCtx, BatteryMonitorService::class.java).setAction(ACTION_REFRESH)
            runCatching { appCtx.startService(intent) }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_MONITOR_ENABLED, false).apply()
            runCatching {
                appCtx.stopService(Intent(appCtx, BatteryMonitorService::class.java))
            }.onFailure { e ->
                DiagnosticLogger.warn("Could not stop BatteryMonitorService", e)
            }
        }

        fun formatDropPercent(percent: Double): String {
            val clean = percent.coerceAtLeast(0.0)
            if (!clean.isFinite() || clean < 0.05) return "0%"
            val roundedTenth = (clean * 10.0).roundToInt() / 10.0
            return if (abs(roundedTenth - roundedTenth.roundToInt()) < 0.05) {
                "${roundedTenth.roundToInt()}%"
            } else {
                String.format(Locale.getDefault(), "%.1f%%", roundedTenth)
            }
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return when {
                hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0L -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }

        fun formatTimeDrop(millis: Long, lossPercent: Double): String {
            return "${formatDuration(millis)} (${formatDropPercent(lossPercent)})"
        }

        fun formatNotificationRate(ratePerHour: Double): String {
            if (!ratePerHour.isFinite() || ratePerHour < 0.005) return "0% /hr"
            return String.format(Locale.getDefault(), "%.2f%% /hr", ratePerHour)
        }
    }

    override fun onCreate() {
        super.onCreate()
        reader = BatterySysfsReader(applicationContext)
        sessionDb = BatterySessionDbHelper.getInstance(applicationContext)
        restoreCounters()
        createNotificationChannel()
        restoreRateSamples()
        registerHardwareStateReceiver()
        val nowElapsed = SystemClock.elapsedRealtime()
        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        updateScreenState(isInteractive, nowElapsed)

        val initialCharging = batteryStatus() == true
        powerConnected = initialCharging
        if (!initialCharging) {
            val initialLevel = readCurrentBatteryLevel()
            val targetType = if (isInteractive) {
                BatterySessionDbHelper.SessionType.ACTIVE
            } else {
                BatterySessionDbHelper.SessionType.IDLE
            }
            val needsSeed = synchronized(screenStateLock) {
                !openSessionActive ||
                    openStartElapsedMs <= 0L ||
                    openStartElapsedMs > nowElapsed ||
                    openStartBatteryLevel !in 0..100 ||
                    openSessionType != targetType
            }
            if (needsSeed && initialLevel != null) {
                seedOpenSession(
                    type = targetType,
                    nowElapsed = nowElapsed,
                    batteryLevel = initialLevel
                )
            }
        }
    }

    private fun readCurrentBatteryLevel(): Int? = runCatching {
        val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
        val prop = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        prop ?: reader.readSnapshot().levelPercent
    }.getOrNull()

    private fun readKernelDeepIdleMicros(): Long {
        val baseDir = File("/sys/devices/system/cpu/cpu0/cpuidle")
        if (!baseDir.isDirectory || !baseDir.canRead()) return 0L
        var totalDeepIdleUs = 0L
        runCatching {
            val states = baseDir.listFiles() ?: return 0L
            for (stateDir in states) {
                val name = stateDir.name
                if (name.startsWith("state") && name != "state0") {
                    val timeFile = File(stateDir, "time")
                    if (timeFile.isFile && timeFile.canRead()) {
                        FileInputStream(timeFile).use { fis ->
                            BufferedReader(InputStreamReader(fis), 64).use { br ->
                                val line = br.readLine()?.trim()
                                val us = line?.toLongOrNull() ?: 0L
                                if (us > 0L) totalDeepIdleUs += us
                            }
                        }
                    }
                }
            }
        }
        return totalDeepIdleUs
    }

    private fun seedOpenSession(
        type: BatterySessionDbHelper.SessionType,
        nowElapsed: Long,
        batteryLevel: Int
    ) {
        synchronized(screenStateLock) {
            openSessionActive = true
            openSessionType = type
            openStartElapsedMs = nowElapsed
            openStartBatteryLevel = batteryLevel.coerceIn(0, 100)
            openSubPercentDrop = 0.0
        }
    }

    private fun handleScreenTransition(interactive: Boolean, nowElapsed: Long) {
        scope.launch {
            val currentCharging = batteryStatus() == true
            if (currentCharging || powerConnected) {
                powerConnected = true
                synchronized(screenStateLock) {
                    openSessionActive = false
                    openSubPercentDrop = 0.0
                }
                runCatching { sampleAndPublish(forceNotificationUpdate = true) }
                return@launch
            }

            val currentLevel = readCurrentBatteryLevel() ?: previousLevel ?: return@launch
            val targetType = if (interactive) {
                BatterySessionDbHelper.SessionType.ACTIVE
            } else {
                BatterySessionDbHelper.SessionType.IDLE
            }

            val closedSessionArgs = synchronized(screenStateLock) {
                val prevActive = openSessionActive
                val prevType = openSessionType
                val prevStartMs = openStartElapsedMs
                val prevStartLevel = openStartBatteryLevel
                val prevSubDrop = openSubPercentDrop

                openSessionActive = true
                openSessionType = targetType
                openStartElapsedMs = nowElapsed
                openStartBatteryLevel = currentLevel
                openSubPercentDrop = 0.0

                if (prevActive && prevStartMs > 0L && prevStartLevel in 0..100 && prevType != targetType) {
                    ClosedSessionArgs(prevType, prevStartMs, nowElapsed, prevStartLevel, currentLevel, prevSubDrop)
                } else null
            }

            if (closedSessionArgs != null) {
                sessionDb.recordCompletedSession(
                    sessionType = closedSessionArgs.type,
                    startElapsedMs = closedSessionArgs.startElapsedMs,
                    endElapsedMs = closedSessionArgs.endElapsedMs,
                    startBatteryLevel = closedSessionArgs.startLevel,
                    endBatteryLevel = closedSessionArgs.endLevel,
                    measuredSubPercentDrop = closedSessionArgs.subPercentDrop
                )
            }
            persistCounters()
            if (interactive && isRunningInForeground) {
                startMonitorLoop(currentLoopIntervalMs, forceImmediateNotification = true)
            } else {
                runCatching { sampleAndPublish(forceNotificationUpdate = true) }
            }
        }
    }

    private data class ClosedSessionArgs(
        val type: BatterySessionDbHelper.SessionType,
        val startElapsedMs: Long,
        val endElapsedMs: Long,
        val startLevel: Int,
        val endLevel: Int,
        val subPercentDrop: Double
    )

    private fun handlePowerConnected() {
        synchronized(screenStateLock) {
            powerConnected = true
            openSessionActive = false
            openStartElapsedMs = 0L
            openStartBatteryLevel = -1
            openSubPercentDrop = 0.0
        }
        scope.launch {
            runCatching { sampleAndPublish(forceNotificationUpdate = true) }
        }
    }

    private fun handlePowerDisconnected(nowElapsed: Long) {
        scope.launch {
            val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
            val currentLevel = readCurrentBatteryLevel() ?: previousLevel
            synchronized(screenStateLock) {
                powerConnected = false
                if (currentLevel != null) {
                    openSessionActive = true
                    openSessionType = if (interactive) {
                        BatterySessionDbHelper.SessionType.ACTIVE
                    } else {
                        BatterySessionDbHelper.SessionType.IDLE
                    }
                    openStartElapsedMs = nowElapsed
                    openStartBatteryLevel = currentLevel
                    openSubPercentDrop = 0.0
                }
            }
            runCatching { sampleAndPublish(forceNotificationUpdate = true) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (intent == null && !prefs.safeBoolean(PREF_MONITOR_ENABLED, false)) {
            DiagnosticLogger.info("Sticky restart ignored because monitor is disabled")
            monitorJob?.cancel()
            isRunningInForeground = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            monitorJob?.cancel()
            isRunningInForeground = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH && monitorJob?.isActive == true) {
            scope.launch {
                runCatching { sampleAndPublish(forceNotificationUpdate = true) }
            }
            return START_STICKY
        }
        val interval = (intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS) ?: DEFAULT_INTERVAL_MS)
            .coerceIn(1_000L, 60_000L)
        if (monitorJob?.isActive == true && currentLoopIntervalMs == interval && isRunningInForeground) {
            scope.launch {
                runCatching { sampleAndPublish(forceNotificationUpdate = true) }
            }
            return START_STICKY
        }
        currentLoopIntervalMs = interval
        runCatching {
            val currentState = mutableState.value
            val initialNotification = buildNotification(
                currentState.sample,
                currentState.rates,
                batteryStatus(),
                currentState.session.takeIf { currentState.sample != null }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            isRunningInForeground = true
            lastNotificationUpdateElapsedMs = SystemClock.elapsedRealtime()
        }.onFailure { e ->
            DiagnosticLogger.error("Unable to enter foreground state", e)
            isRunningInForeground = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startMonitorLoop(interval, forceImmediateNotification = true)
        return START_STICKY
    }

    private fun startMonitorLoop(interval: Long, forceImmediateNotification: Boolean) {
        monitorJob?.cancel()
        DiagnosticLogger.info("Battery monitor service starting")
        monitorJob = scope.launch {
            var isFirstSample = forceImmediateNotification
            while (isActive) {
                try {
                    sampleAndPublish(forceNotificationUpdate = isFirstSample)
                    isFirstSample = false
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    DiagnosticLogger.error("Battery monitor iteration failed; will retry", t)
                }
                val sleepDuration = if (screenStateInteractive == false) {
                    SCREEN_OFF_INTERVAL_MS
                } else {
                    interval
                }
                delay(sleepDuration)
            }
        }
    }

    private fun sampleAndPublish(forceNotificationUpdate: Boolean = false) = synchronized(sampleLock) {
        val sample = reader.readSnapshot()
        val charging = batteryStatus()
        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.safeBoolean("reset_counters_requested", false)) {
            resetCounters()
            preferences.edit().putBoolean("reset_counters_requested", false).apply()
        }

        val previousDirection = previousCharging
        if (charging == true) {
            synchronized(screenStateLock) {
                powerConnected = true
                openSessionActive = false
                openSubPercentDrop = 0.0
            }
        } else if (charging == false && powerConnected) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
            val lvl = sample.levelPercent ?: previousLevel
            synchronized(screenStateLock) {
                powerConnected = false
                if (lvl != null) {
                    openSessionActive = true
                    openSessionType = if (interactive) {
                        BatterySessionDbHelper.SessionType.ACTIVE
                    } else {
                        BatterySessionDbHelper.SessionType.IDLE
                    }
                    openStartElapsedMs = nowElapsed
                    openStartBatteryLevel = lvl
                    openSubPercentDrop = 0.0
                }
            }
        }

        if (previousDirection == false && charging == true &&
            preferences.safeBoolean("reset_on_plug", true)
        ) {
            resetCounters()
            thresholdResetTriggered = false
            rateSamples.clear()
        }
        if (charging != null) previousCharging = charging

        val direction = when (charging) {
            true -> BatteryStatsCalculator.Direction.CHARGING
            false -> BatteryStatsCalculator.Direction.DISCHARGING
            null -> BatteryStatsCalculator.Direction.UNKNOWN
        }
        if (lastRateDirection != null && direction != BatteryStatsCalculator.Direction.UNKNOWN &&
            lastRateDirection != direction
        ) {
            rateSamples.clear()
        }
        if (direction != BatteryStatsCalculator.Direction.UNKNOWN) lastRateDirection = direction
        rateSamples.add(
            BatteryStatsCalculator.TimedSample(
                sample.timestampMillis,
                sample.levelPercent,
                sample.currentMicroAmps,
                sample.chargeCounterMicroAmpHours
            )
        )
        trimRateSamples(sample.timestampMillis)
        persistRateSamples()
        val rates = BatteryStatsCalculator.calculateWindowed(rateSamples.toList(), sample, charging)

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val elapsed = if (previousSampleElapsedRealtime > 0L) {
            (nowElapsed - previousSampleElapsedRealtime).coerceAtLeast(0L)
        } else 0L

        val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        val (onElapsed, offElapsed) = updateAndMeasureScreenTime(interactive, nowElapsed, nowUptime)
        val trackedElapsed = onElapsed + offElapsed

        val oldLevel = previousLevel
        val newLevel = sample.levelPercent
        val resetAtThreshold = preferences.safeBoolean("reset_at_threshold", true)
        val threshold = preferences.safeInt("reset_threshold_percent", 90).coerceIn(50, 100)
        if (charging != true || newLevel == null || newLevel < threshold) {
            thresholdResetTriggered = false
        } else if (resetAtThreshold && !thresholdResetTriggered) {
            resetCounters()
            thresholdResetTriggered = true
            rateSamples.clear()
        }

        synchronized(screenStateLock) {
            if (charging == false && !openSessionActive && newLevel != null) {
                openSessionActive = true
                openSessionType = if (interactive) {
                    BatterySessionDbHelper.SessionType.ACTIVE
                } else {
                    BatterySessionDbHelper.SessionType.IDLE
                }
                openStartElapsedMs = nowElapsed
                openStartBatteryLevel = newLevel
                openSubPercentDrop = 0.0
            }
        }

        activeDrainMillis += onElapsed
        idleDrainMillis += offElapsed

        if (charging == false && elapsed > 0L) {
            val previousCounter = previousChargeCounterMicroAmpHours
            val currentCounter = sample.chargeCounterMicroAmpHours
            val capacityUah = (sample.fullChargeCapacityMicroAmpHours
                ?: sample.estimatedFullChargeCapacityMicroAmpHours
                ?: sample.designCapacityMicroAmpHours
                ?: 4_000_000L)
                .takeIf { it in 100_000L..30_000_000L } ?: 4_000_000L
            val capacityMah = capacityUah.toDouble() / 1_000.0

            val currentMa = sample.currentMicroAmps?.let { abs(it) / 1_000.0 }
                ?.takeIf { it.isFinite() && it in 1.0..15_000.0 }
            val currentLoss = if (currentMa != null) {
                (currentMa * (elapsed / 3_600_000.0) / capacityMah * 100.0)
                    .takeIf { it.isFinite() && it in 0.0..5.0 }
            } else null

            val maxPlausibleIntervalLoss = (elapsed / 3_600_000.0 * 120.0).coerceIn(0.02, 5.0)
            val counterDeltaUah = if (previousCounter != null && currentCounter != null) {
                previousCounter - currentCounter
            } else null
            val counterBasedLossPercent = if (
                currentLoss == null &&
                counterDeltaUah != null && counterDeltaUah in 1L..100_000L &&
                trackedElapsed > 0L
            ) {
                (counterDeltaUah.toDouble() / capacityUah.toDouble() * 100.0)
                    .takeIf { it.isFinite() && it in 0.0..maxPlausibleIntervalLoss }
            } else null

            val intervalLossPercent = currentLoss
                ?: counterBasedLossPercent
                ?: rates.batteryPercentPerHour?.takeIf { it.isFinite() && it in 0.0..500.0 }
                    ?.let { it * elapsed / 3_600_000.0 }
                ?: if (oldLevel != null && newLevel != null && oldLevel > newLevel &&
                    oldLevel - newLevel <= 10
                ) (oldLevel - newLevel).toDouble() else null

            if (intervalLossPercent != null && trackedElapsed > 0L) {
                activeDrainPercent += intervalLossPercent * onElapsed / trackedElapsed
                idleDrainPercent += intervalLossPercent * offElapsed / trackedElapsed
                synchronized(screenStateLock) {
                    if (openSessionActive) {
                        openSubPercentDrop += intervalLossPercent
                    }
                }
            }
        }
        previousChargeCounterMicroAmpHours = sample.chargeCounterMicroAmpHours

        val session = computeSessionTelemetry(charging, sample, rates)
        mutableState.value = MonitorState(sample, rates, session)

        val levelChanged = newLevel != null && newLevel != lastNotifiedBatteryLevel
        val chargingChanged = charging != lastNotifiedCharging
        val throttleMs = if (interactive) {
            NOTIFICATION_THROTTLE_SCREEN_ON_MS
        } else {
            NOTIFICATION_THROTTLE_SCREEN_OFF_MS
        }
        val timerExpired = (nowElapsed - lastNotificationUpdateElapsedMs) >= throttleMs
        if (forceNotificationUpdate || levelChanged || chargingChanged || timerExpired) {
            runCatching {
                notificationManager()?.notify(NOTIFICATION_ID, buildNotification(sample, rates, charging, session))
                lastNotificationUpdateElapsedMs = nowElapsed
                lastNotifiedBatteryLevel = newLevel
                lastNotifiedCharging = charging
            }.onFailure { DiagnosticLogger.error("Battery notification update failed", it) }
        }

        previousLevel = newLevel
        previousSampleElapsedRealtime = nowElapsed
        previousSampleUptimeMillis = nowUptime
        persistCounters()
    }

    private fun registerHardwareStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(hardwareStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(hardwareStateReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (t: RuntimeException) {
            DiagnosticLogger.error("Unable to register hardware-state receiver", t)
        }
    }

    private fun updateScreenState(interactive: Boolean, now: Long = SystemClock.elapsedRealtime()) {
        synchronized(screenStateLock) { updateScreenStateLocked(interactive, now, SystemClock.uptimeMillis()) }
    }

    private fun updateAndMeasureScreenTime(
        interactive: Boolean,
        nowElapsed: Long,
        nowUptime: Long
    ): Pair<Long, Long> =
        synchronized(screenStateLock) {
            when (screenStateInteractive) {
                null -> {
                    screenStateInteractive = interactive
                    screenStateSinceElapsed = nowElapsed
                    screenStateSinceUptime = nowUptime
                    screenStateSinceKernelIdleUs = if (!interactive) readKernelDeepIdleMicros() else 0L
                }
                interactive -> accumulateScreenTimeLocked(nowElapsed, nowUptime)
                else -> updateScreenStateLocked(interactive, nowElapsed, nowUptime)
            }
            val onDelta = screenOnSinceSampleMillis
            val offDelta = screenOffSinceSampleMillis
            screenOnSinceSampleMillis = 0L
            screenOffSinceSampleMillis = 0L
            Pair(onDelta, offDelta)
        }

    private fun updateScreenStateLocked(interactive: Boolean, nowElapsed: Long, nowUptime: Long) {
        accumulateScreenTimeLocked(nowElapsed, nowUptime)
        screenStateInteractive = interactive
        screenStateSinceElapsed = nowElapsed
        screenStateSinceUptime = nowUptime
        screenStateSinceKernelIdleUs = if (!interactive) readKernelDeepIdleMicros() else 0L
    }

    private fun accumulateScreenTime(
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowUptime: Long = SystemClock.uptimeMillis()
    ) {
        synchronized(screenStateLock) { accumulateScreenTimeLocked(nowElapsed, nowUptime) }
    }

    /**
     * Strictly isolates Screen-On vs Screen-Off time and tracks Deep Sleep vs Awake
     * ONLY during Screen-Off periods so that (Deep Sleep + Awake == Screen Off).
     */
    private fun accumulateScreenTimeLocked(nowElapsed: Long, nowUptime: Long) {
        val sinceElapsed = screenStateSinceElapsed
        val sinceUptime = screenStateSinceUptime
        if (sinceElapsed <= 0L || nowElapsed <= sinceElapsed) {
            screenStateSinceElapsed = nowElapsed
            screenStateSinceUptime = nowUptime
            return
        }
        val elapsed = nowElapsed - sinceElapsed
        when (screenStateInteractive) {
            true -> {
                screenOnMillis += elapsed
                screenOnSinceSampleMillis += elapsed
            }
            false -> {
                screenOffMillis += elapsed
                screenOffSinceSampleMillis += elapsed
                val upDelta = if (sinceUptime > 0L) {
                    (nowUptime - sinceUptime).coerceIn(0L, elapsed)
                } else elapsed
                val monotonicSleepDelta = (elapsed - upDelta).coerceAtLeast(0L)

                val currentKernelIdleUs = readKernelDeepIdleMicros()
                val kernelIdleDeltaMs = if (screenStateSinceKernelIdleUs > 0L && currentKernelIdleUs > screenStateSinceKernelIdleUs) {
                    ((currentKernelIdleUs - screenStateSinceKernelIdleUs) / 1000L).coerceIn(0L, elapsed)
                } else 0L
                screenStateSinceKernelIdleUs = currentKernelIdleUs

                val effectiveSleepDelta = max(monotonicSleepDelta, kernelIdleDeltaMs).coerceIn(0L, elapsed)
                val effectiveAwakeDelta = (elapsed - effectiveSleepDelta).coerceAtLeast(0L)

                deepSleepMillis += effectiveSleepDelta
                awakeMillis += effectiveAwakeDelta
            }
            null -> Unit
        }
        screenStateSinceElapsed = nowElapsed
        screenStateSinceUptime = nowUptime
    }

    private fun trimRateSamples(now: Long) {
        val cutoff = now - 10 * 60 * 1000L
        rateSamples.removeAll { it.timestampMillis < cutoff || it.timestampMillis > now }
        while (rateSamples.size > 60) rateSamples.removeAt(0)
        val compacted = rateSamples.fold(mutableListOf<BatteryStatsCalculator.TimedSample>()) { out, item ->
            if (out.isEmpty() || item.timestampMillis - out.last().timestampMillis >= 15_000L) out.add(item)
            else out[out.lastIndex] = item
            out
        }
        rateSamples.clear()
        rateSamples.addAll(compacted)
    }

    private fun persistRateSamples() {
        val serialized = rateSamples.takeLast(30).joinToString(";") {
            listOf(
                it.timestampMillis,
                it.levelPercent ?: "x",
                it.currentMicroAmps ?: "x",
                it.chargeCounterMicroAmpHours ?: "x"
            ).joinToString(",")
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("rate_samples_v1", serialized)
            .putString("rate_direction_v1", (lastRateDirection ?: BatteryStatsCalculator.Direction.UNKNOWN).name)
            .apply()
    }

    private fun restoreRateSamples() = synchronized(sampleLock) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastRateDirection = runCatching {
            BatteryStatsCalculator.Direction.valueOf(prefs.safeString("rate_direction_v1", "UNKNOWN"))
        }.getOrDefault(BatteryStatsCalculator.Direction.UNKNOWN)
        val parsed = prefs.safeString("rate_samples_v1", "").split(";").mapNotNull { row ->
            val fields = row.split(",")
            if (fields.size != 4) return@mapNotNull null
            val timestamp = fields[0].toLongOrNull() ?: return@mapNotNull null
            val level = fields[1].takeIf { it != "x" }?.toIntOrNull()
            val current = fields[2].takeIf { it != "x" }?.toLongOrNull()
            val charge = fields[3].takeIf { it != "x" }?.toLongOrNull()
            BatteryStatsCalculator.TimedSample(timestamp, level, current, charge)
        }
        rateSamples.clear()
        rateSamples.addAll(parsed)
        trimRateSamples(System.currentTimeMillis())
    }

    private fun resetCounters() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val currentLevel = readCurrentBatteryLevel() ?: previousLevel
        val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        synchronized(screenStateLock) {
            accumulateScreenTimeLocked(nowElapsed, nowUptime)
            screenOnMillis = 0L
            screenOffMillis = 0L
            awakeMillis = 0L
            deepSleepMillis = 0L
            screenOnSinceSampleMillis = 0L
            screenOffSinceSampleMillis = 0L
            screenStateSinceElapsed = nowElapsed
            screenStateSinceUptime = nowUptime
            screenStateSinceKernelIdleUs = if (!interactive) readKernelDeepIdleMicros() else 0L
            if (!powerConnected && currentLevel != null) {
                openSessionActive = true
                openSessionType = if (interactive) {
                    BatterySessionDbHelper.SessionType.ACTIVE
                } else {
                    BatterySessionDbHelper.SessionType.IDLE
                }
                openStartElapsedMs = nowElapsed
                openStartBatteryLevel = currentLevel
                openSubPercentDrop = 0.0
            } else {
                openSessionActive = false
                openSubPercentDrop = 0.0
            }
        }
        activeDrainPercent = 0.0
        idleDrainPercent = 0.0
        activeDrainMillis = 0L
        idleDrainMillis = 0L
        rateSamples.clear()
        runCatching { sessionDb.clearAllSessions() }
        persistRateSamples()
        persistCounters()
    }

    private fun batteryStatus(): Boolean? = try {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
    } catch (t: RuntimeException) {
        DiagnosticLogger.error("Unable to read battery status", t)
        null
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager()?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SysBattery battery monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing battery telemetry and discharge estimates"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun computeSessionTelemetry(
        charging: Boolean?,
        sample: BatterySysfsReader.Snapshot? = null,
        rates: BatteryStatsCalculator.Rates? = null
    ): SessionTelemetry {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val (shownOnMillis, shownOffMillis, rawAwakeMillis, rawSleepMillis) = synchronized(screenStateLock) {
            val extraScreen = if (screenStateSinceElapsed > 0L) {
                (nowElapsed - screenStateSinceElapsed).coerceAtLeast(0L)
            } else 0L
            val onMs = if (screenStateInteractive == true) screenOnMillis + extraScreen else screenOnMillis
            val offMs = if (screenStateInteractive == false) screenOffMillis + extraScreen else screenOffMillis

            val (extraAwake, extraSleep) = if (screenStateInteractive == false && extraScreen > 0L) {
                val upDelta = if (screenStateSinceUptime > 0L) {
                    (nowUptime - screenStateSinceUptime).coerceIn(0L, extraScreen)
                } else extraScreen
                val monotonicSleepDelta = (extraScreen - upDelta).coerceAtLeast(0L)
                val currentKernelIdleUs = readKernelDeepIdleMicros()
                val kernelIdleDeltaMs = if (
                    screenStateSinceKernelIdleUs > 0L && currentKernelIdleUs > screenStateSinceKernelIdleUs
                ) {
                    ((currentKernelIdleUs - screenStateSinceKernelIdleUs) / 1000L).coerceIn(0L, extraScreen)
                } else 0L
                val slpDelta = max(monotonicSleepDelta, kernelIdleDeltaMs).coerceIn(0L, extraScreen)
                val awkDelta = (extraScreen - slpDelta).coerceAtLeast(0L)
                Pair(awkDelta, slpDelta)
            } else {
                Pair(0L, 0L)
            }
            Quad(onMs, offMs, awakeMillis + extraAwake, deepSleepMillis + extraSleep)
        }

        // Synchronize displayed seconds so Deep Sleep + Awake ALWAYS equals Screen Off to the exact second
        val totalOffSec = (shownOffMillis / 1000L).coerceAtLeast(0L)
        val totalRawSleepTracked = (rawSleepMillis + rawAwakeMillis).coerceAtLeast(1L)
        val rawSleepRatio = if (shownOffMillis > 0L) {
            (rawSleepMillis.toDouble() / totalRawSleepTracked.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        val sleepSec = if (totalOffSec > 0L) {
            (totalOffSec * rawSleepRatio).roundToInt().toLong().coerceIn(0L, totalOffSec)
        } else 0L
        val awakeSec = (totalOffSec - sleepSec).coerceAtLeast(0L)
        val shownSleepMillis = sleepSec * 1000L
        val shownAwakeMillis = awakeSec * 1000L

        val deepSleepShare = if (totalOffSec > 0L) {
            (sleepSec.toDouble() * 100.0 / totalOffSec.toDouble()).coerceIn(0.0, 100.0)
        } else 0.0
        val awakeShare = if (totalOffSec > 0L) {
            (100.0 - deepSleepShare).coerceIn(0.0, 100.0)
        } else 0.0

        val currentLevel = sample?.levelPercent ?: previousLevel
        val (openActive, openType, openStartMs, openStartLvl, openSubDrop) = synchronized(screenStateLock) {
            OpenSessionSnapshot(
                openSessionActive && charging != true,
                openSessionType,
                openStartElapsedMs,
                openStartBatteryLevel,
                openSubPercentDrop
            )
        }

        val openDurationMs = if (openActive && openStartMs > 0L) {
            (nowElapsed - openStartMs).coerceAtLeast(0L)
        } else 0L

        val openEffectiveDrop = if (openActive && openStartLvl in 0..100 && currentLevel != null) {
            val intDrop = (openStartLvl - currentLevel).coerceAtLeast(0)
            if (openSubDrop > 0.0) {
                val minBound = (intDrop - 0.99).coerceAtLeast(0.0)
                val maxBound = intDrop + 0.99
                openSubDrop.coerceIn(minBound, maxBound)
            } else {
                intDrop.toDouble()
            }
        } else 0.0

        val openRateDouble = if (openActive && openDurationMs >= BatterySessionDbHelper.MIN_VALID_SESSION_MS) {
            BatterySessionDbHelper.calculateSessionRatePerHour(
                startBatteryPercent = (openStartLvl.takeIf { it in 0..100 } ?: 100).toDouble(),
                endBatteryPercent = (openStartLvl.takeIf { it in 0..100 } ?: 100).toDouble() - openEffectiveDrop,
                elapsedMillis = openDurationMs
            )
        } else null

        val rolling24h = if (::sessionDb.isInitialized) {
            sessionDb.queryRolling24hSummary()
        } else {
            BatterySessionDbHelper.Rolling24hSummary()
        }

        val combined24hActiveDrop = rolling24h.active.totalDropPercent +
            if (openActive && openType == BatterySessionDbHelper.SessionType.ACTIVE) openEffectiveDrop else 0.0
        val combined24hActiveMs = rolling24h.active.totalDurationMillis +
            if (openActive && openType == BatterySessionDbHelper.SessionType.ACTIVE) openDurationMs else 0L

        val combined24hIdleDrop = rolling24h.idle.totalDropPercent +
            if (openActive && openType == BatterySessionDbHelper.SessionType.IDLE) openEffectiveDrop else 0.0
        val combined24hIdleMs = rolling24h.idle.totalDurationMillis +
            if (openActive && openType == BatterySessionDbHelper.SessionType.IDLE) openDurationMs else 0L

        val rolling24hActiveRateVal = ratePerHourNumeric(combined24hActiveDrop, combined24hActiveMs, charging)
        val rolling24hIdleRateVal = ratePerHourNumeric(combined24hIdleDrop, combined24hIdleMs, charging)

        val liveInstantRate = if (charging == false) {
            rates?.batteryPercentPerHour?.takeIf { it.isFinite() && it in 0.01..200.0 } ?: run {
                val currentMa = sample?.currentMicroAmps?.let { abs(it) / 1_000.0 }
                    ?.takeIf { it.isFinite() && it in 1.0..15_000.0 }
                val capMah = (sample?.fullChargeCapacityMicroAmpHours
                    ?: sample?.estimatedFullChargeCapacityMicroAmpHours
                    ?: sample?.designCapacityMicroAmpHours
                    ?: 4_000_000L).takeIf { it in 100_000L..30_000_000L }?.div(1_000.0)
                if (currentMa != null && capMah != null && capMah > 0.0) {
                    (currentMa * 100.0 / capMah).takeIf { it.isFinite() && it in 0.01..200.0 }
                } else null
            }
        } else null

        val continuousActiveRate = ratePerHourNumeric(activeDrainPercent, activeDrainMillis, charging)
        val continuousIdleRate = ratePerHourNumeric(idleDrainPercent, idleDrainMillis, charging)

        val activeRateVal = when {
            charging == true -> 0.0
            screenStateInteractive == true && liveInstantRate != null -> {
                if (continuousActiveRate > 0.0 && activeDrainMillis >= 15_000L) {
                    (liveInstantRate * 0.65 + continuousActiveRate * 0.35).coerceIn(0.0, 200.0)
                } else {
                    liveInstantRate
                }
            }
            continuousActiveRate > 0.0 -> continuousActiveRate
            combined24hActiveMs >= BatterySessionDbHelper.MIN_VALID_SESSION_MS -> rolling24hActiveRateVal
            else -> liveInstantRate ?: 0.0
        }

        val idleRateVal = when {
            charging == true -> 0.0
            continuousIdleRate > 0.0 -> continuousIdleRate
            combined24hIdleMs >= BatterySessionDbHelper.MIN_VALID_SESSION_MS -> rolling24hIdleRateVal
            screenStateInteractive == false && liveInstantRate != null -> liveInstantRate
            else -> 0.0
        }

        val activeRate = formatRatePerHourUI(activeRateVal)
        val idleRate = formatRatePerHourUI(idleRateVal)
        val rolling24hActiveRate = formatRatePerHourUI(
            if (rolling24hActiveRateVal > 0.0) rolling24hActiveRateVal else activeRateVal
        )
        val rolling24hIdleRate = formatRatePerHourUI(
            if (rolling24hIdleRateVal > 0.0) rolling24hIdleRateVal else idleRateVal
        )

        val activeLoss = activeDrainPercent.coerceAtLeast(0.0)
        val idleLoss = idleDrainPercent.coerceAtLeast(0.0)
        val totalLoss = (activeLoss + idleLoss).coerceAtLeast(0.0)

        val totalSessionMillis = (shownOnMillis + shownOffMillis).coerceAtLeast(0L)

        val stateLabel = when {
            charging == true -> "CHARGING"
            openActive -> openType.dbValue
            else -> "IDLE"
        }

        val liveSessionRateDouble = liveInstantRate ?: openRateDouble
        val openRateStr = when {
            charging == true -> "Charging"
            liveSessionRateDouble != null -> String.format(Locale.getDefault(), "%.2f%%/hr", liveSessionRateDouble)
            else -> "0.00%/hr"
        }

        val deepSleepFormatted = String.format(
            Locale.getDefault(),
            "%s (%.1f%% of off)",
            formatDuration(shownSleepMillis),
            deepSleepShare
        )
        val awakeFormatted = String.format(
            Locale.getDefault(),
            "%s (%.1f%% of off)",
            formatDuration(shownAwakeMillis),
            awakeShare
        )

        return SessionTelemetry(
            activeDrainRate = activeRate,
            idleDrainRate = idleRate,
            activeDrainRateValue = activeRateVal,
            idleDrainRateValue = idleRateVal,
            activeTimeDropText = formatTimeDrop(shownOnMillis, activeLoss),
            idleTimeDropText = formatTimeDrop(shownOffMillis, idleLoss),
            sessionTimeDropText = formatTimeDrop(totalSessionMillis, totalLoss),
            awakeTimeDropText = awakeFormatted,
            deepSleepTimeDropText = deepSleepFormatted,
            activeLossPercent = activeLoss,
            idleLossPercent = idleLoss,
            totalLossPercent = totalLoss,
            screenOnMillis = shownOnMillis,
            screenOffMillis = shownOffMillis,
            deepSleepMillis = shownSleepMillis,
            awakeMillis = shownAwakeMillis,
            deepSleepShare = deepSleepShare,
            awakeShare = awakeShare,
            currentSessionState = stateLabel,
            currentSessionStartLevel = openStartLvl.takeIf { it in 0..100 },
            currentSessionCurrentLevel = currentLevel,
            currentSessionDurationMs = openDurationMs,
            currentSessionRateText = openRateStr,
            rolling24hActiveRate = rolling24hActiveRate,
            rolling24hIdleRate = rolling24hIdleRate,
            recentSessions = rolling24h.recentSessions
        )
    }

    private data class OpenSessionSnapshot(
        val active: Boolean,
        val type: BatterySessionDbHelper.SessionType,
        val startMs: Long,
        val startLvl: Int,
        val subDrop: Double
    )

    private data class Quad(val a: Long, val b: Long, val c: Long, val d: Long)

    fun buildNotificationPayload(
        sample: BatterySysfsReader.Snapshot?,
        rates: BatteryStatsCalculator.Rates?,
        charging: Boolean?,
        tele: SessionTelemetry,
        showBatteryPercentage: Boolean,
        showChargingPower: Boolean,
        temperatureUnit: String,
        idleWarningText: String?
    ): NotificationStatePayload {
        val levelInt = if (showBatteryPercentage) sample?.levelPercent else null
        val temperatureText = sample?.temperatureTenthsCelsius?.let {
            val celsius = it / 10.0
            val value = if (temperatureUnit == "Fahrenheit (°F)") celsius * 9.0 / 5.0 + 32.0 else celsius
            val unit = if (temperatureUnit == "Fahrenheit (°F)") "°F" else "°C"
            String.format(Locale.getDefault(), "%.0f%s", value, unit)
        }
        val currentMa = if (showChargingPower) {
            (rates?.currentMilliAmpsMagnitude
                ?: sample?.currentMilliAmpsSigned?.let { abs(it) }
                    ?.takeIf { it.isFinite() && it in 1.0..20_000.0 })
                ?.roundToInt()
        } else null

        val modeLabel = when (charging) {
            true -> "Charging"
            false -> "Discharging"
            null -> "Discharging"
        }

        val headerLine = buildString {
            if (levelInt != null) append(levelInt).append("% · ")
            if (temperatureText != null) append(temperatureText).append(" · ")
            append(modeLabel)
            if (currentMa != null && currentMa > 0) {
                append(" ").append(currentMa).append(" mA")
            }
        }

        val activeRateFormatted = formatNotificationRate(tele.activeDrainRateValue)
        val idleRateFormatted = formatNotificationRate(tele.idleDrainRateValue)
        val rateLine = String.format(
            Locale.getDefault(),
            "Active drain: %s · Idle drain: %s",
            activeRateFormatted,
            idleRateFormatted
        )

        val screenOnDropInt = tele.activeLossPercent.coerceAtLeast(0.0).roundToInt()
        val screenOffDropInt = tele.idleLossPercent.coerceAtLeast(0.0).roundToInt()
        val screenOnDropFormatted = formatDropPercent(tele.activeLossPercent)
        val screenOffDropFormatted = formatDropPercent(tele.idleLossPercent)

        val screenOnLine = String.format(
            Locale.getDefault(),
            "Screen on: %s (%s drain)",
            formatDuration(tele.screenOnMillis),
            screenOnDropFormatted
        )
        val screenOffLine = String.format(
            Locale.getDefault(),
            "Screen off: %s (%s drain)",
            formatDuration(tele.screenOffMillis),
            screenOffDropFormatted
        )
        val deepSleepLine = String.format(
            Locale.getDefault(),
            "Deep sleep: %s (%.1f%% of screen off)",
            formatDuration(tele.deepSleepMillis),
            tele.deepSleepShare
        )
        val awakeLine = String.format(
            Locale.getDefault(),
            "Awake: %s (%.1f%% of screen off)",
            formatDuration(tele.awakeMillis),
            tele.awakeShare
        )

        val bodyLines = buildString {
            if (idleWarningText != null) {
                append(idleWarningText).append('\n')
            }
            append(rateLine).append('\n')
            append(screenOnLine).append('\n')
            append(screenOffLine).append('\n')
            append(deepSleepLine).append('\n')
            append(awakeLine)
        }

        return NotificationStatePayload(
            batteryLevelPercent = levelInt,
            temperatureText = temperatureText,
            chargingDirectionLabel = modeLabel,
            instantaneousCurrentMa = currentMa,
            headerLine = headerLine,
            activeDrainRatePerHour = tele.activeDrainRateValue,
            idleDrainRatePerHour = tele.idleDrainRateValue,
            rateLine = rateLine,
            screenOnDurationMillis = tele.screenOnMillis,
            screenOnDropPercent = screenOnDropInt,
            screenOnLine = screenOnLine,
            screenOffDurationMillis = tele.screenOffMillis,
            screenOffDropPercent = screenOffDropInt,
            screenOffLine = screenOffLine,
            deepSleepDurationMillis = tele.deepSleepMillis,
            deepSleepRatioPercent = tele.deepSleepShare,
            deepSleepLine = deepSleepLine,
            awakeScreenOffDurationMillis = tele.awakeMillis,
            awakeScreenOffRatioPercent = tele.awakeShare,
            awakeLine = awakeLine,
            bodyText = bodyLines
        )
    }

    private fun buildNotification(
        sample: BatterySysfsReader.Snapshot?,
        rates: BatteryStatsCalculator.Rates?,
        charging: Boolean?,
        session: SessionTelemetry? = null
    ): Notification {
        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val showBatteryPercentage = preferences.safeBoolean("show_battery_icon", true)
        val showChargingPower = preferences.safeBoolean("show_charging_power", true)
        val temperatureUnit = preferences.safeString("temperature_unit", "Celsius (°C)")

        val tele = session ?: computeSessionTelemetry(charging, sample, rates)
        val idleWarningEnabled = preferences.safeBoolean("idle_warning", true)
        val idleWarningLimit = preferences.safeInt("idle_warning_percent", 5).coerceIn(1, 20)
        val idleWarningText = if (idleWarningEnabled && tele.idleDrainRateValue > idleWarningLimit) {
            "High idle drain detected: ${tele.idleDrainRate} (limit $idleWarningLimit%/h)"
        } else null

        val payload = buildNotificationPayload(
            sample = sample,
            rates = rates,
            charging = charging,
            tele = tele,
            showBatteryPercentage = showBatteryPercentage,
            showChargingPower = showChargingPower,
            temperatureUnit = temperatureUnit,
            idleWarningText = idleWarningText
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sysbattery_status)
            .setContentTitle(payload.headerLine)
            .setContentText(payload.rateLine)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    4103,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this,
                    4104,
                    Intent(this, NotificationDismissedReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(payload.headerLine)
                    .bigText(payload.bodyText)
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun restoreCounters() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        screenOnMillis = prefs.safeLong("screen_on_ms", 0L)
        screenOffMillis = prefs.safeLong("screen_off_ms", 0L)
        awakeMillis = prefs.safeLong("awake_ms", 0L)
        deepSleepMillis = prefs.safeLong("deep_sleep_ms", 0L)
        if (deepSleepMillis + awakeMillis > screenOffMillis + 2_000L) {
            deepSleepMillis = screenOffMillis.coerceAtLeast(0L)
            awakeMillis = 0L
        }
        activeDrainPercent = prefs.safeDouble("active_drain_percent", 0.0)
        idleDrainPercent = prefs.safeDouble("idle_drain_percent", 0.0)
        activeDrainMillis = prefs.safeLong("active_drain_ms", 0L)
        idleDrainMillis = prefs.safeLong("idle_drain_ms", 0L)

        val nowElapsed = SystemClock.elapsedRealtime()
        val savedOpenActive = prefs.safeBoolean("open_session_active", false)
        val savedOpenStartMs = prefs.safeLong("open_start_elapsed_ms", 0L)
        val savedOpenStartLvl = prefs.safeInt("open_start_battery_level", -1)
        val savedOpenSubDrop = prefs.safeDouble("open_sub_percent_drop", 0.0)
        val savedOpenType = BatterySessionDbHelper.SessionType.fromDb(
            prefs.safeString("open_session_type", "ACTIVE")
        )
        synchronized(screenStateLock) {
            if (savedOpenActive && savedOpenStartMs in 1L..nowElapsed && savedOpenStartLvl in 0..100) {
                openSessionActive = true
                openSessionType = savedOpenType
                openStartElapsedMs = savedOpenStartMs
                openStartBatteryLevel = savedOpenStartLvl
                openSubPercentDrop = savedOpenSubDrop.coerceAtLeast(0.0)
            }
        }
    }

    private fun persistCounters() {
        val (totals, openSnap) = synchronized(screenStateLock) {
            Pair(
                Quad(screenOnMillis, screenOffMillis, awakeMillis, deepSleepMillis),
                OpenSessionSnapshot(
                    openSessionActive,
                    openSessionType,
                    openStartElapsedMs,
                    openStartBatteryLevel,
                    openSubPercentDrop
                )
            )
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong("screen_on_ms", totals.a)
            .putLong("screen_off_ms", totals.b)
            .putLong("awake_ms", totals.c)
            .putLong("deep_sleep_ms", totals.d)
            .putFloat("active_drain_percent", activeDrainPercent.toFloat())
            .putFloat("idle_drain_percent", idleDrainPercent.toFloat())
            .putLong("active_drain_ms", activeDrainMillis)
            .putLong("idle_drain_ms", idleDrainMillis)
            .putBoolean("open_session_active", openSnap.active)
            .putString("open_session_type", openSnap.type.dbValue)
            .putLong("open_start_elapsed_ms", openSnap.startMs)
            .putInt("open_start_battery_level", openSnap.startLvl)
            .putFloat("open_sub_percent_drop", openSnap.subDrop.toFloat())
            .apply()
    }

    private fun ratePerHourNumeric(drainPercent: Double, durationMillis: Long, isCharging: Boolean? = false): Double {
        if (isCharging == true && drainPercent <= 0.0) return 0.0
        if (durationMillis <= 0L) return 0.0
        if (!drainPercent.isFinite() || drainPercent < 0.0) return 0.0
        val rate = max(0.0, drainPercent) * 3_600_000.0 / durationMillis
        if (!rate.isFinite() || rate < 0.005) return 0.0
        return rate.coerceIn(0.0, 200.0)
    }

    private fun formatRatePerHourUI(rate: Double): String {
        if (!rate.isFinite() || rate < 0.005) return "0.00%/hr"
        return String.format(Locale.getDefault(), "%.2f%%/hr", rate)
    }

    override fun onDestroy() {
        DiagnosticLogger.info("Battery monitor service destroyed")
        isRunningInForeground = false
        monitorJob?.cancel()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(hardwareStateReceiver) }
            screenReceiverRegistered = false
        }
        accumulateScreenTime()
        persistCounters()
        persistRateSamples()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
