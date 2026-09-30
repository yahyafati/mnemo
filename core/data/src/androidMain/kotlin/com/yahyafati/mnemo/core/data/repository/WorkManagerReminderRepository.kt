package com.yahyafati.mnemo.core.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.DailyTime
import com.yahyafati.mnemo.core.data.work.ReminderWorker
import com.yahyafati.mnemo.core.model.ReminderSettings
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * One unique one-time work per reminder, delayed until the chosen time. Each run schedules the
 * next ([ReminderWorker]), so the reminder follows the local clock across DST changes instead of
 * drifting like a 24-hour periodic work would.
 */
internal class WorkManagerReminderRepository(
    private val workManager: WorkManager,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) : ReminderRepository {
    override suspend fun setReminder(settings: ReminderSettings) {
        settingsRepository.setReminder(settings)
        schedule(settings, ExistingWorkPolicy.REPLACE)
    }

    override suspend fun reschedule() {
        schedule(settingsRepository.settings.first().reminder, ExistingWorkPolicy.REPLACE)
    }

    /** Called by the worker once it has run: the next day's reminder, after this one finishes. */
    internal fun scheduleNext(settings: ReminderSettings) = schedule(settings, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun schedule(settings: ReminderSettings, policy: ExistingWorkPolicy) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(ReminderWorker.UNIQUE_NAME)
            return
        }
        val now = clock.now()
        val delay = Duration.between(now, DailyTime.nextAfter(now, clock.zone(), settings.time))
        workManager.enqueueUniqueWork(
            ReminderWorker.UNIQUE_NAME,
            policy,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay).addTag(ReminderWorker.UNIQUE_NAME).build(),
        )
    }
}
