package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

class FakeUserSettingsRepository(initial: UserSettings = UserSettings()) : UserSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setDesiredRetention(value: Double) = settings.update { it.copy(desiredRetention = value) }

    override suspend fun setNewCardsPerDay(value: Int) = settings.update { it.copy(newCardsPerDay = value) }

    override suspend fun setReviewsPerDay(value: Int) = settings.update { it.copy(reviewsPerDay = value) }

    override suspend fun setLearningSteps(steps: List<Duration>) = settings.update { it.copy(learningSteps = steps) }

    override suspend fun setRelearningSteps(steps: List<Duration>) = settings.update { it.copy(relearningSteps = steps) }

    override suspend fun setDarkThemeConfig(value: DarkThemeConfig) = settings.update { it.copy(darkThemeConfig = value) }

    override suspend fun setUseDynamicColor(value: Boolean) = settings.update { it.copy(useDynamicColor = value) }

    override suspend fun setCardFontSize(value: CardFontSize) = settings.update { it.copy(cardFontSize = value) }

    override suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) =
        settings.update { it.copy(backup = it.backup.copy(autoBackupEnabled = enabled, folderUri = folderUri)) }

    override suspend fun setLastBackupAt(value: Instant) = settings.update { it.copy(backup = it.backup.copy(lastBackupAt = value)) }

    override suspend fun setFsrsWeights(weights: FsrsWeights?) = settings.update { it.copy(fsrsWeights = weights) }

    override suspend fun setReminder(value: ReminderSettings) = settings.update { it.copy(reminder = value) }

    override suspend fun setAutoPlayAudio(value: Boolean) = settings.update { it.copy(autoPlayAudio = value) }

    override suspend fun setOnboardingCompleted(value: Boolean) = settings.update { it.copy(onboardingCompleted = value) }
}
