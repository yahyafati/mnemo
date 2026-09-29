package com.yahyafati.mnemo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(val settings: UserSettings) : SettingsUiState
}

/** Backups, restore and export (Settings › Data). */
data class DataUiState(
    val backupState: TransferState<Unit> = TransferState.Idle,
    val exportState: TransferState<Unit> = TransferState.Idle,
    val restore: RestoreStep? = null,
)

sealed interface RestoreStep {
    /** A backup was read and is ready; the user confirms replacing everything with it. */
    data class Confirm(val createdAt: Instant) : RestoreStep

    /** The picked file couldn't be restored. */
    data class Failed(val error: TransferError) : RestoreStep
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val transferRepository: DataTransferRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map<UserSettings, SettingsUiState> { SettingsUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    private val restore = MutableStateFlow<RestoreStep?>(null)

    val dataState: StateFlow<DataUiState> = combine(transferRepository.backupState, transferRepository.exportState, restore, ::DataUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataUiState())

    fun backUpTo(uri: String) = transferRepository.startBackup(uri)

    fun exportTo(uri: String, format: ExportFormat) = transferRepository.startExport(uri, format)

    fun dismissTransfers() = transferRepository.clearFinished()

    /** Turns automatic backups on into [folderUri] (a SAF tree), or off. */
    fun setAutoBackup(enabled: Boolean, folderUri: String?) {
        viewModelScope.launch { transferRepository.setAutoBackup(enabled, folderUri) }
    }

    /** Reads the backup at [uri]; if it can be restored, asks to confirm. */
    fun readBackup(uri: String) {
        viewModelScope.launch {
            restore.value = when (val result = transferRepository.stageRestore(uri)) {
                is MnemoResult.Success -> RestoreStep.Confirm(result.data)
                is MnemoResult.Failure -> RestoreStep.Failed(
                    if (result.error is MnemoError.Storage) TransferError.Storage else TransferError.UnsupportedFile,
                )
            }
        }
    }

    fun confirmRestore() {
        if (restore.value is RestoreStep.Confirm) transferRepository.restartToRestore()
    }

    fun dismissRestore() {
        restore.value = null
    }

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
