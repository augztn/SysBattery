package com.nlab.sysbattery.data

import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Optional root backend. Executes a fixed, read-only command allowlist through su.
 */
class RootAccessReader {
    data class RootReport(
        val rooted: Boolean,
        val identity: String,
        val findings: List<RootBatteryStatsParser.Finding>
    )

    fun read(): RootReport {
        val identityResult = runRootCommand("id")
        val identity = identityResult.output.trim()
        val rooted = identityResult.exitCode == 0 &&
            (identity.contains("uid=0") || identity.contains("gid=0"))
        if (!rooted) {
            DiagnosticLogger.info("Root diagnostics unavailable: su missing or permission denied")
            return RootReport(
                rooted = false,
                identity = identity.ifBlank { "su unavailable or root denied" },
                findings = emptyList()
            )
        }
        DiagnosticLogger.info("Root diagnostics started")
        val battery = runRootCommand("dumpsys batterystats --charged | head -c 24000")
        val history = runRootCommand("dumpsys batterystats --history | head -c 12000")
        DiagnosticLogger.info("Root diagnostics completed; batteryOutputChars=${battery.output.length}")
        return RootReport(
            rooted = true,
            identity = identity,
            findings = (RootBatteryStatsParser.parse(battery.output) + RootBatteryStatsParser.parse(history.output))
                .distinctBy { "${it.label}:${it.value}" }
                .take(100)
        )
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun runRootCommand(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val outputTask = java.util.concurrent.FutureTask<String> {
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            }
            Thread(outputTask, "SysBatteryRootOutput").apply { isDaemon = true; start() }
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outputTask.cancel(true)
                return CommandResult(-1, "")
            }
            val output = try {
                outputTask.get(2, TimeUnit.SECONDS).take(MAX_OUTPUT)
            } catch (_: Exception) {
                ""
            }
            CommandResult(process.exitValue(), output)
        } catch (e: Exception) {
            DiagnosticLogger.warn("Root diagnostics command failed", e)
            CommandResult(-1, "")
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 12L
        private const val MAX_OUTPUT = 24_000
    }
}
