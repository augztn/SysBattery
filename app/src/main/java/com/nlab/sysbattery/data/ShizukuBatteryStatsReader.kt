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
        val historyOutput = service.readBatteryStatsHistory()
        if (historyOutput.startsWith("ERROR:")) {
            throw IllegalStateException(historyOutput.removePrefix("ERROR:").trim())
        }
        val findings = (
            RootBatteryStatsParser.parse(chargedOutput, maxFindings = 100) +
                RootBatteryStatsParser.parse(historyOutput, maxFindings = 100)
            ).distinctBy { "${it.label}:${it.value}" }.take(160)
        return Report(
            findings = findings,
            uidPowerEstimates = RootBatteryStatsParser.parseUidPowerEstimates(chargedOutput)
        )
    }
}
