package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * The queries the merge engine runs after it has applied other devices' changes (docs/sync/ROADMAP.md S3): finding
 * rows two devices made differently, and rows that ended up pointing at something that was deleted. They are plain
 * writes, so the sync triggers record them like any other, which is how a device's merge result reaches the others.
 */
@Dao
interface SyncMergeDao {
    @Query("UPDATE notes SET deckId = :to, updatedAt = :now WHERE deckId = :from AND deletedAt IS NULL")
    suspend fun moveNotes(from: String, to: String, now: Long)

    @Query("UPDATE cards SET deckId = :to, updatedAt = :now WHERE deckId = :from AND deletedAt IS NULL")
    suspend fun moveCards(from: String, to: String, now: Long)

    @Query("UPDATE decks SET parentId = :to, updatedAt = :now WHERE parentId = :from AND deletedAt IS NULL")
    suspend fun moveChildDecks(from: String, to: String, now: Long)

    /** Live cards of a deleted note: a delete wins over whatever the other device did to the note. */
    @Query(
        """
        UPDATE cards SET deletedAt = :now, updatedAt = :now
        WHERE deletedAt IS NULL AND noteId IN (SELECT id FROM notes WHERE deletedAt IS NOT NULL)
        """,
    )
    suspend fun deleteCardsOfDeletedNotes(now: Long)

    /** Deleted decks that still have a live note, card or subdeck in them. */
    @Query(
        """
        SELECT id FROM decks WHERE deletedAt IS NOT NULL AND (
            id IN (SELECT deckId FROM notes WHERE deletedAt IS NULL)
            OR id IN (SELECT deckId FROM cards WHERE deletedAt IS NULL)
            OR id IN (SELECT parentId FROM decks WHERE deletedAt IS NULL AND parentId IS NOT NULL)
        ) ORDER BY id
        """,
    )
    suspend fun getDeletedDecksInUse(): List<String>

    /** A deck, deleted or not: the recovery needs the name of one that was deleted. */
    @Query("SELECT name FROM decks WHERE id = :id")
    suspend fun getDeckName(id: String): String?

    @Query("SELECT parentId FROM decks WHERE id = :id")
    suspend fun getDeckParent(id: String): String?

    /** Whether a deck row exists, deleted or not. */
    @Query("SELECT COUNT(*) FROM decks WHERE id = :id")
    suspend fun countDecks(id: String): Int

    /** Live media rows, to see which files this device is missing. */
    @Query("SELECT id FROM media WHERE deletedAt IS NULL")
    suspend fun getLiveMediaIds(): List<String>

    /** Live decks and notes: whether a collection holds anything a join would replace. */
    @Query("SELECT (SELECT COUNT(*) FROM decks WHERE deletedAt IS NULL) + (SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL)")
    suspend fun countLiveDecksAndNotes(): Int
}
