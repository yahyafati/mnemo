package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration

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
}
