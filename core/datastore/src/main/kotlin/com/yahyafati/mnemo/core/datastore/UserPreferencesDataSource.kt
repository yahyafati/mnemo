package com.yahyafati.mnemo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yahyafati.mnemo.core.model.BackupSettings
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * User settings in Preferences DataStore. A missing key means "default", so defaults can change in
 * code without a migration. Unknown enum names (from a newer app version) also fall back.
 */
class UserPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            desiredRetention = prefs[Keys.DesiredRetention] ?: defaults.desiredRetention,
            newCardsPerDay = prefs[Keys.NewCardsPerDay] ?: defaults.newCardsPerDay,
            reviewsPerDay = prefs[Keys.ReviewsPerDay] ?: defaults.reviewsPerDay,
            learningSteps = prefs[Keys.LearningSteps]?.let(::decodeSteps) ?: defaults.learningSteps,
            relearningSteps = prefs[Keys.RelearningSteps]?.let(::decodeSteps) ?: defaults.relearningSteps,
            darkThemeConfig = prefs[Keys.DarkTheme].toEnum(defaults.darkThemeConfig),
            useDynamicColor = prefs[Keys.DynamicColor] ?: defaults.useDynamicColor,
            cardFontSize = prefs[Keys.CardFontSize].toEnum(defaults.cardFontSize),
            backup = BackupSettings(
                autoBackupEnabled = prefs[Keys.AutoBackup] ?: defaults.backup.autoBackupEnabled,
                folderUri = prefs[Keys.BackupFolder],
                keepCount = prefs[Keys.BackupKeepCount] ?: defaults.backup.keepCount,
                lastBackupAt = prefs[Keys.LastBackupAt]?.let(Instant::ofEpochMilli),
            ),
            fsrsWeights = readFsrsWeights(prefs),
            reminder = ReminderSettings(
                enabled = prefs[Keys.ReminderEnabled] ?: defaults.reminder.enabled,
                time = prefs[Keys.ReminderMinute]?.takeIf { it in 0 until MINUTES_PER_DAY }
                    ?.let { LocalTime.of(it / 60, it % 60) } ?: defaults.reminder.time,
            ),
            autoPlayAudio = prefs[Keys.AutoPlayAudio] ?: defaults.autoPlayAudio,
            onboardingCompleted = prefs[Keys.OnboardingCompleted] ?: defaults.onboardingCompleted,
        )
    }

    suspend fun setReminder(value: ReminderSettings) = edit {
        it[Keys.ReminderEnabled] = value.enabled
        it[Keys.ReminderMinute] = value.time.hour * 60 + value.time.minute
    }

    suspend fun setAutoPlayAudio(value: Boolean) = edit { it[Keys.AutoPlayAudio] = value }

    suspend fun setOnboardingCompleted(value: Boolean) = edit { it[Keys.OnboardingCompleted] = value }

    suspend fun setDesiredRetention(value: Double) = edit { it[Keys.DesiredRetention] = value }

    suspend fun setNewCardsPerDay(value: Int) = edit { it[Keys.NewCardsPerDay] = value }

    suspend fun setReviewsPerDay(value: Int) = edit { it[Keys.ReviewsPerDay] = value }

    suspend fun setLearningSteps(steps: List<Duration>) = edit { it[Keys.LearningSteps] = encodeSteps(steps) }

    suspend fun setRelearningSteps(steps: List<Duration>) = edit { it[Keys.RelearningSteps] = encodeSteps(steps) }

    suspend fun setDarkThemeConfig(value: DarkThemeConfig) = edit { it[Keys.DarkTheme] = value.name }

    suspend fun setUseDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }

    suspend fun setCardFontSize(value: CardFontSize) = edit { it[Keys.CardFontSize] = value.name }

    suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) = edit {
        it[Keys.AutoBackup] = enabled
        if (folderUri == null) it.remove(Keys.BackupFolder) else it[Keys.BackupFolder] = folderUri
    }

    suspend fun setLastBackupAt(value: Instant) = edit { it[Keys.LastBackupAt] = value.toEpochMilli() }

    /** Stores fitted FSRS weights, or goes back to the defaults with null. */
    suspend fun setFsrsWeights(value: FsrsWeights?) = edit {
        if (value == null) {
            it.remove(Keys.FsrsWeightValues)
            it.remove(Keys.FsrsOptimizedAt)
            it.remove(Keys.FsrsTrainingReviews)
            it.remove(Keys.FsrsPreviousLoss)
            it.remove(Keys.FsrsLoss)
        } else {
            it[Keys.FsrsWeightValues] = value.values.joinToString(",")
            it[Keys.FsrsOptimizedAt] = value.optimizedAt.toEpochMilli()
            it[Keys.FsrsTrainingReviews] = value.trainingReviews
            it[Keys.FsrsPreviousLoss] = value.previousLoss
            it[Keys.FsrsLoss] = value.loss
        }
    }

    // Weights that don't parse, or have the wrong count, fall back to the defaults.
    private fun readFsrsWeights(prefs: Preferences): FsrsWeights? {
        val values = prefs[Keys.FsrsWeightValues]?.split(',')?.map { it.toDoubleOrNull() ?: return null } ?: return null
        if (values.size != FSRS_WEIGHT_COUNT) return null
        return FsrsWeights(
            values = values,
            optimizedAt = Instant.ofEpochMilli(prefs[Keys.FsrsOptimizedAt] ?: 0),
            trainingReviews = prefs[Keys.FsrsTrainingReviews] ?: 0,
            previousLoss = prefs[Keys.FsrsPreviousLoss] ?: Double.NaN,
            loss = prefs[Keys.FsrsLoss] ?: Double.NaN,
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val DesiredRetention = doublePreferencesKey("desired_retention")
        val NewCardsPerDay = intPreferencesKey("new_cards_per_day")
        val ReviewsPerDay = intPreferencesKey("reviews_per_day")
        val LearningSteps = stringPreferencesKey("learning_steps_seconds")
        val RelearningSteps = stringPreferencesKey("relearning_steps_seconds")
        val DarkTheme = stringPreferencesKey("dark_theme_config")
        val DynamicColor = booleanPreferencesKey("use_dynamic_color")
        val CardFontSize = stringPreferencesKey("card_font_size")
        val AutoBackup = booleanPreferencesKey("auto_backup")
        val BackupFolder = stringPreferencesKey("backup_folder_uri")
        val BackupKeepCount = intPreferencesKey("backup_keep_count")
        val LastBackupAt = longPreferencesKey("last_backup_at")
        val FsrsWeightValues = stringPreferencesKey("fsrs_weights")
        val FsrsOptimizedAt = longPreferencesKey("fsrs_optimized_at")
        val FsrsTrainingReviews = intPreferencesKey("fsrs_training_reviews")
        val FsrsPreviousLoss = doublePreferencesKey("fsrs_previous_loss")
        val FsrsLoss = doublePreferencesKey("fsrs_loss")
        val ReminderEnabled = booleanPreferencesKey("reminder_enabled")
        val ReminderMinute = intPreferencesKey("reminder_minute_of_day")
        val AutoPlayAudio = booleanPreferencesKey("auto_play_audio")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    private companion object {
        const val FSRS_WEIGHT_COUNT = 21
        const val MINUTES_PER_DAY = 24 * 60

        // Steps are stored as comma-separated seconds; an empty string is "no steps".
        fun encodeSteps(steps: List<Duration>): String = steps.joinToString(",") { it.seconds.toString() }

        fun decodeSteps(value: String): List<Duration>? = if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(',').map { it.toLongOrNull()?.let(Duration::ofSeconds) ?: return null }
        }

        inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
            enumValues<E>().firstOrNull { it.name == this } ?: default
    }
}
