package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun getDecks(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getDeck(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    fun observeDeck(id: String): Flow<DeckEntity?>

    @Upsert
    suspend fun upsert(deck: DeckEntity)

    @Query("UPDATE decks SET starred = :starred, updatedAt = :now WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean, now: Long)

    @Query("UPDATE decks SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)

    /**
     * Per-deck card counts for today. [dayEnd] is the start of the next study day; [now] decides
     * which buried cards are hidden. Suspended and buried cards count toward the total only.
     */
    @Query(
        """
        SELECT d.id AS deckId,
            COALESCE(SUM(CASE WHEN $STUDYABLE AND c.state = 2 AND c.due < :dayEnd THEN 1 ELSE 0 END), 0) AS reviewDue,
            COALESCE(SUM(CASE WHEN $STUDYABLE AND c.state IN (1, 3) AND c.due < :dayEnd THEN 1 ELSE 0 END), 0) AS learningDue,
            COALESCE(SUM(CASE WHEN $STUDYABLE AND c.state = 0 THEN 1 ELSE 0 END), 0) AS newCount,
            COUNT(c.id) AS total,
            MAX(c.lastReview) AS lastReviewedAt
        FROM decks d
        LEFT JOIN cards c ON c.deckId = d.id AND c.deletedAt IS NULL
        WHERE d.deletedAt IS NULL
        GROUP BY d.id
        """,
    )
    fun observeDeckCounts(now: Long, dayEnd: Long): Flow<List<DeckCounts>>

    private companion object {
        const val STUDYABLE = "c.suspended = 0 AND (c.buriedUntil IS NULL OR c.buriedUntil <= :now)"
    }
}

data class DeckCounts(
    val deckId: String,
    val reviewDue: Int,
    val learningDue: Int,
    val newCount: Int,
    val total: Int,
    val lastReviewedAt: Long?,
)
