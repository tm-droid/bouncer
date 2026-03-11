package com.tanush.bouncer.ui.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tanush.bouncer.R
import java.util.concurrent.TimeUnit

class UpdateCheckService(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val allowMetered = inputData.getBoolean(KEY_ALLOW_METERED, false)

        if (!allowMetered && isMeteredNetwork(applicationContext)) {
            return Result.retry()
        }

        val updateManager = AppUpdateManager(applicationContext)
        updateManager.setAllowMeteredChecks(allowMetered)

        return runCatching {
            val update = updateManager.checkForUpdates(silent = true)
            updateManager.updateLastCheckNow()
            if (update != null) {
                showUpdateBadgeNotification(update)
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun showUpdateBadgeNotification(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                return
            }
        }

        createChannelIfNeeded()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.update_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.update_notification_body,
                    info.newVersion
                )
            )
            .setNumber(1)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setSilent(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.update_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.update_notification_channel_desc)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun isMeteredNetwork(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return true

        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return !isWifi && !isEthernet
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bouncer_update_check_work"
        private const val KEY_ALLOW_METERED = "allow_metered"
        private const val CHANNEL_ID = "bouncer_update_channel"
        private const val NOTIFICATION_ID = 1432

        fun schedule(
            context: Context,
            frequency: UpdateCheckFrequency = UpdateCheckFrequency.DAILY,
            allowMetered: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckService>(
                frequency.intervalDays,
                TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_ALLOW_METERED to allowMetered))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

enum class UpdateCheckFrequency(val intervalDays: Long) {
    DAILY(1),
    WEEKLY(7)
}
