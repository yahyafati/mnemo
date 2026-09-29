package com.yahyafati.mnemo.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

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
    val backup: BackupSettings = BackupSettings(),
    /** FSRS weights fitted to this user's reviews; null means the FSRS-6 defaults. */
    val fsrsWeights: FsrsWeights? = null,
    val reminder: ReminderSettings = ReminderSettings(),
    /** Play a card's sounds when its side appears (Settings › Study). */
    val autoPlayAudio: Boolean = true,
    /** The first-run introduction was finished or skipped. */
    val onboardingCompleted: Boolean = false,
)

/** The daily study reminder (Settings › Reminders). */
data class ReminderSettings(
    val enabled: Boolean = false,
    /** Local time of day the notification is posted. */
    val time: LocalTime = LocalTime.of(19, 0),
)

/** The 21 FSRS-6 weights the optimizer fitted to the review history (ADR 0007). */
data class FsrsWeights(
    val values: List<Double>,
    val optimizedAt: Instant,
    /** Reviews the fit was measured on. */
    val trainingReviews: Int,
    /** Mean log loss on those reviews of the weights used before, and of [values]. */
    val previousLoss: Double,
    val loss: Double,
)

/** Automatic backups (Settings › Data). */
data class BackupSettings(
    val autoBackupEnabled: Boolean = false,
    /** The Storage Access Framework tree the user picked for automatic backups. */
    val folderUri: String? = null,
    /** How many automatic backups to keep in [folderUri]; older ones are deleted. */
    val keepCount: Int = 7,
    val lastBackupAt: Instant? = null,
)
