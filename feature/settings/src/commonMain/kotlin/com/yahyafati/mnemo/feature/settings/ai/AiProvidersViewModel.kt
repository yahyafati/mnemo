package com.yahyafati.mnemo.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.model.AiUsageTotal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiProvidersUiState(
    val loading: Boolean = true,
    val providers: List<AiProvider> = emptyList(),
    val models: Map<String, List<AiModel>> = emptyMap(),
    val routes: Map<AiTask, AiTaskRoute> = emptyMap(),
    /** What each task uses right now. */
    val effective: Map<AiTask, AiRoute?> = emptyMap(),
    val usage: List<AiUsageTotal> = emptyList(),
) {
    /** The provider tasks without a route of their own use: the first usable one. */
    val defaultProviderId: String? get() = providers.firstOrNull { it.isUsable }?.id
}

sealed interface AiProvidersAction {
    data class SetEnabled(val providerId: String, val enabled: Boolean) : AiProvidersAction

    data class Move(val providerId: String, val offset: Int) : AiProvidersAction

    /** A null [providerId] goes back to the default provider; a null [modelId] is the provider's default model. */
    data class SetRoute(val task: AiTask, val providerId: String?, val modelId: String? = null) : AiProvidersAction
}

class AiProvidersViewModel(
    private val repository: AiProviderRepository,
) : ViewModel() {
    val uiState: StateFlow<AiProvidersUiState> = combine(
        repository.observeProviders(),
        repository.observeAllModels(),
        repository.observeRoutes(),
        repository.observeEffectiveRoutes(),
        repository.observeUsage(),
    ) { providers, models, routes, effective, usage ->
        AiProvidersUiState(
            loading = false,
            providers = providers,
            models = models,
            routes = routes.associateBy { it.task },
            effective = effective,
            usage = usage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvidersUiState())

    init {
        // Keys left behind by a restore or an interrupted save.
        viewModelScope.launch { repository.pruneOrphanedKeys() }
    }

    fun onAction(action: AiProvidersAction) {
        viewModelScope.launch {
            when (action) {
                is AiProvidersAction.SetEnabled -> repository.setEnabled(action.providerId, action.enabled)
                is AiProvidersAction.Move -> repository.moveProvider(action.providerId, action.offset)
                is AiProvidersAction.SetRoute -> repository.setRoute(action.task, action.providerId, action.modelId)
            }
        }
    }
}
