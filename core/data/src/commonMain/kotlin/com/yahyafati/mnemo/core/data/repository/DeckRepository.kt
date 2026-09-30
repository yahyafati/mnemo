package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DeckRepository {
    /** Every live deck, sorted by name. */
    fun observeDecks(): Flow<List<Deck>>

    /** Every live deck with today's counts. */
    fun observeDeckSummaries(): Flow<List<DeckSummary>>

    fun observeDeck(id: String): Flow<Deck?>

    suspend fun getDecks(): List<Deck>

    suspend fun getDeck(id: String): Deck?

    /**
     * Creates the deck [path] ("Parent::Child"), creating missing parents, or, with [id], updates
     * that deck (renaming and re-parenting it to match [path]). [examDate] sets (or, when null,
     * clears) the deck's exam countdown. Returns the deck's id.
     */
    suspend fun saveDeck(
        path: String,
        description: String = "",
        category: String? = null,
        id: String? = null,
        examDate: LocalDate? = null,
    ): String

    suspend fun setStarred(id: String, starred: Boolean)

    /** Soft-deletes the deck, its subdecks, and all their notes and cards. */
    suspend fun deleteDeck(id: String)
}
