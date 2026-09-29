package com.yahyafati.mnemo.core.ai.prompt

import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.model.Cloze
import com.yahyafati.mnemo.core.model.NoteKind

/** What the study-time assistant is asked to do with the current card. */
enum class AssistRequest {
    /** "Explain this": why the answer is right, and a way to remember it. */
    Explain,

    /** "Give me an example". */
    Example,

    /** "Rewrite this card": clearer wording of the same fact, as JSON fields. */
    Rewrite,
}

/**
 * Prompts for study-time AI (PROJECT_OVERVIEW §4). The card is sent with both sides: the answer
 * is already showing when these run. Explanations are short Markdown; a rewrite is JSON.
 */
data class StudyAssistPrompt(
    val request: AssistRequest,
    val kind: NoteKind,
    /** The note's fields: front and back, or cloze text and extra. */
    val fields: List<String>,
    val deckName: String? = null,
) {
    fun messages(): List<ChatMessage> = listOf(ChatMessage.system(system()), ChatMessage.user(user()))

    private fun system(): String = when (request) {
        AssistRequest.Explain, AssistRequest.Example ->
            "You are a concise tutor helping someone study with flashcards. Answer in Markdown in under 150 words, " +
                "in the same language as the card. Write math as \\( … \\) or \\[ … \\]. No preamble."
        AssistRequest.Rewrite ->
            "You improve flashcards for spaced-repetition study: one fact per card, clear and unambiguous wording, " +
                "short answers. Keep the same fact, the same language and the same Markdown/math conventions. " +
                "Reply with only a JSON object {\"front\": \"…\", \"back\": \"…\"} and nothing else."
    }

    private fun user(): String = buildString {
        if (!deckName.isNullOrBlank()) appendLine("Deck: $deckName")
        val front = fields.getOrElse(0) { "" }.trim()
        val back = fields.getOrElse(1) { "" }.trim()
        when (kind) {
            NoteKind.Cloze -> {
                appendLine("Cloze card (hidden parts marked {{cN::…}}):")
                appendLine(front)
                if (back.isNotEmpty()) appendLine("Extra: $back")
                appendLine()
                appendLine("Full text: ${Cloze.reveal(front)}")
            }
            NoteKind.Basic, NoteKind.Reversed -> {
                appendLine("Front: $front")
                appendLine("Back: $back")
            }
        }
        appendLine()
        append(
            when (request) {
                AssistRequest.Explain -> "Explain why this answer is correct and give one memorable way to remember it."
                AssistRequest.Example -> "Give one or two concrete, memorable examples that illustrate this card."
                AssistRequest.Rewrite -> when (kind) {
                    NoteKind.Cloze ->
                        "Rewrite this cloze card. \"front\" is the text with the same deletions, numbered exactly as now " +
                            "(${Cloze.ordinals(front).joinToString { "c$it" }}); \"back\" is the extra hint, or empty."
                    NoteKind.Reversed ->
                        "Rewrite this card. It is studied in both directions, so each side must work as a prompt for the other."
                    NoteKind.Basic -> "Rewrite this card."
                }
            },
        )
    }
}
