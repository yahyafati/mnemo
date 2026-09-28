package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration

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
}
