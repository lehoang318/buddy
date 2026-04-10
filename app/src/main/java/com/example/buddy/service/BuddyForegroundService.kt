package com.example.buddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.buddy.MainActivity
import com.example.buddy.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BuddyForegroundService : Service() {

    enum class OperationStatus {
        IDLE,
        LLM_STREAMING,
        WEB_SEARCHING,
        URL_FETCHING
    }

    enum class OperationType {
        LLM_STREAM,
        WEB_SEARCH,
        URL_FETCH
    }

    companion object {
        const val ACTION_START = "com.example.buddy.START_FOREGROUND"
        const val ACTION_STOP = "com.example.buddy.STOP_FOREGROUND"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "buddy_foreground_channel"
        const val TAG = "BuddyForegroundService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _operationStatus = MutableStateFlow(OperationStatus.IDLE)
        val operationStatus: StateFlow<OperationStatus> = _operationStatus

        private val _statusDetail = MutableStateFlow("")
        val statusDetail: StateFlow<String> = _statusDetail

        private var serviceInstance: BuddyForegroundService? = null

        fun updateStatus(status: OperationStatus, detail: String) {
            _operationStatus.value = status
            _statusDetail.value = detail
            serviceInstance?.updateNotificationInternal(
                status.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                detail
            )
        }
    }

    private val pendingRetries = ConcurrentHashMap<String, RetryInfo>()
    private val retryCounter = AtomicInteger(0)

    data class RetryInfo(
        val operationType: OperationType,
        var retryCount: Int = 0,
        val maxRetries: Int = 3,
        var nextRetryTime: Long = 0
    )

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val retryHandler = Handler(Looper.getMainLooper())
    private val pendingRetryRunnables = ConcurrentHashMap<String, Runnable>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startService()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }

    private fun startService() {
        if (_isRunning.value) {
            Log.d(TAG, "Service already running")
            return
        }

        Log.d(TAG, "Starting foreground service...")
        
        serviceInstance = this

        createNotificationChannel()
        val notification = buildNotification("Buddy is starting...", "")

        startForeground(NOTIFICATION_ID, notification)
        _isRunning.value = true
        _operationStatus.value = OperationStatus.IDLE

        Log.d(TAG, "Foreground service started successfully")
    }

    private fun stopService() {
        Log.d(TAG, "Stopping foreground service...")

        pendingRetryRunnables.values.forEach { retryHandler.removeCallbacks(it) }
        pendingRetryRunnables.clear()
        pendingRetries.clear()

        _isRunning.value = false
        _operationStatus.value = OperationStatus.IDLE
        _statusDetail.value = ""
        
        serviceInstance = null

        retryHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Foreground service stopped")
    }

    private fun updateNotificationInternal(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Buddy Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background connectivity service for LLM and web search"
                setSound(null, null)
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val stopIntent = Intent(this, BuddyForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.avatar)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun calculateBackoffDelay(retryAttempt: Int): Long {
        val baseDelay = 1000L * (1 shl retryAttempt.coerceAtMost(4))
        val jitter = (baseDelay * 0.2 * Math.random()).toLong()
        return (baseDelay + jitter).coerceAtMost(30000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pendingRetryRunnables.values.forEach { retryHandler.removeCallbacks(it) }
        pendingRetryRunnables.clear()
        pendingRetries.clear()
        _isRunning.value = false
        serviceScope.cancel()
    }
}