package com.yahyafati.mnemo.core.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import com.yahyafati.mnemo.core.data.R

/**
 * The ongoing notification that keeps a long import, export or backup alive when the user leaves
 * the app. Without notification permission the work still runs; the notification just isn't shown.
 */
internal object TransferNotifications {
    private const val CHANNEL = "transfers"

    fun foregroundInfo(context: Context, notificationId: Int, @StringRes title: Int, progress: Float?): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.core_data_transfer_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.core_data_ic_transfer)
            .setContentTitle(context.getString(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PROGRESS_MAX, ((progress ?: 0f) * PROGRESS_MAX).toInt(), progress == null)
            .build()
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private const val PROGRESS_MAX = 100
}

/**
 * Promotes the worker to a foreground service, if the system allows it right now (it doesn't when
 * the app is in the background on Android 12+); the work continues either way.
 */
internal suspend fun CoroutineWorker.tryForeground(notificationId: Int, @StringRes title: Int, progress: Float?) {
    try {
        setForeground(TransferNotifications.foregroundInfo(applicationContext, notificationId, title, progress))
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException is an IllegalStateException.
    } catch (e: SecurityException) {
        // Missing foreground-service permission in an unusual build; run in the background.
    }
}
