package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.model.ReminderSettings

/** Stores the reminder in [settings], like the real one, and counts reschedules instead of using WorkManager. */
class FakeReminderRepository(private val settings: FakeUserSettingsRepository) : ReminderRepository {
    var reschedules = 0
        private set

    override suspend fun setReminder(settings: ReminderSettings) {
        this.settings.setReminder(settings)
        reschedules++
    }

    override suspend fun reschedule() {
        reschedules++
    }
}
