package com.nlab.sysbattery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import kotlin.math.max

/**
 * Thread-safe singleton SQLite relational store for state-driven battery discharge sessions
 * and 24-hour duration-weighted rolling aggregations.
 */
class BatterySessionDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    enum class SessionType(val dbValue: String) {
        ACTIVE("ACTIVE"),
        IDLE("IDLE");

        companion object {
            fun fromDb(value: String?): SessionType =
                if (value.equals("IDLE", ignoreCase = true)) IDLE else ACTIVE
        }
    }

    data class SessionRecord(
        val id: Long,
        val sessionType: SessionType,
        val startTimestampElapsedMs: Long,
        val endTimestampElapsedMs: Long,
        val startBatteryLevel: Int,
        val endBatteryLevel: Int,
        val calculatedRatePerHour: Double
    ) {
        val durationMillis: Long
            get() = (endTimestampElapsedMs - startTimestampElapsedMs).coerceAtLeast(0L)

        val formattedDuration: String
            get() {
                val totalSeconds = durationMillis / 1000L
                val hours = totalSeconds / 3600L
                val minutes = (totalSeconds % 3600L) / 60L
                val seconds = totalSeconds % 60L
                return when {
                    hours > 0L -> "${hours}h ${minutes}m"
                    minutes > 0L -> "${minutes}m ${seconds}s"
                    else -> "${seconds}s"
                }
            }
    }

    data class RollingAggregate(
        val totalDropPercent: Double = 0.0,
        val totalDurationMillis: Long = 0L
    )

    data class Rolling24hSummary(
        val active: RollingAggregate = RollingAggregate(),
        val idle: RollingAggregate = RollingAggregate(),
        val recentSessions: List<SessionRecord> = emptyList()
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_TYPE TEXT NOT NULL,
                $COL_START_TS INTEGER NOT NULL,
                $COL_END_TS INTEGER NOT NULL,
                $COL_RECORDED_EPOCH INTEGER NOT NULL,
                $COL_START_LEVEL INTEGER NOT NULL,
                $COL_END_LEVEL INTEGER NOT NULL,
                $COL_DROP_PERCENT REAL NOT NULL,
                $COL_RATE_PER_HOUR REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sessions_type_epoch ON $TABLE_SESSIONS($COL_SESSION_TYPE, $COL_RECORDED_EPOCH DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    /**
     * Validates and inserts a completed state session (ACTIVE or IDLE) when the screen toggles.
     * Returns true if the session passed validation guard rails and was persisted.
     */
    @Synchronized
    fun recordCompletedSession(
        sessionType: SessionType,
        startElapsedMs: Long,
        endElapsedMs: Long,
        startBatteryLevel: Int,
        endBatteryLevel: Int,
        measuredSubPercentDrop: Double? = null,
        recordedEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val elapsedMs = endElapsedMs - startElapsedMs
        if (elapsedMs < MIN_VALID_SESSION_MS) {
            return false
        }

        val integerDrop = startBatteryLevel - endBatteryLevel
        if (integerDrop < 0) {
            return false
        }

        val effectiveDrop = when {
            measuredSubPercentDrop != null && measuredSubPercentDrop.isFinite() && measuredSubPercentDrop > 0.0 -> {
                val minBound = (integerDrop - 0.99).coerceAtLeast(0.0)
                val maxBound = integerDrop + 0.99
                measuredSubPercentDrop.coerceIn(minBound, maxBound)
            }
            integerDrop > 0 -> integerDrop.toDouble()
            else -> 0.0
        }

        val ratePerHour = calculateSessionRatePerHour(
            startBatteryPercent = startBatteryLevel.toDouble(),
            endBatteryPercent = startBatteryLevel.toDouble() - effectiveDrop,
            elapsedMillis = elapsedMs
        ) ?: return false

        return runCatching {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SESSION_TYPE, sessionType.dbValue)
                put(COL_START_TS, startElapsedMs)
                put(COL_END_TS, endElapsedMs)
                put(COL_RECORDED_EPOCH, recordedEpochMs)
                put(COL_START_LEVEL, startBatteryLevel.coerceIn(0, 100))
                put(COL_END_LEVEL, endBatteryLevel.coerceIn(0, 100))
                put(COL_DROP_PERCENT, effectiveDrop)
                put(COL_RATE_PER_HOUR, ratePerHour)
            }
            val rowId = db.insert(TABLE_SESSIONS, null, values)
            pruneOldSessions(db, recordedEpochMs)
            rowId != -1L
        }.getOrElse { e ->
            DiagnosticLogger.error("Failed to insert battery session", e)
            false
        }
    }

    @Synchronized
    fun queryRolling24hSummary(
        nowEpochMs: Long = System.currentTimeMillis(),
        recentLimit: Int = 20
    ): Rolling24hSummary {
        val cutoffEpochMs = nowEpochMs - WINDOW_24_HOURS_MS
        var activeDrop = 0.0
        var activeDuration = 0L
        var idleDrop = 0.0
        var idleDuration = 0L
        val recent = mutableListOf<SessionRecord>()

        runCatching {
            val db = readableDatabase
            db.rawQuery(
                """
                SELECT $COL_SESSION_TYPE,
                       SUM($COL_DROP_PERCENT),
                       SUM($COL_END_TS - $COL_START_TS)
                FROM $TABLE_SESSIONS
                WHERE $COL_RECORDED_EPOCH >= ?
                GROUP BY $COL_SESSION_TYPE
                """.trimIndent(),
                arrayOf(cutoffEpochMs.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val type = SessionType.fromDb(cursor.getString(0))
                    val drop = cursor.getDouble(1).coerceAtLeast(0.0)
                    val duration = cursor.getLong(2).coerceAtLeast(0L)
                    if (type == SessionType.ACTIVE) {
                        activeDrop = drop
                        activeDuration = duration
                    } else {
                        idleDrop = drop
                        idleDuration = duration
                    }
                }
            }

            db.query(
                TABLE_SESSIONS,
                null,
                null,
                null,
                null,
                null,
                "$COL_ID DESC",
                recentLimit.toString()
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
                val typeIdx = cursor.getColumnIndexOrThrow(COL_SESSION_TYPE)
                val startTsIdx = cursor.getColumnIndexOrThrow(COL_START_TS)
                val endTsIdx = cursor.getColumnIndexOrThrow(COL_END_TS)
                val startLvlIdx = cursor.getColumnIndexOrThrow(COL_START_LEVEL)
                val endLvlIdx = cursor.getColumnIndexOrThrow(COL_END_LEVEL)
                val rateIdx = cursor.getColumnIndexOrThrow(COL_RATE_PER_HOUR)

                while (cursor.moveToNext()) {
                    recent.add(
                        SessionRecord(
                            id = cursor.getLong(idIdx),
                            sessionType = SessionType.fromDb(cursor.getString(typeIdx)),
                            startTimestampElapsedMs = cursor.getLong(startTsIdx),
                            endTimestampElapsedMs = cursor.getLong(endTsIdx),
                            startBatteryLevel = cursor.getInt(startLvlIdx),
                            endBatteryLevel = cursor.getInt(endLvlIdx),
                            calculatedRatePerHour = cursor.getDouble(rateIdx)
                        )
                    )
                }
            }
        }.onFailure { e ->
            DiagnosticLogger.error("Failed to query 24h rolling session summary", e)
        }

        return Rolling24hSummary(
            active = RollingAggregate(activeDrop, activeDuration),
            idle = RollingAggregate(idleDrop, idleDuration),
            recentSessions = recent
        )
    }

    @Synchronized
    fun clearAllSessions() {
        runCatching {
            writableDatabase.delete(TABLE_SESSIONS, null, null)
        }.onFailure { e ->
            DiagnosticLogger.error("Failed to clear battery session database", e)
        }
    }

    private fun pruneOldSessions(db: SQLiteDatabase, nowEpochMs: Long) {
        val retentionCutoff = nowEpochMs - RETENTION_7_DAYS_MS
        db.delete(TABLE_SESSIONS, "$COL_RECORDED_EPOCH < ?", arrayOf(retentionCutoff.toString()))
        db.execSQL(
            """
            DELETE FROM $TABLE_SESSIONS
            WHERE $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE_SESSIONS ORDER BY $COL_ID DESC LIMIT $MAX_STORED_SESSIONS
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val DATABASE_NAME = "sysbattery_sessions.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_SESSIONS = "battery_drain_sessions"
        const val COL_ID = "id"
        const val COL_SESSION_TYPE = "session_type"
        const val COL_START_TS = "start_timestamp"
        const val COL_END_TS = "end_timestamp"
        const val COL_RECORDED_EPOCH = "recorded_epoch_ms"
        const val COL_START_LEVEL = "start_battery_level"
        const val COL_END_LEVEL = "end_battery_level"
        const val COL_DROP_PERCENT = "drop_percent"
        const val COL_RATE_PER_HOUR = "calculated_rate_per_hour"

        const val MIN_VALID_SESSION_MS = 5_000L
        private const val WINDOW_24_HOURS_MS = 24L * 60L * 60L * 1000L
        private const val RETENTION_7_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MAX_STORED_SESSIONS = 250

        @Volatile
        private var instance: BatterySessionDbHelper? = null

        fun getInstance(context: Context): BatterySessionDbHelper =
            instance ?: synchronized(this) {
                instance ?: BatterySessionDbHelper(context.applicationContext).also { instance = it }
            }

        /**
         * Differential Drain Formula:
         * Drain Rate (%/h) = (Baseline Battery % - Current Battery %) / (Session Elapsed Ms / 3,600,000)
         */
        fun calculateSessionRatePerHour(
            startBatteryPercent: Double,
            endBatteryPercent: Double,
            elapsedMillis: Long
        ): Double? {
            if (elapsedMillis < MIN_VALID_SESSION_MS) return null
            val deltaPercent = startBatteryPercent - endBatteryPercent
            if (!deltaPercent.isFinite() || deltaPercent < -0.0001) return null
            val hours = elapsedMillis.toDouble() / 3_600_000.0
            if (hours <= 0.0) return null
            val rate = max(0.0, deltaPercent) / hours
            return rate.takeIf { it.isFinite() && it <= 500.0 }
        }
    }
}
