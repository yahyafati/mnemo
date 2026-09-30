package com.yahyafati.mnemo.core.data.repository

import androidx.paging.PagingData
import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.flow.Flow

/** The card browser: every card, searchable and filterable, with bulk edits (ROADMAP Phase 2). */
interface CardBrowserRepository {
    /** Cards matching [query], paged, each with its note and deck name. */
    fun browse(query: CardQuery): Flow<PagingData<StudyCard>>

    fun count(query: CardQuery): Flow<Int>

    /** Ids of every card matching [query], for "select all". */
    suspend fun cardIds(query: CardQuery): List<String>

    /** Every tag in use, sorted. */
    suspend fun tags(): List<String>

    suspend fun setSuspended(cardIds: Collection<String>, suspended: Boolean)

    suspend fun setFlagged(cardIds: Collection<String>, flagged: Boolean)

    /** Moves the cards to [deckId]; their notes follow, so new cards of a note land there too. */
    suspend fun moveToDeck(cardIds: Collection<String>, deckId: String)

    /** Adds [tag] to the notes of these cards. */
    suspend fun addTag(cardIds: Collection<String>, tag: String)

    suspend fun removeTag(cardIds: Collection<String>, tag: String)

    /** Deletes the notes of these cards, with all their cards. */
    suspend fun deleteNotes(cardIds: Collection<String>)
}
