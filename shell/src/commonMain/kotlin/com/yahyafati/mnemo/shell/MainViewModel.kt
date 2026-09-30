package com.yahyafati.mnemo.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState

    /** [showOnboarding]: first run, nothing in the collection yet. */
    data class Ready(val settings: UserSettings, val showOnboarding: Boolean = false) : MainUiState
}

/** Somewhere the app should open once the shell is up: from a notification, the widget, or onboarding. */
sealed interface AppDestination {
    /** The Study tab (Daily Mix). */
    data object Study : AppDestination

    /** The editor, adding cards to [deckId]. */
    data class AddCards(val deckId: String) : AppDestination
}

/** App-wide state: appearance (theme, dynamic color, card text size), onboarding, and where to open. */
class MainViewModel(
    private val settingsRepository: UserSettingsRepository,
    private val deckRepository: DeckRepository,
    private val reminderRepository: ReminderRepository,
    private val transferRepository: DataTransferRepository,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(settingsRepository.settings, deckRepository.observeDecks()) { settings, decks ->
        // Someone with decks (an update from before onboarding existed, or a restored backup) skips it.
        MainUiState.Ready(settings, showOnboarding = !settings.onboardingCompleted && decks.isEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState.Loading)

    private val _destination = MutableStateFlow<AppDestination?>(null)

    /** Where to go next; the shell navigates, then calls [destinationHandled]. */
    val destination: StateFlow<AppDestination?> = _destination.asStateFlow()

    fun open(destination: AppDestination) {
        _destination.value = destination
    }

    fun destinationHandled() {
        _destination.value = null
    }

    /**
     * Finishes onboarding: creates the first deck named [deckName] (and opens the editor on it),
     * and turns on the daily reminder if asked.
     */
    fun finishOnboarding(deckName: String?, reminder: Boolean) {
        viewModelScope.launch {
            val name = deckName?.trim()?.takeIf { it.isNotEmpty() }
            if (name != null) _destination.value = AppDestination.AddCards(deckRepository.saveDeck(name))
            if (reminder) reminderRepository.setReminder(settingsRepository.settings.first().reminder.copy(enabled = true))
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /** Onboarding's "Import from Anki": starts the import, which the Decks screen then shows. */
    fun importFromOnboarding(uri: String, reminder: Boolean) {
        transferRepository.startImport(uri)
        finishOnboarding(deckName = null, reminder = reminder)
    }
}
