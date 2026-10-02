package com.yahyafati.mnemo.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.feature.settings.RestoreStep
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

/** A short note the shell shows after something the user started from the menu or a dropped file. */
enum class ShellMessage {
    BackupDone,
    ExportDone,
    TransferFailed,
    UnsupportedFile,
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

    private val _restore = MutableStateFlow<RestoreStep?>(null)

    /** The restore being confirmed (File › Restore…, a backup dropped on the window), or null. */
    val restore: StateFlow<RestoreStep?> = _restore.asStateFlow()

    private val _messages = Channel<ShellMessage>(Channel.BUFFERED)

    /** Notes for a snackbar: the result of a backup or export that was started from outside Settings. */
    val messages: Flow<ShellMessage> = _messages.receiveAsFlow()

    /**
     * Opens the file at [location] by what it is (an Anki package is imported, a zip is a backup to
     * restore, a book is left to the shell). Returns true if an import started, so the shell can show the Decks tab and its progress.
     */
    fun openFile(location: String): Boolean = when (FileKind.of(location)) {
        FileKind.AnkiPackage -> {
            transferRepository.startImport(location)
            true
        }
        FileKind.Backup -> {
            stageRestore(location)
            false
        }
        // A book has its own screen, which the shell opens (MnemoRoot); there is nothing to start here.
        FileKind.Epub -> false
        FileKind.Unknown -> {
            _messages.trySend(ShellMessage.UnsupportedFile)
            false
        }
    }

    /** Reads the backup at [location]; if it can be restored, asks to confirm. */
    fun stageRestore(location: String) {
        viewModelScope.launch {
            _restore.value = when (val result = transferRepository.stageRestore(location)) {
                is MnemoResult.Success -> RestoreStep.Confirm(result.data)
                is MnemoResult.Failure -> RestoreStep.Failed(
                    if (result.error is MnemoError.Storage) TransferError.Storage else TransferError.UnsupportedFile,
                )
            }
        }
    }

    fun confirmRestore() {
        if (_restore.value is RestoreStep.Confirm) transferRepository.restartToRestore()
    }

    fun dismissRestore() {
        _restore.value = null
    }

    fun backUpTo(location: String) {
        transferRepository.clearFinished()
        transferRepository.startBackup(location)
        announce(transferRepository.backupState, ShellMessage.BackupDone)
    }

    fun exportTo(location: String, format: ExportFormat) {
        transferRepository.clearFinished()
        transferRepository.startExport(location, format)
        announce(transferRepository.exportState, ShellMessage.ExportDone)
    }

    /** Says how the transfer that was just started ended. Settings shows its own status line too. */
    private fun announce(state: Flow<TransferState<Unit>>, done: ShellMessage) {
        viewModelScope.launch {
            val end = state.first { it is TransferState.Succeeded || it is TransferState.Failed }
            _messages.send(if (end is TransferState.Succeeded) done else ShellMessage.TransferFailed)
        }
    }
}
