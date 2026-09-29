package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SmartExtractUiState {
    data object Loading : SmartExtractUiState

    /** No usable provider: show the setup prompt. */
    data object NeedsProvider : SmartExtractUiState

    data class Ready(val route: AiRoute) : SmartExtractUiState
}

/** The Smart Extract side of the Create tab. Generation itself arrives in Phase 4. */
@HiltViewModel
class SmartExtractViewModel @Inject constructor(
    repository: AiProviderRepository,
) : ViewModel() {
    val uiState: StateFlow<SmartExtractUiState> = repository.observeEffectiveRoutes()
        .map { routes -> routes[AiTask.Extract]?.let(SmartExtractUiState::Ready) ?: SmartExtractUiState.NeedsProvider }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartExtractUiState.Loading)
}
