package com.nlab.sysbattery.data

import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import com.nlab.sysbattery.data.waitForProcessExit
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
        val rooted = identityResult.exitCode == 0 && hasRootUid(identity)
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
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(2_048)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        val remaining = MAX_OUTPUT - output.length
                        if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                        // Drain excess output so the process cannot block on a full pipe.
                    }
                    output.toString()
                }
            }
            Thread(outputTask, "SysBatteryRootOutput").apply { isDaemon = true; start() }
            val exitCode = waitForProcessExit(process, TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?: run {
                    outputTask.cancel(true)
                    return CommandResult(-1, "")
                }
            val output = try {
                outputTask.get(2, TimeUnit.SECONDS)
            } catch (_: Exception) {
                ""
            }
            CommandResult(exitCode, output)
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


private val ROOT_UID_PATTERN = Regex("""(?:^|\s)uid=0(?:\s|\(|$)""")

internal fun hasRootUid(identity: String): Boolean = ROOT_UID_PATTERN.containsMatchIn(identity)
