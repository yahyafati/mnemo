package com.yahyafati.mnemo.core.model

import java.time.Duration
import java.time.Instant

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
