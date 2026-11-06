package com.nlab.sysbattery.data

import com.nlab.sysbattery.data.shizuku.IBatteryStatsUserService

/**
 * Parses BatteryStats gathered by the optional, authorized Shizuku User Service.
 */
class ShizukuBatteryStatsReader(
    private val service: IBatteryStatsUserService
) {
    data class Report(
        val findings: List<RootBatteryStatsParser.Finding>,
        val uidPowerEstimates: List<RootBatteryStatsParser.UidPowerEstimate>
    )

    fun read(): Report {
        val chargedOutput = service.readChargedBatteryStats()
        if (chargedOutput.startsWith("ERROR:")) {
            throw IllegalStateException(chargedOutput.removePrefix("ERROR:").trim())
        }
        val historyOutput = runCatching { service.readBatteryStatsHistory() }
            .getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
        val historyFindings = if (historyOutput.startsWith("ERROR:")) {
            listOf(RootBatteryStatsParser.Finding("History unavailable", historyOutput.removePrefix("ERROR:").trim()))
        } else {
            RootBatteryStatsParser.parse(historyOutput, maxFindings = 100)
        }
        val alarmOutput = runCatching { service.readAlarmStats() }
            .getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
        val jobOutput = runCatching { service.readJobSchedulerStats() }
            .getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
        val schedulerFindings = buildList {
            if (alarmOutput.startsWith("ERROR:")) {
                add(RootBatteryStatsParser.Finding("Alarm data unavailable", alarmOutput.removePrefix("ERROR:").trim()))
            } else addAll(RootBatteryStatsParser.parseSchedulerDump(alarmOutput, "Alarm", 30))
            if (jobOutput.startsWith("ERROR:")) {
                add(RootBatteryStatsParser.Finding("Job data unavailable", jobOutput.removePrefix("ERROR:").trim()))
            } else addAll(RootBatteryStatsParser.parseSchedulerDump(jobOutput, "Job", 30))
        }
        val findings = (
            RootBatteryStatsParser.parse(chargedOutput, maxFindings = 100) + historyFindings + schedulerFindings
            ).distinctBy { "${it.label}:${it.value}" }.take(220)
        return Report(
            findings = findings,
            uidPowerEstimates = RootBatteryStatsParser.parseUidPowerEstimates(chargedOutput)
        )
    }
}
