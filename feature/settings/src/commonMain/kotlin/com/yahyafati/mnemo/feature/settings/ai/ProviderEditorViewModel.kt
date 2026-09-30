package com.yahyafati.mnemo.feature.settings.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.repository.AiProviderDraft
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.ApiKeyChange
import com.yahyafati.mnemo.core.model.AiProviderPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ProviderEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AiProviderRepository,
) : ViewModel() {
    private val existingId: String? = savedStateHandle[PROVIDER_ID_KEY]

    private val _uiState = MutableStateFlow(ProviderEditorUiState())
    val uiState: StateFlow<ProviderEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val provider = existingId?.let { repository.getProvider(it) }
        if (provider != null) {
            _uiState.value = ProviderEditorUiState(
                loading = false,
                providerId = provider.id,
                isNew = false,
                presetId = provider.presetId,
                name = provider.name,
                baseUrl = provider.baseUrl,
                hasStoredKey = provider.hasApiKey,
                headers = provider.headers.map { (k, v) -> HeaderField(k, v) },
                defaultModel = provider.defaultModel.orEmpty(),
                timeoutSeconds = provider.timeoutSeconds.toString(),
                isLocal = provider.isLocal,
                enabled = provider.enabled,
                savedModels = repository.observeModels(provider.id).first(),
                disclosureAccepted = provider.disclosureAcceptedAt != null,
            )
            return
        }
        // A new provider gets its id now (kept across process death), so a test before the first
        // save already has something to log usage against.
        val id = savedStateHandle.get<String>(DRAFT_ID_KEY) ?: UUID.randomUUID().toString().also { savedStateHandle[DRAFT_ID_KEY] = it }
        _uiState.value = ProviderEditorUiState(loading = false, providerId = id)
        savedStateHandle.get<String>(PRESET_ID_KEY)?.let { choosePreset(it) }
    }

    fun onAction(action: ProviderEditorAction) {
        when (action) {
            is ProviderEditorAction.ChoosePreset -> choosePreset(action.presetId)
            is ProviderEditorAction.Name -> _uiState.update { it.copy(name = action.value) }
            is ProviderEditorAction.BaseUrl -> changeConnection { it.copy(baseUrl = action.value) }
            is ProviderEditorAction.ApiKey -> changeConnection { it.copy(apiKey = action.value) }
            ProviderEditorAction.ReplaceKey -> _uiState.update { it.copy(replacingKey = true, removeKey = false) }
            ProviderEditorAction.RemoveKey -> changeConnection { it.copy(removeKey = true, replacingKey = false, apiKey = "") }
            ProviderEditorAction.KeepKey -> changeConnection { it.copy(removeKey = false, replacingKey = false, apiKey = "") }
            ProviderEditorAction.AddHeader -> _uiState.update { it.copy(headers = it.headers + HeaderField()) }
            is ProviderEditorAction.HeaderName -> changeConnection { s ->
                s.copy(headers = s.headers.mapIndexed { i, h -> if (i == action.index) h.copy(name = action.value) else h })
            }
            is ProviderEditorAction.HeaderValue -> changeConnection { s ->
                s.copy(headers = s.headers.mapIndexed { i, h -> if (i == action.index) h.copy(value = action.value) else h })
            }
            is ProviderEditorAction.RemoveHeader -> changeConnection { s ->
                s.copy(headers = s.headers.filterIndexed { i, _ -> i != action.index })
            }
            // A different model keeps the report (its model list is still right) but not a capability override.
            is ProviderEditorAction.DefaultModel -> _uiState.update { it.copy(defaultModel = action.value, capabilityOverride = null) }
            is ProviderEditorAction.Timeout -> _uiState.update { it.copy(timeoutSeconds = action.value.filter(Char::isDigit).take(4)) }
            is ProviderEditorAction.Local -> changeConnection { it.copy(isLocal = action.value) }
            is ProviderEditorAction.Enabled -> _uiState.update { it.copy(enabled = action.value) }
            is ProviderEditorAction.Capabilities -> _uiState.update { it.copy(capabilityOverride = action.value) }
            ProviderEditorAction.Test -> test()
            ProviderEditorAction.AcceptDisclosure -> acceptDisclosure()
            ProviderEditorAction.DismissDisclosure -> _uiState.update { it.copy(showDisclosure = false) }
            ProviderEditorAction.Save -> save()
            ProviderEditorAction.DismissSaveError -> _uiState.update { it.copy(saveFailed = false) }
            ProviderEditorAction.Delete -> _uiState.update { it.copy(confirmDelete = true) }
            ProviderEditorAction.DismissDelete -> _uiState.update { it.copy(confirmDelete = false) }
            ProviderEditorAction.ConfirmDelete -> delete()
        }
    }

    /** Where and how to connect changed: the last test no longer describes it. */
    private fun changeConnection(change: (ProviderEditorUiState) -> ProviderEditorUiState) =
        _uiState.update { change(it).copy(report = null) }

    private fun choosePreset(presetId: String) {
        val preset = AiProviderPresets.byId(presetId) ?: return
        changeConnection { state ->
            if (!state.isNew) return@changeConnection state
            // Only replace a name the user hasn't changed from the previous preset's.
            val nameIsDefault = state.name.isBlank() || state.name == state.preset?.name
            state.copy(
                presetId = preset.id,
                name = if (nameIsDefault && preset.id != AiProviderPresets.CUSTOM_ID) preset.name else state.name,
                baseUrl = preset.baseUrl,
                isLocal = preset.isLocal,
                headers = preset.headers.map { (k, v) -> HeaderField(k, v) },
            )
        }
    }

    private fun test() {
        val state = _uiState.value
        if (!state.canTest) return
        if (!state.disclosureAccepted) {
            _uiState.update { it.copy(showDisclosure = true) }
            return
        }
        _uiState.update { it.copy(testing = true) }
        viewModelScope.launch {
            val report = repository.testConnection(draft(_uiState.value))
            _uiState.update { s ->
                s.copy(
                    testing = false,
                    report = report,
                    // A server with one model (typical for Ollama) fills in the model by itself.
                    defaultModel = s.defaultModel.ifBlank { report.testedModel.orEmpty() },
                )
            }
        }
    }

    private fun acceptDisclosure() {
        _uiState.update { it.copy(disclosureAccepted = true, showDisclosure = false) }
        if (!_uiState.value.isNew) viewModelScope.launch { repository.acceptDisclosure(_uiState.value.providerId) }
        test()
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (repository.saveProvider(draft(state), state.report)) {
                is MnemoResult.Failure -> _uiState.update { it.copy(saving = false, saveFailed = true) }
                is MnemoResult.Success -> {
                    val model = state.defaultModel.trim()
                    if (state.capabilityOverride != null && model.isNotEmpty()) {
                        repository.setCapabilities(state.providerId, model, state.capabilityOverride)
                    }
                    _uiState.update { it.copy(saving = false, closeRequested = true) }
                }
            }
        }
    }

    private fun delete() {
        val state = _uiState.value
        _uiState.update { it.copy(confirmDelete = false) }
        if (state.isNew) {
            _uiState.update { it.copy(closeRequested = true) }
            return
        }
        viewModelScope.launch {
            repository.deleteProvider(state.providerId)
            _uiState.update { it.copy(closeRequested = true) }
        }
    }

    private fun draft(state: ProviderEditorUiState) = AiProviderDraft(
        id = state.providerId,
        name = state.name,
        baseUrl = state.normalizedUrl,
        presetId = state.presetId,
        apiKey = when {
            state.removeKey -> ApiKeyChange.Remove
            state.apiKey.isNotBlank() -> ApiKeyChange.Set(state.apiKey)
            else -> ApiKeyChange.Keep
        },
        headers = state.headers.filterNot { it.isBlank }.associate { it.name.trim() to it.value.trim() },
        defaultModel = state.defaultModel.trim().ifEmpty { null },
        timeoutSeconds = state.timeout ?: com.yahyafati.mnemo.core.model.AiProvider.DEFAULT_TIMEOUT_SECONDS,
        isLocal = state.isLocal,
        enabled = state.enabled,
        disclosureAccepted = state.disclosureAccepted,
    )

    internal companion object {
        // Match the property names of AiProviderEditorRoute.
        const val PROVIDER_ID_KEY = "providerId"
        const val PRESET_ID_KEY = "presetId"
        const val DRAFT_ID_KEY = "draftProviderId"
    }
}
