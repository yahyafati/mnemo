package com.yahyafati.mnemo.feature.settings.ai

import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiProviderPreset
import com.yahyafati.mnemo.core.model.AiProviderPresets

data class HeaderField(val name: String = "", val value: String = "") {
    val isBlank: Boolean get() = name.isBlank() && value.isBlank()

    val isValid: Boolean get() = isBlank || (AiEndpoint.isValidHeaderName(name.trim()) && AiEndpoint.isValidHeaderValue(value.trim()))
}

/**
 * The provider editor. The typed API key lives only here, in memory: it is never put in the
 * saved state, and a stored key is never read back into the UI.
 */
data class ProviderEditorUiState(
    val loading: Boolean = true,
    val providerId: String = "",
    val isNew: Boolean = true,
    val presetId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    /** A newly typed key; blank keeps the stored one. */
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    /** The user is typing a replacement for the stored key. */
    val replacingKey: Boolean = false,
    val removeKey: Boolean = false,
    val headers: List<HeaderField> = emptyList(),
    val defaultModel: String = "",
    val timeoutSeconds: String = AiProvider.DEFAULT_TIMEOUT_SECONDS.toString(),
    val isLocal: Boolean = false,
    val enabled: Boolean = true,
    val savedModels: List<AiModel> = emptyList(),
    /** The last test of the current settings; cleared when the URL, key or headers change. */
    val report: AiConnectionReport? = null,
    val testing: Boolean = false,
    val disclosureAccepted: Boolean = false,
    val showDisclosure: Boolean = false,
    /** Capabilities the user set for the default model, saved with the provider. */
    val capabilityOverride: AiCapabilities? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val confirmDelete: Boolean = false,
    val closeRequested: Boolean = false,
) {
    val preset: AiProviderPreset? get() = AiProviderPresets.byId(presetId)

    val normalizedUrl: String get() = AiEndpoint.normalize(baseUrl)

    val host: String get() = AiEndpoint.host(normalizedUrl) ?: normalizedUrl

    val urlCheck: AiEndpoint.Check get() = AiEndpoint.check(normalizedUrl, isLocal)

    val headersValid: Boolean get() = headers.all { it.isValid }

    val timeout: Int? get() = timeoutSeconds.trim().toIntOrNull()?.takeIf { it in AiProvider.MIN_TIMEOUT_SECONDS..AiProvider.MAX_TIMEOUT_SECONDS }

    /** On a phone, "localhost" is the phone: the user almost certainly wants the computer's address. */
    val pointsAtThisDevice: Boolean get() = isLocal && AiEndpoint.host(normalizedUrl)?.lowercase() in setOf("localhost", "127.0.0.1", "::1")

    /** The preset needs a key and none will be stored. */
    val missingKey: Boolean get() = preset?.requiresApiKey == true && apiKey.isBlank() && (!hasStoredKey || removeKey)

    /** Model ids to choose from: the last test's list if it has one, else the saved models. */
    val modelChoices: List<String>
        get() {
            val tested = report?.takeIf { it.modelsFailure == null }?.models?.map { it.id }
            return (tested ?: savedModels.map { it.id }).distinct()
        }

    /** What the default model supports: the user's choice, then this session's test, then what was saved. */
    val capabilities: AiCapabilities?
        get() = capabilityOverride
            ?: report?.capabilities?.takeIf { report.testedModel == defaultModel.trim() }
            ?: savedModels.firstOrNull { it.id == defaultModel.trim() }?.capabilities

    val capabilitiesSetByUser: Boolean
        get() = capabilityOverride != null ||
            (report?.testedModel != defaultModel.trim() && savedModels.firstOrNull { it.id == defaultModel.trim() }?.capabilitiesSetByUser == true)

    val canTest: Boolean get() = !loading && !testing && urlCheck == AiEndpoint.Check.Ok && headersValid && timeout != null

    val canSave: Boolean get() = !loading && !saving && urlCheck == AiEndpoint.Check.Ok && headersValid && timeout != null
}

sealed interface ProviderEditorAction {
    data class ChoosePreset(val presetId: String) : ProviderEditorAction

    data class Name(val value: String) : ProviderEditorAction

    data class BaseUrl(val value: String) : ProviderEditorAction

    data class ApiKey(val value: String) : ProviderEditorAction

    data object ReplaceKey : ProviderEditorAction

    data object RemoveKey : ProviderEditorAction

    /** Undoes [ReplaceKey] or [RemoveKey]. */
    data object KeepKey : ProviderEditorAction

    data object AddHeader : ProviderEditorAction

    data class HeaderName(val index: Int, val value: String) : ProviderEditorAction

    data class HeaderValue(val index: Int, val value: String) : ProviderEditorAction

    data class RemoveHeader(val index: Int) : ProviderEditorAction

    data class DefaultModel(val value: String) : ProviderEditorAction

    data class Timeout(val value: String) : ProviderEditorAction

    data class Local(val value: Boolean) : ProviderEditorAction

    data class Enabled(val value: Boolean) : ProviderEditorAction

    data class Capabilities(val value: AiCapabilities) : ProviderEditorAction

    data object Test : ProviderEditorAction

    data object AcceptDisclosure : ProviderEditorAction

    data object DismissDisclosure : ProviderEditorAction

    data object Save : ProviderEditorAction

    data object DismissSaveError : ProviderEditorAction

    data object Delete : ProviderEditorAction

    data object ConfirmDelete : ProviderEditorAction

    data object DismissDelete : ProviderEditorAction
}
