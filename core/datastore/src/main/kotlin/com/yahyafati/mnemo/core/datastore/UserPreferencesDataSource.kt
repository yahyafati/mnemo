package com.yahyafati.mnemo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

/**
 * User settings in Preferences DataStore. A missing key means "default", so defaults can change in
 * code without a migration. Unknown enum names (from a newer app version) also fall back.
 */
class UserPreferencesDataSource @Inject constructor(
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
        )
    }

    suspend fun setDesiredRetention(value: Double) = edit { it[Keys.DesiredRetention] = value }

    suspend fun setNewCardsPerDay(value: Int) = edit { it[Keys.NewCardsPerDay] = value }

    suspend fun setReviewsPerDay(value: Int) = edit { it[Keys.ReviewsPerDay] = value }

    suspend fun setLearningSteps(steps: List<Duration>) = edit { it[Keys.LearningSteps] = encodeSteps(steps) }

    suspend fun setRelearningSteps(steps: List<Duration>) = edit { it[Keys.RelearningSteps] = encodeSteps(steps) }

    suspend fun setDarkThemeConfig(value: DarkThemeConfig) = edit { it[Keys.DarkTheme] = value.name }

    suspend fun setUseDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }

    suspend fun setCardFontSize(value: CardFontSize) = edit { it[Keys.CardFontSize] = value.name }

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
    }

    private companion object {
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
