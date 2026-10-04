package com.yahyafati.mnemo.core.ai.client

import com.yahyafati.mnemo.core.common.result.MnemoError

/**
 * Recognises a server saying "I can't take these images". There is no standard error for it, so it
 * goes by the status (400, 415 and 422 are what servers use for a body they won't read) and by what
 * the message talks about. Authentication, rate limits and server errors are never read as this.
 */
internal object ImageRejection {
    /** An [MnemoError.ImagesNotAccepted] for [error] if it is that, else null. */
    fun from(error: MnemoError): MnemoError.ImagesNotAccepted? {
        if (error !is MnemoError.Http || error.code !in STATUSES) return null
        val body = error.body?.lowercase() ?: return null
        if (HINTS.none { it in body }) return null
        return MnemoError.ImagesNotAccepted(listOfNotNull("HTTP ${error.code}", error.body).joinToString(": "))
    }

    private val STATUSES = setOf(400, 415, 422)

    // "image", "vision" and "multimodal" name the feature; the others are how servers without
    // vision complain about a content list ("Input should be a valid string").
    private val HINTS = listOf("image", "vision", "multimodal", "multi-modal", "modalit", "valid string", "expected string", "unsupported content")
}
