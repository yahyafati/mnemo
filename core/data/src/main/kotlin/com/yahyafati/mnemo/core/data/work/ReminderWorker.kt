package com.yahyafati.mnemo.core.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yahyafati.mnemo.core.common.intent.AppIntents
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.R
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerReminderRepository
import com.yahyafati.mnemo.core.database.dao.DeckDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * The daily study reminder. Posts a notification with today's card count, unless there is
 * nothing to study, then schedules tomorrow's. Counts come from the database directly: this runs
 * without the UI, and nothing leaves the device.
 */
@HiltWorker
internal class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deckDao: DeckDao,
    private val settingsRepository: UserSettingsRepository,
    private val reminders: WorkManagerReminderRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.reminder.enabled) return Result.success()
        val now = clock.now()
        val counts = deckDao.observeDeckCounts(now.toEpochMilli(), StudyDay.end(now, clock.zone()).toEpochMilli()).first()
        val due = counts.sumOf { it.reviewDue + it.learningDue }
        val new = counts.sumOf { it.newCount }.coerceAtMost(settings.newCardsPerDay)
        if (due + new > 0) notify(applicationContext, due + new)
        reminders.scheduleNext(settings.reminder)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily-reminder"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 4_100

        fun notify(context: Context, cards: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.core_data_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppIntents.EXTRA_OPEN, AppIntents.OPEN_STUDY)
            }
            val content = launch?.let {
                PendingIntent.getActivity(context, NOTIFICATION_ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.core_data_ic_reminder)
                .setContentTitle(context.getString(R.string.core_data_reminder_title))
                .setContentText(context.resources.getQuantityString(R.plurals.core_data_reminder_text, cards, cards))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked between the check and the post.
            }
        }
    }
}
