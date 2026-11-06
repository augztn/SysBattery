package com.nlab.sysbattery.data.shizuku

import android.os.RemoteException
import com.nlab.sysbattery.data.terminateProcess
import com.nlab.sysbattery.data.waitForProcessExit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Shizuku User Service executes fixed, read-only BatteryStats queries.
 * No caller-provided command strings are accepted.
 */
class BatteryStatsUserService : IBatteryStatsUserService.Stub() {
    @Throws(RemoteException::class)
    override fun readChargedBatteryStats(): String = readBatteryStats("--charged")

    @Throws(RemoteException::class)
    override fun readBatteryStatsHistory(): String = readBatteryStats("--history")

    @Throws(RemoteException::class)
    override fun readAlarmStats(): String = readSystemDump("alarm")

    @Throws(RemoteException::class)
    override fun readJobSchedulerStats(): String = readSystemDump("jobscheduler")

    private fun readBatteryStats(mode: String): String {
        require(mode == "--charged" || mode == "--history") { "Unsupported BatteryStats query" }
        return executeReadOnlyCommand(listOf("dumpsys", "batterystats", mode), "BatteryStats $mode")
    }

    private fun readSystemDump(serviceName: String): String {
        require(serviceName == "alarm" || serviceName == "jobscheduler") { "Unsupported system dump" }
        return executeReadOnlyCommand(listOf("dumpsys", serviceName), serviceName)
    }

    private fun executeReadOnlyCommand(command: List<String>, description: String): String {
        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return "ERROR: ${e.javaClass.simpleName}: ${e.message ?: "Could not start $description query"}"
        }

        val outputTask = FutureTask {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(2_048)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    val remaining = MAX_OUTPUT - output.length
                    if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                    // Drain excess output so the child process cannot block on a full pipe.
                }
                output.toString()
            }
        }
        Thread(outputTask, "SysBatteryShizukuOutput").apply {
            isDaemon = true
            start()
        }

        return try {
            val exitCode = waitForProcessExit(process, TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (exitCode == null) {
                terminateProcess(process)
                outputTask.cancel(true)
                "ERROR: $description command timed out."
            } else {
                val output = outputTask.get(2, TimeUnit.SECONDS)
                if (exitCode != 0) {
                    "ERROR: $description command exited $exitCode: ${output.take(500)}"
                } else {
                    output
                }
            }
        } catch (e: Exception) {
            terminateProcess(process)
            outputTask.cancel(true)
            "ERROR: ${e.javaClass.simpleName}: ${e.message ?: "$description query failed"}"
        }
    }

    private companion object {
        const val MAX_OUTPUT = 48_000
        const val TIMEOUT_SECONDS = 12L
    }
}
