package com.example.buddy.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

object ServiceHelper {

    private const val TAG = "ServiceHelper"
    private const val COOLDOWN_MS = 30_000L // 30 seconds after last operation

    private val activeOperations = AtomicInteger(0)
    private var pendingStopHandler: Handler? = null
    private var pendingStopRunnable: Runnable? = null

    enum class OperationType {
        LLM_STREAM,
        WEB_SEARCH,
        URL_FETCH
    }

    fun onOperationStart(context: Context) {
        val count = activeOperations.incrementAndGet()
        Log.d(TAG, "Operation started. Active count: $count")
        
        // Cancel any pending service stop
        cancelPendingStop()
        
        if (count == 1) {
            // First operation starting - start foreground service
            Log.d(TAG, "Starting foreground service (first operation)")
            startForegroundService(context)
        }
    }

    fun onOperationEnd(context: Context) {
        val count = activeOperations.decrementAndGet()
        Log.d(TAG, "Operation ended. Active count: $count")
        
        if (count == 0) {
            // Last operation ended - schedule service stop after cooldown
            Log.d(TAG, "Scheduling service stop in ${COOLDOWN_MS}ms")
            scheduleServiceStop(context)
        } else if (count < 0) {
            activeOperations.set(0) // Prevent negative
        }
    }

    fun hasActiveOperations(): Boolean = activeOperations.get() > 0

    fun getActiveOperationCount(): Int = activeOperations.get()

    private fun startForegroundService(context: Context) {
        val intent = Intent(context, BuddyForegroundService::class.java).apply {
            action = BuddyForegroundService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun scheduleServiceStop(context: Context) {
        // Cancel any existing pending stop
        cancelPendingStop()
        
        // Create new handler and runnable for delayed stop
        pendingStopHandler = Handler(Looper.getMainLooper())
        pendingStopRunnable = Runnable {
            // Check again if operations started while waiting
            if (!hasActiveOperations()) {
                Log.d(TAG, "Cooldown expired, stopping foreground service")
                stopForegroundService(context)
            }
        }
        pendingStopHandler?.postDelayed(pendingStopRunnable!!, COOLDOWN_MS)
    }

    private fun cancelPendingStop() {
        pendingStopRunnable?.let { runnable ->
            pendingStopHandler?.removeCallbacks(runnable)
        }
        pendingStopHandler = null
        pendingStopRunnable = null
    }

    private fun stopForegroundService(context: Context) {
        val intent = Intent(context, BuddyForegroundService::class.java).apply {
            action = BuddyForegroundService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground service: ${e.message}")
        }
    }
}