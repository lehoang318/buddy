package com.example.buddy.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.buddy.data.EventLog

private const val TAG = "Connectivity"

class ConnectivityWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ConnectivityWorker"
    }

    override fun doWork(): Result {
        return try {
            EventLog.info(TAG, "Background check started")

            val runtime = Runtime.getRuntime()
            val process = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
            val exitValue = process.waitFor()

            if (exitValue == 0) {
                EventLog.info(TAG, "Background check OK")
                Result.success()
            } else {
                EventLog.warning(TAG, "Network unreachable", "exit=$exitValue")
                Result.retry()
            }
        } catch (e: Exception) {
            EventLog.error(TAG, "Background check failed", e.message)
            Result.retry()
        }
    }
}