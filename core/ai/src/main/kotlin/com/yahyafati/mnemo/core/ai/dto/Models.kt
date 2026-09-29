package com.yahyafati.mnemo.core.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /models`. */
@Serializable
data class ModelsResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null,
    /** OpenRouter describes what each model accepts; other providers leave this out. */
    val architecture: Architecture? = null,
) {
    @Serializable
    data class Architecture(
        @SerialName("input_modalities") val inputModalities: List<String>? = null,
    )
}

/** The error bodies servers send: `{"error": {"message": …}}`, `{"error": "…"}` or `{"message": …}`. */
@Serializable
internal data class ErrorBody(
    val error: kotlinx.serialization.json.JsonElement? = null,
    val message: String? = null,
    val detail: kotlinx.serialization.json.JsonElement? = null,
)
