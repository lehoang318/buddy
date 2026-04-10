package com.example.buddy.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundScheduler {

    private const val CONNECTIVITY_WORKER_TAG = "connectivity_worker"

    fun scheduleConnectivityChecks(context: Context) {
        // Build periodic work request
        val connectivityWork = PeriodicWorkRequestBuilder<ConnectivityWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        // Enqueue work
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                CONNECTIVITY_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                connectivityWork
            )
    }

    fun cancelConnectivityChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CONNECTIVITY_WORKER_TAG)
    }
}