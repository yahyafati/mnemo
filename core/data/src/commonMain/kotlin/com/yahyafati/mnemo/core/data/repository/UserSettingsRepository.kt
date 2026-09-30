package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

interface UserSettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setDesiredRetention(value: Double)

    suspend fun setNewCardsPerDay(value: Int)

    suspend fun setReviewsPerDay(value: Int)

    suspend fun setLearningSteps(steps: List<Duration>)

    suspend fun setRelearningSteps(steps: List<Duration>)

    suspend fun setDarkThemeConfig(value: DarkThemeConfig)

    suspend fun setUseDynamicColor(value: Boolean)

    suspend fun setCardFontSize(value: CardFontSize)

    /** Turns automatic backups on or off; [folderUri] is the backup folder (a SAF tree). */
    suspend fun setAutoBackup(enabled: Boolean, folderUri: String?)

    suspend fun setLastBackupAt(value: Instant)

    /** Schedules with [weights] from now on, or with the FSRS-6 defaults when null. */
    suspend fun setFsrsWeights(weights: FsrsWeights?)

    /** Stores the reminder setting only; [ReminderRepository] schedules the notification. */
    suspend fun setReminder(value: ReminderSettings)

    suspend fun setAutoPlayAudio(value: Boolean)

    suspend fun setOnboardingCompleted(value: Boolean)
}
