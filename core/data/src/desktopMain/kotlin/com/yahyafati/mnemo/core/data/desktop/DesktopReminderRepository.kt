package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.ReminderSettings

/**
 * The daily reminder is a phone feature (ROADMAP "Not on desktop"): the desktop keeps the choice,
 * so a backup restored from a phone round-trips it, but shows no notification.
 */
internal class DesktopReminderRepository(
    private val settingsRepository: UserSettingsRepository,
) : ReminderRepository {
    override suspend fun setReminder(settings: ReminderSettings) = settingsRepository.setReminder(settings)

    override suspend fun reschedule() = Unit
}
