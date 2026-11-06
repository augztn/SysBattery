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
        val findings = (
            RootBatteryStatsParser.parse(chargedOutput, maxFindings = 100) + historyFindings
            ).distinctBy { "${it.label}:${it.value}" }.take(160)
        return Report(
            findings = findings,
            uidPowerEstimates = RootBatteryStatsParser.parseUidPowerEstimates(chargedOutput)
        )
    }
}
