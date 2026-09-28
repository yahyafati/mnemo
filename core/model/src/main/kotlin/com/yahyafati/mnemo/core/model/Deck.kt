package com.yahyafati.mnemo.core.model

import java.time.Instant

/**
 * A deck. Nesting is Anki-style: "Languages::Japanese" is a deck named "Japanese" whose
 * [parentId] points at "Languages".
 */
data class Deck(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val description: String = "",
    val category: String? = null,
    val starred: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /** Separator between deck levels in a full name, as in Anki. */
        const val PATH_SEPARATOR = "::"
    }
}

/** A deck with its card counts for today. Counts cover this deck only, not its subdecks. */
data class DeckSummary(
    val deck: Deck,
    /** Full name, e.g. "Languages::Japanese". */
    val path: String,
    /** Review cards due today plus learning cards due today. */
    val dueCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val totalCount: Int,
    val lastReviewedAt: Instant?,
)
