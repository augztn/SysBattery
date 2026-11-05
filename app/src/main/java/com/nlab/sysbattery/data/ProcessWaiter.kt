package com.nlab.sysbattery.data

import android.os.Build
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Waits for a child process without relying on Process.waitFor(timeout), which is API 26+.
 * Returns the exit code, or null when the timeout expires.
 */
internal fun waitForProcessExit(
    process: Process,
    timeout: Long,
    unit: TimeUnit
): Int? {
    require(timeout > 0L) { "Process timeout must be positive" }
    val waitTask = FutureTask { process.waitFor() }
    Thread(waitTask, "SysBatteryProcessWait").apply {
        isDaemon = true
        start()
    }
    return try {
        waitTask.get(timeout, unit)
    } catch (_: TimeoutException) {
        terminateProcess(process)
        waitTask.cancel(true)
        null
    }
}

/** Terminates a process on every supported API, using forceful termination where available. */
internal fun terminateProcess(process: Process) {
    process.destroy()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        process.destroyForcibly()
    }
}
