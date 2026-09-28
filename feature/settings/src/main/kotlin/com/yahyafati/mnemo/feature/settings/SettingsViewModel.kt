package com.yahyafati.mnemo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(val settings: UserSettings) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map<UserSettings, SettingsUiState> { SettingsUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun setDesiredRetention(value: Double) = launch { setDesiredRetention(Math.round(value * 100) / 100.0) }

    fun setNewCardsPerDay(value: Int) = launch { setNewCardsPerDay(value.coerceIn(0, MAX_PER_DAY)) }

    fun setReviewsPerDay(value: Int) = launch { setReviewsPerDay(value.coerceIn(0, MAX_PER_DAY)) }

    /** Returns false (and saves nothing) if [text] isn't a valid list of steps. */
    fun setLearningSteps(text: String): Boolean = saveSteps(text) { setLearningSteps(it) }

    fun setRelearningSteps(text: String): Boolean = saveSteps(text) { setRelearningSteps(it) }

    fun setDarkThemeConfig(value: DarkThemeConfig) = launch { setDarkThemeConfig(value) }

    fun setUseDynamicColor(value: Boolean) = launch { setUseDynamicColor(value) }

    fun setCardFontSize(value: CardFontSize) = launch { setCardFontSize(value) }

    private fun saveSteps(text: String, save: suspend UserSettingsRepository.(List<Duration>) -> Unit): Boolean {
        val steps = StepsFormat.parse(text) ?: return false
        launch { save(steps) }
        return true
    }

    private fun launch(block: suspend UserSettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }

    companion object {
        const val MAX_PER_DAY = 9_999
    }
}
