package com.example.buddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.buddy.MainActivity
import com.example.buddy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BuddyForegroundService : Service() {

    enum class OperationStatus {
        IDLE,
        LLM_STREAMING,
        WEB_SEARCHING,
        URL_FETCHING
    }

    companion object {
        const val ACTION_START = "com.example.buddy.START_FOREGROUND"
        const val ACTION_STOP = "com.example.buddy.STOP_FOREGROUND"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "buddy_foreground_channel"
        const val TAG = "BuddyForegroundService"

        private var serviceInstance: BuddyForegroundService? = null

        fun updateStatus(status: OperationStatus, detail: String) {
            serviceInstance?.updateNotificationInternal(
                status.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                detail
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startService()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }

    private fun startService() {
        Log.d(TAG, "Starting foreground service...")
        
        serviceInstance = this

        createNotificationChannel()
        val notification = buildNotification("Buddy is starting...", "")

        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "Foreground service started successfully")
    }

    private fun stopService() {
        Log.d(TAG, "Stopping foreground service...")

        serviceInstance = null
        serviceScope.cancel()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Foreground service stopped")
    }

    private fun updateNotificationInternal(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Buddy Connectivity",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background connectivity service for LLM and web search"
            setSound(null, null)
            setShowBadge(false)
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}