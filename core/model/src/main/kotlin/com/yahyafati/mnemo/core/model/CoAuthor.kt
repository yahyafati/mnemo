package com.yahyafati.mnemo.core.model

/** One message of an AI Co-Author conversation. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * The deck AI Co-Author works on (PROJECT_OVERVIEW §4.3): its name and its notes. Only this,
 * summarized, is sent to the provider; see [MAX_NOTES_SENT].
 */
data class CoAuthorDeck(
    val name: String,
    val notes: List<Note>,
) {
    companion object {
        /** Notes sent with a Co-Author request, at most; a larger deck is sampled evenly. */
        const val MAX_NOTES_SENT = 150

        /** Characters of each side sent per note. */
        const val MAX_SIDE_CHARS = 160
    }
}

/** Notes that ask the same thing (AI Co-Author's "Find duplicates"; computed on the device). */
data class DuplicateGroup(
    val notes: List<Note>,
    /** 1.0 for the same text; lower for near-duplicates. */
    val similarity: Double,
)
