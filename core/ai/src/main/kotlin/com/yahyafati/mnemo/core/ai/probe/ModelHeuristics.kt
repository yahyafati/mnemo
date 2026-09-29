package com.yahyafati.mnemo.core.ai.probe

import com.yahyafati.mnemo.core.ai.dto.ModelInfo

/**
 * Guesses from model ids, for what the API doesn't say. They only pre-fill settings the user can
 * change, so a wrong guess costs a tap, not a failed feature.
 */
object ModelHeuristics {
    /** Whether [id] accepts images: the provider's own description if it has one, else the name. */
    fun supportsVision(id: String, info: ModelInfo? = null): Boolean {
        info?.architecture?.inputModalities?.let { return "image" in it }
        val name = id.lowercase()
        return VISION_HINTS.any { it in name }
    }

    /**
     * Whether [id] looks like a chat model. `GET /models` also lists embedding, speech and image
     * models, which the model picker hides (they can still be typed in).
     */
    fun isChatModel(id: String): Boolean {
        val name = id.lowercase()
        return NON_CHAT_HINTS.none { it in name }
    }

    private val VISION_HINTS = listOf(
        "vision", "-vl", "vl-", "llava", "pixtral", "moondream", "minicpm-v", "gpt-4o", "gpt-4.1", "gpt-5",
        "claude", "gemini", "gemma-3", "gemma3", "llama-4", "llama4",
    )

    private val NON_CHAT_HINTS = listOf(
        "embed", "whisper", "tts", "dall-e", "moderation", "transcribe", "imagen", "gpt-image", "rerank",
        "davinci-002", "babbage-002", "text-similarity", "aqa",
    )
}
