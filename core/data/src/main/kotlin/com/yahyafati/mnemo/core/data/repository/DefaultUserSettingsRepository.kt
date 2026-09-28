package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import javax.inject.Inject

internal class DefaultUserSettingsRepository @Inject constructor(
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
}
