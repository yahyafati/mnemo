package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.ReminderSettings

/** The daily study reminder (Settings › Reminders): stores the choice and keeps the notification scheduled. */
interface ReminderRepository {
    /** Saves [settings], then schedules the next reminder, or cancels it when turned off. */
    suspend fun setReminder(settings: ReminderSettings)

    /** Schedules the next reminder from the stored settings: at app start, and after a restore. */
    suspend fun reschedule()
}
