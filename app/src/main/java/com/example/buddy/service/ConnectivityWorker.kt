package com.example.buddy.service

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.buddy.data.EventLog

class ConnectivityWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ConnectivityWorker"
    }

    override fun doWork(): Result {
        return try {
            // Log event for debugging
            EventLog.add("I", "Running background connectivity check")
            Log.d(TAG, "Performing background connectivity check...")
            
            // Basic connectivity check - just verify we can reach Google DNS (8.8.8.8)
            // This is a simple way to check if network is available without depending on app-specific services
            val reachable = checkNetworkConnectivity()
            
            if (reachable) {
                EventLog.add("I", "Background connectivity check: OK")
                Log.d(TAG, "Connectivity check passed")
                Result.success()
            } else {
                EventLog.add("W", "Background connectivity check: Network unreachable")
                Log.w(TAG, "Connectivity check failed - network unreachable")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ConnectivityWorker", e)
            EventLog.add("E", "ConnectivityWorker error: ${e.message}")
            Result.failure()
        }
    }
    
    private fun checkNetworkConnectivity(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            false
        }
    }
}