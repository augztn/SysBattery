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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * State-Driven Battery Monitor Service:
 * 1. Hardware State Tracking (ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED)
 * 2. Monotonic clock differential math (SystemClock.elapsedRealtime() & SystemClock.uptimeMillis())
 * 3. Screen-off isolated Deep Sleep vs Awake wakelock ratio engine
 * 4. Low-overhead multi-row status notification with 1s screen-on live updates
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
    private var previousChargeCounterMicroAmpHours: Long? = null
    private var monitorJob: Job? = null
    private var screenOnMillis = 0L
    private var screenOffMillis = 0L
    private var awakeMillis = 0L
    private var deepSleepMillis = 0L
    private var screenOnSinceSampleMillis = 0L
    private var screenOffSinceSampleMillis = 0L
    private var deepSleepSinceSampleMillis = 0L
    private var awakeSinceSampleMillis = 0L
    private var activeDrainPercent = 0.0
    private var idleDrainPercent = 0.0
    private var deepSleepDrainPercent = 0.0
    private var awakeDrainPercent = 0.0
    private var activeDrainMillis = 0L
    private var idleDrainMillis = 0L
    private var subPercentSinceLevelChange = 0.0
    private var onMillisSinceLevelChange = 0L
    private var offMillisSinceLevelChange = 0L
    private var sleepMillisSinceLevelChange = 0L
    private var awakeMillisSinceLevelChange = 0L
    private var previousCharging: Boolean? = null
    private var thresholdResetTriggered = false
    private var screenStateInteractive: Boolean? = null
    private var screenStateSinceElapsed = 0L
    private var screenStateSinceUptime = 0L
    private val rateSamples = mutableListOf<BatteryStatsCalculator.TimedSample>()
    private var lastRateDirection: BatteryStatsCalculator.Direction? = null
    private var screenReceiverRegistered = false

    // Notification Throttle Tracking (1s live updates while CPU is awake)
    private var lastNotificationUpdateElapsedMs = 0L
    private var lastPersistElapsedMs = 0L
    private var lastRollingQueryElapsedMs = 0L
    private var cachedRolling24h = BatterySessionDbHelper.Rolling24hSummary()
    private var lastNotifiedBatteryLevel: Int? = null
    private var lastNotifiedCharging: Boolean? = null
    private var cachedIconLevel: Int? = null
    private var cachedBatteryPercentIcon: IconCompat? = null

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
        val headerLine: String,
        val rateLine: String,
        val screenOnLine: String,
        val screenOffLine: String,
        val deepSleepLine: String,
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
        val awakeTimeDropText: String = "0s (0%)",
        val deepSleepTimeDropText: String = "0s (0%)",
        val activeLossPercent: Double = 0.0,
        val idleLossPercent: Double = 0.0,
        val deepSleepLossPercent: Double = 0.0,
        val awakeLossPercent: Double = 0.0,
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
        private const val DEFAULT_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        private const val PERSIST_THROTTLE_MS = 5_000L
        private const val ROLLING_QUERY_THROTTLE_MS = 30_000L
        private const val CHANNEL_ID = "sysbattery_monitor"
        private const val COUNTERS_SCHEMA_VERSION = 4
        const val PREFS_NAME = "sysbattery"
        const val PREF_MONITOR_ENABLED = "monitor_enabled"
        const val PREF_DRAIN_DISPLAY_MODE = "drain_reading_method"
        const val METHOD_BOTH = "Both (Time - % & %/hr)"
        const val METHOD_TIME_DROP = "Time - % Drop (20m - 5%)"
        const val METHOD_RATE_ONLY = "Hourly Rate (%/hr)"
        private const val NOTIFICATION_ID = 4102

        @Volatile
        private var isRunningInForeground = false

        private val mutableState = MutableStateFlow(MonitorState())
        val state: StateFlow<MonitorState> = mutableState.asStateFlow()

        fun resetStoredCounters(context: Context, notifyRunningService: Boolean = true) {
            val appCtx = context.applicationContext
            runCatching { BatterySessionDbHelper.getInstance(appCtx).clearAllSessions() }
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt("counters_schema_v", COUNTERS_SCHEMA_VERSION)
                .putLong("screen_on_ms", 0L)
                .putLong("screen_off_ms", 0L)
                .putLong("awake_ms", 0L)
                .putLong("deep_sleep_ms", 0L)
                .putLong("screen_state_since_elapsed", 0L)
                .putLong("screen_state_since_uptime", 0L)
                .putBoolean("screen_state_has_saved", false)
                .putFloat("active_drain_percent", 0f)
                .putFloat("idle_drain_percent", 0f)
                .putFloat("deep_sleep_drain_percent", 0f)
                .putFloat("awake_drain_percent", 0f)
                .putLong("active_drain_ms", 0L)
                .putLong("idle_drain_ms", 0L)
                .putFloat("sub_percent_since_lvl", 0f)
                .putLong("on_ms_since_lvl", 0L)
                .putLong("off_ms_since_lvl", 0L)
                .putLong("sleep_ms_since_lvl", 0L)
                .putLong("awake_ms_since_lvl", 0L)
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
                .putExtra(EXTRA_INTERVAL_MS, intervalMs.coerceIn(1_000L, 60_000L))
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
            if (!clean.isFinite() || clean < 0.005) return "0%"
            if (clean < 0.1) {
                return String.format(Locale.getDefault(), "%.2f%%", clean)
            }
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

            // Flush accumulated screen time and battery drain for the session that just ended BEFORE switching session type
            runCatching { sampleAndPublish(forceNotificationUpdate = true) }

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
                cachedRolling24h = sessionDb.queryRolling24hSummary()
                lastRollingQueryElapsedMs = nowElapsed
            }
            persistCounters()
            persistRateSamples()
            lastPersistElapsedMs = nowElapsed
            if (monitorJob?.isActive != true && isRunningInForeground) {
                startMonitorLoop(currentLoopIntervalMs, forceImmediateNotification = true)
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
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.safeBoolean("reset_on_plug", true)) {
                synchronized(sampleLock) {
                    resetCounters()
                    thresholdResetTriggered = false
                    prefs.edit().putBoolean("threshold_reset_triggered", false).apply()
                    rateSamples.clear()
                    previousCharging = true
                }
            }
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
                // delay() uses CLOCK_MONOTONIC (uptimeMillis): it ticks every 1s while the CPU is Awake
                // (updating Awake & discharge live) and automatically pauses in hardware during Deep Sleep.
                delay(interval)
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
        val rates = BatteryStatsCalculator.calculateWindowed(rateSamples.toList(), sample, charging)

        val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        val (onElapsed, offElapsed, sleepElapsed, awakeElapsed) = updateAndMeasureScreenTime(interactive)
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsed = if (previousSampleElapsedRealtime > 0L) {
            (nowElapsed - previousSampleElapsedRealtime).coerceAtLeast(0L)
        } else 0L
        val trackedElapsed = onElapsed + offElapsed

        val oldLevel = previousLevel
        val newLevel = sample.levelPercent
        val resetAtThreshold = preferences.safeBoolean("reset_at_threshold", true)
        val threshold = preferences.safeInt("reset_threshold_percent", 100).coerceIn(50, 100)
        if (newLevel == null || newLevel < threshold) {
            if (thresholdResetTriggered) {
                thresholdResetTriggered = false
                preferences.edit().putBoolean("threshold_reset_triggered", false).apply()
            }
        } else if (resetAtThreshold && !thresholdResetTriggered) {
            resetCounters()
            thresholdResetTriggered = true
            preferences.edit().putBoolean("threshold_reset_triggered", true).apply()
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

        if (charging == false && (elapsed > 0L || trackedElapsed > 0L)) {
            onMillisSinceLevelChange += onElapsed
            offMillisSinceLevelChange += offElapsed
            sleepMillisSinceLevelChange += sleepElapsed
            awakeMillisSinceLevelChange += awakeElapsed

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

            val rawOnLoss = if (onElapsed > 0L) {
                val activeMa = currentMa ?: 300.0
                (activeMa * (onElapsed / 3_600_000.0) / capacityMah * 100.0)
                    .takeIf { it.isFinite() && it in 0.0..5.0 } ?: 0.0
            } else 0.0

            val totalOffSoFar = synchronized(screenStateLock) { screenOffMillis }
            val (rawSleepLoss, rawAwakeLoss) = if (offElapsed > 0L) {
                val sleepMa = ((currentMa ?: 180.0) * 0.08).coerceIn(12.0, 28.0)
                // Use live fuel-gauge current during Awake; only clamp the first 2 seconds after screen-off
                // to prevent screen-on backlight current from spiking a 1-second screen lock.
                val maxAwakeMa = if (totalOffSoFar < 2_500L) 220.0 else 1_200.0
                val awakeMa = (currentMa ?: 180.0).coerceIn(45.0, maxAwakeMa)

                val calcSleep = (sleepMa * (sleepElapsed / 3_600_000.0) / capacityMah * 100.0)
                    .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val calcAwake = (awakeMa * (awakeElapsed / 3_600_000.0) / capacityMah * 100.0)
                    .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val calcTotalOff = calcSleep + calcAwake

                val counterDeltaUah = if (previousCounter != null && currentCounter != null) {
                    previousCounter - currentCounter
                } else null
                val maxPlausibleOffLoss = (offElapsed / 3_600_000.0 * 35.0).coerceIn(0.01, 5.0)
                val counterOffLoss = if (
                    onElapsed < 2_000L &&
                    offElapsed >= 15_000L &&
                    counterDeltaUah != null &&
                    counterDeltaUah in 1L..100_000L
                ) {
                    (counterDeltaUah.toDouble() / capacityUah.toDouble() * 100.0)
                        .takeIf { it.isFinite() && it in 0.0..maxPlausibleOffLoss }
                } else null

                if (counterOffLoss != null && calcTotalOff > 0.0) {
                    val sleepRatio = (calcSleep / calcTotalOff).coerceIn(0.0, 1.0)
                    val sLoss = counterOffLoss * sleepRatio
                    Pair(sLoss, (counterOffLoss - sLoss).coerceAtLeast(0.0))
                } else {
                    Pair(calcSleep, calcAwake)
                }
            } else Pair(0.0, 0.0)

            val rawOffLoss = rawSleepLoss + rawAwakeLoss
            val rawTickLoss = rawOnLoss + rawOffLoss

            if (rawTickLoss > 0.0) {
                activeDrainPercent += rawOnLoss
                idleDrainPercent += rawOffLoss
                deepSleepDrainPercent += rawSleepLoss
                awakeDrainPercent += rawAwakeLoss
                subPercentSinceLevelChange += rawTickLoss
                synchronized(screenStateLock) {
                    if (openSessionActive) {
                        openSubPercentDrop += rawTickLoss
                    }
                }
            }

            val levelDropped = oldLevel != null && newLevel != null && newLevel < oldLevel && (oldLevel - newLevel) in 1..10
            if (levelDropped) {
                val intDrop = (oldLevel!! - newLevel!!).toDouble()
                if (subPercentSinceLevelChange < intDrop) {
                    val shortfall = (intDrop - subPercentSinceLevelChange).coerceAtLeast(0.0)
                    val windowTotalMs = (onMillisSinceLevelChange + offMillisSinceLevelChange).coerceAtLeast(1L)
                    val onShare = if (onMillisSinceLevelChange + offMillisSinceLevelChange > 0L) {
                        onMillisSinceLevelChange.toDouble() / windowTotalMs.toDouble()
                    } else if (trackedElapsed > 0L) {
                        onElapsed.toDouble() / trackedElapsed.toDouble()
                    } else 1.0
                    val extraOnLoss = (shortfall * onShare).coerceIn(0.0, shortfall)
                    val extraOffLoss = (shortfall - extraOnLoss).coerceAtLeast(0.0)
                    activeDrainPercent += extraOnLoss
                    idleDrainPercent += extraOffLoss
                    if (extraOffLoss > 0.0) {
                        val sleepWeight = sleepMillisSinceLevelChange.toDouble()
                        val awakeWeight = awakeMillisSinceLevelChange.toDouble() * 6.0
                        val totalWeight = (sleepWeight + awakeWeight).coerceAtLeast(1.0)
                        val extraSleepLoss = (extraOffLoss * (sleepWeight / totalWeight)).coerceIn(0.0, extraOffLoss)
                        val extraAwakeLoss = (extraOffLoss - extraSleepLoss).coerceAtLeast(0.0)
                        deepSleepDrainPercent += extraSleepLoss
                        awakeDrainPercent += extraAwakeLoss
                    }
                    synchronized(screenStateLock) {
                        if (openSessionActive) {
                            openSubPercentDrop += shortfall
                        }
                    }
                    subPercentSinceLevelChange = 0.0
                } else {
                    subPercentSinceLevelChange = (subPercentSinceLevelChange - intDrop).coerceIn(0.0, 0.8)
                }
                onMillisSinceLevelChange = 0L
                offMillisSinceLevelChange = 0L
                sleepMillisSinceLevelChange = 0L
                awakeMillisSinceLevelChange = 0L
            }
        } else if (oldLevel != null && newLevel != null && newLevel > oldLevel) {
            subPercentSinceLevelChange = 0.0
            onMillisSinceLevelChange = 0L
            offMillisSinceLevelChange = 0L
            sleepMillisSinceLevelChange = 0L
            awakeMillisSinceLevelChange = 0L
        }
        previousChargeCounterMicroAmpHours = sample.chargeCounterMicroAmpHours

        val session = computeSessionTelemetry(charging, sample, rates)
        mutableState.value = MonitorState(sample, rates, session)

        val levelChanged = newLevel != null && newLevel != lastNotifiedBatteryLevel
        val chargingChanged = charging != lastNotifiedCharging
        val timerExpired = (nowElapsed - lastNotificationUpdateElapsedMs) >= NOTIFICATION_THROTTLE_MS
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
        if (forceNotificationUpdate || levelChanged || chargingChanged || (nowElapsed - lastPersistElapsedMs) >= PERSIST_THROTTLE_MS) {
            persistRateSamples()
            persistCounters()
            lastPersistElapsedMs = nowElapsed
        }
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

    private fun updateScreenState(interactive: Boolean, @Suppress("UNUSED_PARAMETER") now: Long = 0L) {
        synchronized(screenStateLock) {
            updateScreenStateLocked(
                interactive = interactive,
                nowElapsed = SystemClock.elapsedRealtime(),
                nowUptime = SystemClock.uptimeMillis()
            )
        }
    }

    private fun updateAndMeasureScreenTime(interactive: Boolean): Quad =
        synchronized(screenStateLock) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowUptime = SystemClock.uptimeMillis()
            when (screenStateInteractive) {
                null -> {
                    screenStateInteractive = interactive
                    screenStateSinceElapsed = nowElapsed
                    screenStateSinceUptime = nowUptime
                }
                interactive -> accumulateScreenTimeLocked(nowElapsed, nowUptime)
                else -> updateScreenStateLocked(interactive, nowElapsed, nowUptime)
            }
            val onDelta = screenOnSinceSampleMillis
            val offDelta = screenOffSinceSampleMillis
            val sleepDelta = deepSleepSinceSampleMillis
            val awakeDelta = awakeSinceSampleMillis
            screenOnSinceSampleMillis = 0L
            screenOffSinceSampleMillis = 0L
            deepSleepSinceSampleMillis = 0L
            awakeSinceSampleMillis = 0L
            Quad(onDelta, offDelta, sleepDelta, awakeDelta)
        }

    private fun updateScreenStateLocked(interactive: Boolean, nowElapsed: Long, nowUptime: Long) {
        accumulateScreenTimeLocked(nowElapsed, nowUptime)
        screenStateInteractive = interactive
        screenStateSinceElapsed = nowElapsed
        screenStateSinceUptime = nowUptime
    }

    private fun accumulateScreenTime() {
        synchronized(screenStateLock) {
            accumulateScreenTimeLocked(
                nowElapsed = SystemClock.elapsedRealtime(),
                nowUptime = SystemClock.uptimeMillis()
            )
        }
    }

    /**
     * Strictly isolates Screen-On vs Screen-Off time and tracks Deep Sleep vs Awake
     * ONLY during Screen-Off periods using Android's monotonic clocks:
     * - elapsedRealtime() advances continuously including Deep Sleep (system suspend).
     * - uptimeMillis() advances ONLY when the CPU is Awake and pauses during Deep Sleep.
     */
    private fun accumulateScreenTimeLocked(nowElapsed: Long, nowUptime: Long) {
        val sinceElapsed = screenStateSinceElapsed
        val sinceUptime = screenStateSinceUptime
        if (sinceElapsed <= 0L || nowElapsed <= sinceElapsed) {
            if (sinceElapsed <= 0L) {
                screenStateSinceElapsed = nowElapsed
                screenStateSinceUptime = nowUptime
            }
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
                val rawUpDelta = if (sinceUptime > 0L && nowUptime >= sinceUptime) {
                    (nowUptime - sinceUptime).coerceIn(0L, elapsed)
                } else 0L
                val rawSleepDelta = (elapsed - rawUpDelta).coerceAtLeast(0L)
                // System suspend-to-RAM takes >=50ms; sub-50ms differences on a 1s tick are clock rounding jitter
                val effectiveSleepDelta = if (rawSleepDelta < 50L) 0L else rawSleepDelta
                val effectiveAwakeDelta = (elapsed - effectiveSleepDelta).coerceAtLeast(0L)

                deepSleepMillis += effectiveSleepDelta
                awakeMillis += effectiveAwakeDelta
                deepSleepSinceSampleMillis += effectiveSleepDelta
                awakeSinceSampleMillis += effectiveAwakeDelta
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
            if (out.isEmpty() || item.timestampMillis - out.last().timestampMillis >= 1_000L) out.add(item)
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
        val currentLevel = readCurrentBatteryLevel() ?: previousLevel
        val interactive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        synchronized(screenStateLock) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowUptime = SystemClock.uptimeMillis()
            accumulateScreenTimeLocked(nowElapsed, nowUptime)
            screenOnMillis = 0L
            screenOffMillis = 0L
            awakeMillis = 0L
            deepSleepMillis = 0L
            screenOnSinceSampleMillis = 0L
            screenOffSinceSampleMillis = 0L
            deepSleepSinceSampleMillis = 0L
            awakeSinceSampleMillis = 0L
            screenStateInteractive = interactive
            screenStateSinceElapsed = nowElapsed
            screenStateSinceUptime = nowUptime
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
        deepSleepDrainPercent = 0.0
        awakeDrainPercent = 0.0
        activeDrainMillis = 0L
        idleDrainMillis = 0L
        subPercentSinceLevelChange = 0.0
        onMillisSinceLevelChange = 0L
        offMillisSinceLevelChange = 0L
        sleepMillisSinceLevelChange = 0L
        awakeMillisSinceLevelChange = 0L
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
        val (shownOnMillis, shownOffMillis, rawAwakeMillis, rawSleepMillis) = synchronized(screenStateLock) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowUptime = SystemClock.uptimeMillis()
            val extraScreen = if (screenStateSinceElapsed > 0L) {
                (nowElapsed - screenStateSinceElapsed).coerceAtLeast(0L)
            } else 0L
            val onMs = if (screenStateInteractive == true) screenOnMillis + extraScreen else screenOnMillis
            val offMs = if (screenStateInteractive == false) screenOffMillis + extraScreen else screenOffMillis

            val (extraAwake, extraSleep) = if (screenStateInteractive == false && extraScreen > 0L) {
                val rawUpDelta = if (screenStateSinceUptime > 0L && nowUptime >= screenStateSinceUptime) {
                    (nowUptime - screenStateSinceUptime).coerceIn(0L, extraScreen)
                } else 0L
                val rawSlpDelta = (extraScreen - rawUpDelta).coerceAtLeast(0L)
                val slpDelta = if (rawSlpDelta < 50L) 0L else rawSlpDelta
                val awkDelta = (extraScreen - slpDelta).coerceAtLeast(0L)
                Pair(awkDelta, slpDelta)
            } else {
                Pair(0L, 0L)
            }
            Quad(onMs, offMs, awakeMillis + extraAwake, deepSleepMillis + extraSleep)
        }
        val nowElapsed = SystemClock.elapsedRealtime()

        // Synchronize displayed seconds so Deep Sleep + Awake ALWAYS equals Screen Off to the exact second
        val totalOffSec = (shownOffMillis / 1000L).coerceAtLeast(0L)
        val awakeSec = if (totalOffSec > 0L) {
            ((rawAwakeMillis + 500L) / 1000L).coerceIn(0L, totalOffSec)
        } else 0L
        val sleepSec = (totalOffSec - awakeSec).coerceAtLeast(0L)
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
            if (lastRollingQueryElapsedMs == 0L || (nowElapsed - lastRollingQueryElapsedMs) >= ROLLING_QUERY_THROTTLE_MS) {
                cachedRolling24h = sessionDb.queryRolling24hSummary()
                lastRollingQueryElapsedMs = nowElapsed
            }
            cachedRolling24h
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

        val activeLoss = activeDrainPercent.coerceAtLeast(0.0)
        val idleLoss = idleDrainPercent.coerceAtLeast(0.0)

        val continuousActiveRate = if (shownOnMillis >= 2_000L && activeLoss > 0.0) {
            ratePerHourNumeric(activeLoss, shownOnMillis, charging)
        } else 0.0

        val continuousIdleRate = if (shownOffMillis >= 2_000L && idleLoss > 0.0) {
            val rawRate = ratePerHourNumeric(idleLoss, shownOffMillis, charging)
            if (shownOffMillis < 15_000L) rawRate.coerceAtMost(4.9) else rawRate
        } else 0.0

        val activeRateVal = when {
            charging == true -> 0.0
            screenStateInteractive == true && liveInstantRate != null -> {
                if (continuousActiveRate > 0.0 && shownOnMillis >= 30_000L) {
                    (continuousActiveRate * 0.70 + liveInstantRate * 0.30).coerceIn(0.0, 200.0)
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
            shownOffMillis < 2_000L -> 0.0
            continuousIdleRate > 0.0 -> continuousIdleRate
            combined24hIdleMs >= BatterySessionDbHelper.MIN_VALID_SESSION_MS -> rolling24hIdleRateVal
            else -> 0.0
        }

        val activeRate = formatRatePerHourUI(activeRateVal)
        val idleRate = formatRatePerHourUI(idleRateVal)

        val rawSleepLoss = deepSleepDrainPercent.coerceAtLeast(0.0)
        val rawAwakeLoss = awakeDrainPercent.coerceAtLeast(0.0)
        val rawIdleSubTotal = rawSleepLoss + rawAwakeLoss
        val (deepSleepLoss, awakeLoss) = when {
            idleLoss <= 0.0 || totalOffSec <= 0L -> Pair(0.0, 0.0)
            sleepSec <= 0L -> Pair(0.0, idleLoss)
            awakeSec <= 0L -> Pair(idleLoss, 0.0)
            rawIdleSubTotal > 0.0 -> {
                val scaledSleep = (idleLoss * (rawSleepLoss / rawIdleSubTotal)).coerceIn(0.0, idleLoss)
                Pair(scaledSleep, (idleLoss - scaledSleep).coerceAtLeast(0.0))
            }
            else -> {
                val sleepWeight = sleepSec.toDouble()
                val awakeWeight = awakeSec.toDouble() * 6.0
                val totalWeight = (sleepWeight + awakeWeight).coerceAtLeast(1.0)
                val scaledSleep = (idleLoss * (sleepWeight / totalWeight)).coerceIn(0.0, idleLoss)
                Pair(scaledSleep, (idleLoss - scaledSleep).coerceAtLeast(0.0))
            }
        }
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

        return SessionTelemetry(
            activeDrainRate = activeRate,
            idleDrainRate = idleRate,
            activeDrainRateValue = activeRateVal,
            idleDrainRateValue = idleRateVal,
            activeTimeDropText = formatTimeDrop(shownOnMillis, activeLoss),
            idleTimeDropText = formatTimeDrop(shownOffMillis, idleLoss),
            sessionTimeDropText = formatTimeDrop(totalSessionMillis, totalLoss),
            awakeTimeDropText = formatTimeDrop(shownAwakeMillis, awakeLoss),
            deepSleepTimeDropText = formatTimeDrop(shownSleepMillis, deepSleepLoss),
            activeLossPercent = activeLoss,
            idleLossPercent = idleLoss,
            deepSleepLossPercent = deepSleepLoss,
            awakeLossPercent = awakeLoss,
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
        val levelInt = sample?.levelPercent
        val temperatureText = sample?.temperatureTenthsCelsius?.let {
            val celsius = it / 10.0
            val value = if (temperatureUnit == "Fahrenheit (°F)") celsius * 9.0 / 5.0 + 32.0 else celsius
            val unit = if (temperatureUnit == "Fahrenheit (°F)") "°F" else "°C"
            String.format(Locale.getDefault(), "%.0f%s", value, unit)
        }
        val showCurrent = if (charging == true) showChargingPower else true
        val currentMa = if (showCurrent) {
            (rates?.currentMilliAmpsMagnitude
                ?: sample?.currentMilliAmpsSigned?.let { abs(it) }
                    ?.takeIf { it.isFinite() && it in 1.0..20_000.0 })
                ?.roundToInt()
        } else null

        val chargingWatts = if (charging == true && showChargingPower) {
            rates?.powerWattsMagnitude?.takeIf { it.isFinite() && it > 0.05 }
                ?: if (currentMa != null && sample?.voltageMilliVolts != null && sample.voltageMilliVolts > 0) {
                    (currentMa / 1000.0) * (sample.voltageMilliVolts / 1000.0)
                } else null
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
            if (chargingWatts != null && chargingWatts > 0.05) {
                append(String.format(Locale.getDefault(), " (%.1f W)", chargingWatts))
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

        val screenOnDropFormatted = formatDropPercent(tele.activeLossPercent)
        val screenOffDropFormatted = formatDropPercent(tele.idleLossPercent)
        val deepSleepDropFormatted = formatDropPercent(tele.deepSleepLossPercent)
        val awakeDropFormatted = formatDropPercent(tele.awakeLossPercent)

        val screenOnLine = String.format(
            Locale.getDefault(),
            "Screen on: %s (%s)",
            formatDuration(tele.screenOnMillis),
            screenOnDropFormatted
        )
        val screenOffLine = String.format(
            Locale.getDefault(),
            "Screen off: %s (%s)",
            formatDuration(tele.screenOffMillis),
            screenOffDropFormatted
        )
        val deepSleepLine = String.format(
            Locale.getDefault(),
            "Deep sleep: %s (%s)",
            formatDuration(tele.deepSleepMillis),
            deepSleepDropFormatted
        )
        val awakeLine = String.format(
            Locale.getDefault(),
            "Awake: %s (%s)",
            formatDuration(tele.awakeMillis),
            awakeDropFormatted
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
            headerLine = headerLine,
            rateLine = rateLine,
            screenOnLine = screenOnLine,
            screenOffLine = screenOffLine,
            deepSleepLine = deepSleepLine,
            awakeLine = awakeLine,
            bodyText = bodyLines
        )
    }

    private fun getOrCreateBatteryPercentIcon(level: Int): IconCompat {
        val clamped = level.coerceIn(0, 100)
        val existing = cachedBatteryPercentIcon
        if (cachedIconLevel == clamped && existing != null) {
            return existing
        }
        val sizePx = 96
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val text = clamped.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = when {
                text.length >= 3 -> 52f
                text.length == 2 -> 68f
                else -> 74f
            }
        }
        val fontMetrics = paint.fontMetrics
        val centerY = (sizePx / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, sizePx / 2f, centerY, paint)
        val icon = IconCompat.createWithBitmap(bitmap)
        cachedIconLevel = clamped
        cachedBatteryPercentIcon = icon
        return icon
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
        val idleWarningText = if (
            idleWarningEnabled &&
            tele.screenOffMillis >= 60_000L &&
            tele.idleLossPercent >= 0.05 &&
            tele.idleDrainRateValue > idleWarningLimit
        ) {
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

        val currentLevel = sample?.levelPercent
        val builder = if (showBatteryPercentage && currentLevel != null) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(getOrCreateBatteryPercentIcon(currentLevel))
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sysbattery_status)
        }

        return builder
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

    private fun readSystemBootCount(): Int = runCatching {
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    private fun restoreCounters() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.safeInt("counters_schema_v", 0) < COUNTERS_SCHEMA_VERSION) {
            resetStoredCounters(applicationContext, notifyRunningService = false)
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val savedSinceElapsed = prefs.safeLong("screen_state_since_elapsed", 0L)
        val currentBootCount = readSystemBootCount()
        val savedBootCount = prefs.safeInt("last_boot_count", -1)
        val rebootDetected = (savedBootCount != -1 && currentBootCount != -1 && currentBootCount != savedBootCount) ||
            (savedSinceElapsed > 0L && savedSinceElapsed > nowElapsed)
        if (rebootDetected && prefs.safeBoolean("reset_on_reboot", true)) {
            resetStoredCounters(applicationContext, notifyRunningService = false)
            if (currentBootCount != -1) {
                prefs.edit().putInt("last_boot_count", currentBootCount).apply()
            }
            return
        }

        thresholdResetTriggered = prefs.safeBoolean("threshold_reset_triggered", false)
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
        deepSleepDrainPercent = prefs.safeDouble("deep_sleep_drain_percent", 0.0)
        awakeDrainPercent = prefs.safeDouble("awake_drain_percent", 0.0)
        activeDrainMillis = prefs.safeLong("active_drain_ms", 0L)
        idleDrainMillis = prefs.safeLong("idle_drain_ms", 0L)
        subPercentSinceLevelChange = prefs.safeDouble("sub_percent_since_lvl", 0.0)
        onMillisSinceLevelChange = prefs.safeLong("on_ms_since_lvl", 0L)
        offMillisSinceLevelChange = prefs.safeLong("off_ms_since_lvl", 0L)
        sleepMillisSinceLevelChange = prefs.safeLong("sleep_ms_since_lvl", 0L)
        awakeMillisSinceLevelChange = prefs.safeLong("awake_ms_since_lvl", 0L)

        val nowUptime = SystemClock.uptimeMillis()
        val hasSavedScreenState = prefs.safeBoolean("screen_state_has_saved", false)
        val savedScreenInteractive = prefs.safeBoolean("screen_state_interactive", true)
        val savedSinceUptime = prefs.safeLong("screen_state_since_uptime", 0L)

        val savedOpenActive = prefs.safeBoolean("open_session_active", false)
        val savedOpenStartMs = prefs.safeLong("open_start_elapsed_ms", 0L)
        val savedOpenStartLvl = prefs.safeInt("open_start_battery_level", -1)
        val savedOpenSubDrop = prefs.safeDouble("open_sub_percent_drop", 0.0)
        val savedOpenType = BatterySessionDbHelper.SessionType.fromDb(
            prefs.safeString("open_session_type", "ACTIVE")
        )
        synchronized(screenStateLock) {
            if (hasSavedScreenState && savedSinceElapsed in 1L..nowElapsed && savedSinceUptime in 1L..nowUptime) {
                screenStateInteractive = savedScreenInteractive
                screenStateSinceElapsed = savedSinceElapsed
                screenStateSinceUptime = savedSinceUptime
            }
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
        val (totals, openSnap, stateSnap) = synchronized(screenStateLock) {
            Triple(
                Quad(screenOnMillis, screenOffMillis, awakeMillis, deepSleepMillis),
                OpenSessionSnapshot(
                    openSessionActive,
                    openSessionType,
                    openStartElapsedMs,
                    openStartBatteryLevel,
                    openSubPercentDrop
                ),
                Triple(screenStateInteractive, screenStateSinceElapsed, screenStateSinceUptime)
            )
        }
        val currentBootCount = readSystemBootCount()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("counters_schema_v", COUNTERS_SCHEMA_VERSION)
            .apply {
                if (currentBootCount != -1) {
                    putInt("last_boot_count", currentBootCount)
                }
            }
            .putBoolean("threshold_reset_triggered", thresholdResetTriggered)
            .putLong("screen_on_ms", totals.a)
            .putLong("screen_off_ms", totals.b)
            .putLong("awake_ms", totals.c)
            .putLong("deep_sleep_ms", totals.d)
            .putBoolean("screen_state_has_saved", stateSnap.first != null && stateSnap.second > 0L)
            .putBoolean("screen_state_interactive", stateSnap.first ?: true)
            .putLong("screen_state_since_elapsed", stateSnap.second)
            .putLong("screen_state_since_uptime", stateSnap.third)
            .putFloat("active_drain_percent", activeDrainPercent.toFloat())
            .putFloat("idle_drain_percent", idleDrainPercent.toFloat())
            .putFloat("deep_sleep_drain_percent", deepSleepDrainPercent.toFloat())
            .putFloat("awake_drain_percent", awakeDrainPercent.toFloat())
            .putLong("active_drain_ms", activeDrainMillis)
            .putLong("idle_drain_ms", idleDrainMillis)
            .putFloat("sub_percent_since_lvl", subPercentSinceLevelChange.toFloat())
            .putLong("on_ms_since_lvl", onMillisSinceLevelChange)
            .putLong("off_ms_since_lvl", offMillisSinceLevelChange)
            .putLong("sleep_ms_since_lvl", sleepMillisSinceLevelChange)
            .putLong("awake_ms_since_lvl", awakeMillisSinceLevelChange)
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
