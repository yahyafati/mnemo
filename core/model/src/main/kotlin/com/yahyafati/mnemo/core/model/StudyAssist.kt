package com.yahyafati.mnemo.core.model

import java.time.Instant

/** Study-time AI on the current card (PROJECT_OVERVIEW §4). */
enum class StudyAssist(val task: AiTask) {
    /** "Explain this". */
    Explain(AiTask.Explain),

    /** "Give me an example". */
    Example(AiTask.Explain),

    /** "Rewrite this card". */
    Rewrite(AiTask.Rewrite),
}

/** A piece of a streamed AI answer, or its end. */
sealed interface AssistUpdate {
    data class Text(val delta: String) : AssistUpdate

    /** Always last. [failure] is set if the answer stopped early or never started. */
    data class Done(val failure: AiFailure? = null) : AssistUpdate
}

/** What "Rewrite this card" proposes: new note fields, not saved until the user applies them. */
sealed interface RewriteOutcome {
    data class Proposed(val fields: List<String>) : RewriteOutcome

    data class Failed(val failure: AiFailure) : RewriteOutcome
}

/**
 * An "Explain this" or "Give me an example" answer kept for a note, so opening it again costs no
 * request. [outdated] is set when the note's fields changed after it was saved.
 */
data class SavedAssistAnswer(
    val text: String,
    val providerName: String,
    val modelId: String,
    val savedAt: Instant,
    val outdated: Boolean = false,
)
