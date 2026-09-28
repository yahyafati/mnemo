package com.yahyafati.mnemo.core.model

import java.time.Duration

enum class CardFontSize(val scale: Float) {
    Small(0.9f),
    Medium(1f),
    Large(1.2f),
}

data class UserSettings(
    /** Target probability of recall when a card comes due. */
    val desiredRetention: Double = 0.9,
    val newCardsPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FollowSystem,
    val useDynamicColor: Boolean = false,
    val cardFontSize: CardFontSize = CardFontSize.Medium,
)
