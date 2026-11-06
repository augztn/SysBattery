package com.nlab.sysbattery.data

/**
 * Best-effort parser for human-readable Android dumpsys batterystats output.
 * Output varies by Android/OEM release; findings preserve source text and are not treated as
 * direct electrical measurements. Unknown lines are intentionally ignored.
 */
object RootBatteryStatsParser {
    data class Finding(val label: String, val value: String)
    data class UidPowerEstimate(val uid: String, val powerMah: Double, val backgroundPowerMah: Double?)

    private val estimatedPower = Regex("""(?i)^\s*estimated power use(?:\s*\([^)]*\))?\s*:\s*(.+?)\s*$""")
    private val estimatedPowerSectionEnd = Regex("""(?i)^(?:battery history|statistics since|history|per-app|per application)\b.*$""")
    private val capacity = Regex("""(?i)^\s*(?:battery )?capacity\s*[:=]\s*(.+?)\s*$""")
    private val discharge = Regex("""(?i)^\s*(discharge(?:d)?(?:\s+since.*)?|start clock time)\s*[:=]\s*(.+?)\s*$""")
    private val screen = Regex("""(?i)^\s*(screen(?: on| off)? time|screen-on time|screen-off time|screen)\s*:\s*((?:on|off)\b|.*\b[0-9]+(?:\.[0-9]+)?\s*(?:d|h|m|s)\b.*)\s*$""")
    private val uidLine = Regex("""(?i)^\s*(uid\s+[^:]+)\s*:\s*(.+?)\s*$""")
    private val componentEstimate = Regex("""(?i)^\s*(screen|cpu|bluetooth|camera|audio|video|mobile_radio|sensors|gnss|wifi|wakelock|ambient_display)\s*:\s*([0-9]+(?:\.[0-9]+)?)(?:\s+(.*))?$""")
    private val uidLeadingEstimate = Regex("""(?i)^\s*(uid\s+[^:]+)\s*:\s*([0-9]+(?:\.[0-9]+)?)(?:\s+(.*))?$""")
    private val uidPower = Regex("""(?i)(?:^|[, (])(?:cpu|wifi|mobile radio|screen|camera|flashlight|bluetooth|gps|system|wake lock)\s*[:=]\s*([0-9]+(?:\.[0-9]+)?)\s*m?ah\b""")
    private val wakeLock = Regex("""(?i)^\s*(?:wake lock|wakelock|partial wakelock)\s*[:=]?\s*(.+?)\s*$""")
    private val historyWakeLock = Regex("""(?i)(?:\+wake_lock=|wake_lock=|\+wakelock=)([^,\s]+)""")
    private val historyScreen = Regex("""(?i)(?:\+screen=|screen=)(on|off)\b""")
    private val historyState = Regex("""(?i)(\+running|\-running|\+wake_lock|\-wake_lock|\+sensor|\-sensor)\b""")

    /** Extracts a bounded, read-only preview of alarm/job records from dumpsys output. */
    fun parseSchedulerDump(output: String, label: String, maxFindings: Int = 40): List<Finding> {
        if (output.isBlank() || maxFindings <= 0) return emptyList()
        val isAlarm = label.equals("Alarm", ignoreCase = true)
        val pattern = if (isAlarm) {
            Regex("""(?i)(alarm stats|wakeup|pending alarm|alarmclock|RTC_WAKEUP|ELAPSED_REALTIME_WAKEUP|com\\.[a-z0-9_.]+)""")
        } else {
            Regex("""(?i)(JOB #|JobStatus|jobstatus|scheduled jobs|running jobs|service:|com\\.[a-z0-9_.]+)""")
        }
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && pattern.containsMatchIn(it) }
            .filterNot { it.startsWith("ERROR:", ignoreCase = true) }
            .distinct()
            .take(maxFindings)
            .map { Finding(if (isAlarm) "Alarm activity" else "Scheduled job", it.take(220)) }
            .toList()
    }

    /** Returns Android's per-UID estimated energy rows from the Estimated power use section. */
    fun parseUidPowerEstimates(output: String, maxRows: Int = 200): List<UidPowerEstimate> {
        if (output.isBlank() || maxRows <= 0) return emptyList()
        val rows = linkedMapOf<String, UidPowerEstimate>()
        var inEstimatedPowerSection = false
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (estimatedPowerSectionEnd.matches(line)) inEstimatedPowerSection = false
            if (line.startsWith("Estimated power use", ignoreCase = true)) inEstimatedPowerSection = true
            if (!inEstimatedPowerSection) return@forEach
            val match = uidLeadingEstimate.find(line) ?: return@forEach
            val uid = match.groupValues[1].removePrefix("UID").trim()
            val powerMah = match.groupValues[2].toDoubleOrNull() ?: return@forEach
            val backgroundMah = Regex("""(?i)(?:^|\s)bg\s*[:=]\s*([0-9]+(?:\.[0-9]+)?)\b""")
                .find(match.groupValues[3])?.groupValues?.get(1)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.0..100_000.0 }
            if (uid.isNotBlank() && powerMah.isFinite() && powerMah in 0.0..100_000.0) {
                rows.putIfAbsent(uid, UidPowerEstimate(uid, powerMah, backgroundMah))
            }
        }
        return rows.values.take(maxRows)
    }

    fun parse(output: String, maxFindings: Int = 80): List<Finding> {
        if (output.isBlank() || maxFindings <= 0) return emptyList()
        val findings = linkedMapOf<String, Finding>()
        fun add(label: String, value: String) {
            val clean = value.trim().take(240)
            if (clean.isNotEmpty()) findings.putIfAbsent("$label:${clean.lowercase()}", Finding(label, clean))
        }

        var inEstimatedPowerSection = false
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (estimatedPowerSectionEnd.matches(line)) {
                inEstimatedPowerSection = false
            }
            if (line.startsWith("Estimated power use", ignoreCase = true)) {
                inEstimatedPowerSection = true
            }
            estimatedPower.find(line)?.let { add("Estimated power", it.groupValues[1]) }

            // The section heading supplies the mAh unit; component/UID rows often omit it.
            val componentEstimateMatch = if (inEstimatedPowerSection) componentEstimate.find(line) else null
            if (inEstimatedPowerSection) {
                componentEstimateMatch?.let {
                    add("Component estimate (mAh)", "${it.groupValues[1]}: ${it.groupValues[2]} (Android estimate)")
                }
                uidLeadingEstimate.find(line)?.let {
                    add("UID estimate (mAh)", "${it.groupValues[1]}: ${it.groupValues[2]} (Android estimate)")
                }
            }
            capacity.find(line)?.let { add("Battery capacity", it.groupValues[1]) }
            discharge.find(line)?.let { add("Discharge", "${it.groupValues[1]}: ${it.groupValues[2]}") }
            if (componentEstimateMatch == null) {
                screen.find(line)?.let { add("Screen time/state", "${it.groupValues[1]}: ${it.groupValues[2]}") }
            }

            val uid = uidLine.find(line)
            if (uid != null && (line.contains("mah", ignoreCase = true) || line.contains("power", ignoreCase = true))) {
                add("UID power estimate", "${uid.groupValues[1]}: ${uid.groupValues[2]}")
            }
            wakeLock.find(line)?.let { add("Wake lock", it.groupValues[1]) }
            historyWakeLock.find(line)?.let { add("History wake-lock event", it.groupValues[1]) }
            historyScreen.find(line)?.let { add("History screen state", it.groupValues[1].lowercase()) }
            if (historyState.containsMatchIn(line)) add("History event", line)
            if (line.contains("Estimated power use", ignoreCase = true)) {
                uidPower.findAll(line).forEach { add("Component power estimate", it.value.trim()) }
            }
        }
        return findings.values.take(maxFindings)
    }
}
