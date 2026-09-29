package com.yahyafati.mnemo.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id AND deletedAt IS NULL")
    suspend fun getCard(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY templateOrd")
    suspend fun getCardsForNote(noteId: String): List<CardEntity>

    @Query("UPDATE cards SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE cards SET deletedAt = :now, updatedAt = :now WHERE deckId IN (:deckIds) AND deletedAt IS NULL")
    suspend fun softDeleteInDecks(deckIds: List<String>, now: Long)

    @Query("UPDATE cards SET deckId = :deckId, updatedAt = :now WHERE noteId = :noteId")
    suspend fun moveNoteCards(noteId: String, deckId: String, now: Long)

    @Query("UPDATE cards SET starred = :starred, updatedAt = :now WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean, now: Long)

    @Query("UPDATE cards SET flagged = :flagged, updatedAt = :now WHERE id = :id")
    suspend fun setFlagged(id: String, flagged: Boolean, now: Long)

    @Query("UPDATE cards SET suspended = :suspended, updatedAt = :now WHERE id = :id")
    suspend fun setSuspended(id: String, suspended: Boolean, now: Long)

    @Query("UPDATE cards SET buriedUntil = :until, updatedAt = :now WHERE id = :id")
    suspend fun setBuriedUntil(id: String, until: Long?, now: Long)

    /** Learning and relearning cards due before [dayEnd], earliest first. */
    @Query(
        """
        SELECT * FROM cards WHERE $STUDYABLE AND deckId IN (:deckIds)
            AND state IN (1, 3) AND due < :dayEnd
        ORDER BY due
        """,
    )
    suspend fun getLearningCards(deckIds: List<String>, now: Long, dayEnd: Long): List<CardEntity>

    /** Review cards due before [dayEnd], most overdue first. */
    @Query(
        """
        SELECT * FROM cards WHERE $STUDYABLE AND deckId IN (:deckIds)
            AND state = 2 AND due < :dayEnd
        ORDER BY due LIMIT :limit
        """,
    )
    suspend fun getReviewCards(deckIds: List<String>, now: Long, dayEnd: Long, limit: Int): List<CardEntity>

    /** New cards in the order they were added; a note's cards stay together. */
    @Query(
        """
        SELECT * FROM cards WHERE $STUDYABLE AND deckId IN (:deckIds) AND state = 0
        ORDER BY createdAt, noteId, templateOrd LIMIT :limit
        """,
    )
    suspend fun getNewCards(deckIds: List<String>, now: Long, limit: Int): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE deletedAt IS NULL")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE noteId IN (:noteIds) AND deletedAt IS NULL ORDER BY noteId, templateOrd")
    suspend fun getCardsForNotes(noteIds: List<String>): List<CardEntity>

    @Query("SELECT DISTINCT noteId FROM cards WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getNoteIds(ids: List<String>): List<String>

    @Query("UPDATE cards SET suspended = :suspended, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setSuspended(ids: List<String>, suspended: Boolean, now: Long)

    @Query("UPDATE cards SET flagged = :flagged, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setFlagged(ids: List<String>, flagged: Boolean, now: Long)

    @Query("UPDATE cards SET deckId = :deckId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDeck(ids: List<String>, deckId: String, now: Long)

    @Query("UPDATE cards SET deletedAt = :now, updatedAt = :now WHERE noteId IN (:noteIds) AND deletedAt IS NULL")
    suspend fun softDeleteForNotes(noteIds: List<String>, now: Long)

    /** A page of the card browser; the query comes from [BrowseQueries.rows]. */
    @RawQuery(observedEntities = [CardEntity::class, NoteEntity::class, DeckEntity::class])
    fun browse(query: SupportSQLiteQuery): PagingSource<Int, BrowseRow>

    /** Card ids matching a browser filter; the query comes from [BrowseQueries.ids]. */
    @RawQuery
    suspend fun browseIds(query: SupportSQLiteQuery): List<String>

    /** How many cards match a browser filter; the query comes from [BrowseQueries.count]. */
    @RawQuery(observedEntities = [CardEntity::class, NoteEntity::class])
    fun browseCount(query: SupportSQLiteQuery): Flow<Int>

    private companion object {
        const val STUDYABLE =
            "deletedAt IS NULL AND suspended = 0 AND (buriedUntil IS NULL OR buriedUntil <= :now)"
    }
}

/** A card browser row: the card, its note, and its deck's name. Columns are prefixed (see [BrowseQueries]). */
data class BrowseRow(
    @Embedded(prefix = "c_") val card: CardEntity,
    @Embedded(prefix = "n_") val note: NoteEntity,
    val deckName: String?,
)
