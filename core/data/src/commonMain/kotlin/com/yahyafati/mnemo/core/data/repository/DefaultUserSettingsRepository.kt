package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

internal class DefaultUserSettingsRepository(
    private val dataSource: UserPreferencesDataSource,
) : UserSettingsRepository {
    override val settings: Flow<UserSettings> = dataSource.settings

    override suspend fun setDesiredRetention(value: Double) = dataSource.setDesiredRetention(value)

    override suspend fun setNewCardsPerDay(value: Int) = dataSource.setNewCardsPerDay(value)

    override suspend fun setReviewsPerDay(value: Int) = dataSource.setReviewsPerDay(value)

    override suspend fun setLearningSteps(steps: List<Duration>) = dataSource.setLearningSteps(steps)

    override suspend fun setRelearningSteps(steps: List<Duration>) = dataSource.setRelearningSteps(steps)

    override suspend fun setDarkThemeConfig(value: DarkThemeConfig) = dataSource.setDarkThemeConfig(value)

    override suspend fun setUseDynamicColor(value: Boolean) = dataSource.setUseDynamicColor(value)

    override suspend fun setCardFontSize(value: CardFontSize) = dataSource.setCardFontSize(value)

    override suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) = dataSource.setAutoBackup(enabled, folderUri)

    override suspend fun setLastBackupAt(value: Instant) = dataSource.setLastBackupAt(value)

    override suspend fun setFsrsWeights(weights: FsrsWeights?) = dataSource.setFsrsWeights(weights)

    override suspend fun setReminder(value: ReminderSettings) = dataSource.setReminder(value)

    override suspend fun setAutoPlayAudio(value: Boolean) = dataSource.setAutoPlayAudio(value)

    override suspend fun setOnboardingCompleted(value: Boolean) = dataSource.setOnboardingCompleted(value)

    override suspend fun setPdfReadOptions(mode: PdfReadMode, quality: PdfQuality) = dataSource.setPdfReadOptions(mode, quality)

    override suspend fun acceptImageDisclosure(providerId: String) = dataSource.acceptImageDisclosure(providerId)
}
