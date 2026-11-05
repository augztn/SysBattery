package com.nlab.sysbattery.data.shizuku

import android.os.RemoteException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Shizuku User Service executes this fixed read-only query as shell.
 * No caller-provided command strings are accepted.
 */
class BatteryStatsUserService : IBatteryStatsUserService.Stub() {
    @Throws(RemoteException::class)
    override fun readChargedBatteryStats(): String {
        val process = try {
            ProcessBuilder("dumpsys", "batterystats", "--charged")
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return "ERROR: ${e.javaClass.simpleName}: ${e.message ?: "Could not start BatteryStats query"}"
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
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                outputTask.cancel(true)
                "ERROR: BatteryStats command timed out."
            } else {
                val output = outputTask.get(2, TimeUnit.SECONDS)
                if (process.exitValue() != 0) {
                    "ERROR: dumpsys batterystats exited ${process.exitValue()}: ${output.take(500)}"
                } else {
                    output
                }
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            outputTask.cancel(true)
            "ERROR: ${e.javaClass.simpleName}: ${e.message ?: "BatteryStats query failed"}"
        }
    }

    private companion object {
        const val MAX_OUTPUT = 48_000
        const val TIMEOUT_SECONDS = 12L
    }
}
