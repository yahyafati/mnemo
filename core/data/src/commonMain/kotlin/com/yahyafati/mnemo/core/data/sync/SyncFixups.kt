package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.SyncMergeDao
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import java.util.UUID

/**
 * What the merge puts right after other devices' changes were applied, where a row-by-row merge leaves something
 * wrong that only the whole picture shows (ADR 0013). Each pass is a function of the merged rows, writes through the
 * normal tables (so the others receive the result, and a device that computes the same thing writes the same
 * values), and stops changing anything once it has run.
 *
 * 1. **Two decks of one name.** "Spanish" created offline on two devices is two rows. The one with the lower id
 *    stays; the other's notes, cards and subdecks move into it and it is deleted. Names compare ignoring case, like
 *    `DeckRepository.saveDeck`.
 * 2. **Cards of a deleted note.** A delete wins over an edit, so cards that were added to a note another device deleted
 *    go with it.
 * 3. **Work in a deleted deck.** Notes, cards or subdecks that another device added to a deck that was deleted would
 *    be hidden with it. They move to `<name> (recovered)`, a deck whose id comes from the deleted one's, so every
 *    device that does this makes the same deck.
 */
internal class SyncFixups(
    private val deckDao: DeckDao,
    private val mergeDao: SyncMergeDao,
) {
    suspend fun run(now: Long) {
        mergeDuplicateDecks(now)
        mergeDao.deleteCardsOfDeletedNotes(now)
        recoverWorkInDeletedDecks(now)
    }

    private suspend fun mergeDuplicateDecks(now: Long) {
        // Merging a pair can make their children collide, so go on until a pass finds nothing.
        repeat(MAX_ROUNDS) {
            val duplicates = deckDao.getDecks()
                .groupBy { it.parentId to it.name.lowercase() }
                .values
                .filter { it.size > 1 }
            if (duplicates.isEmpty()) return
            for (group in duplicates) {
                val winner = group.minOf { it.id }
                for (loser in group.map { it.id }.filter { it != winner }) {
                    mergeDao.moveNotes(loser, winner, now)
                    mergeDao.moveCards(loser, winner, now)
                    mergeDao.moveChildDecks(loser, winner, now)
                    deckDao.softDelete(listOf(loser), now)
                }
            }
        }
    }

    private suspend fun recoverWorkInDeletedDecks(now: Long) {
        for (deleted in mergeDao.getDeletedDecksInUse()) {
            val name = mergeDao.getDeckName(deleted) ?: continue
            val home = recoveredDeck(deleted, name, now)
            mergeDao.moveNotes(deleted, home, now)
            mergeDao.moveCards(deleted, home, now)
            mergeDao.moveChildDecks(deleted, home, now)
        }
    }

    /** The live deck that takes the work of [deleted]: made now, or the one an earlier pass (here or elsewhere) made. */
    private suspend fun recoveredDeck(deleted: String, name: String, now: Long): String {
        var id = recoveredId(deleted)
        var title = "$name (recovered)"
        // The user may have deleted the recovered deck too; then the work goes one level further.
        repeat(MAX_ROUNDS) {
            if (mergeDao.countDecks(id) == 0) {
                val parent = mergeDao.getDeckParent(deleted)?.takeIf { deckDao.getDeck(it) != null }
                deckDao.upsert(
                    DeckEntity(
                        id = id, parentId = parent, name = title, description = "", category = null, starred = false,
                        createdAt = now, updatedAt = now,
                    ),
                )
                return id
            }
            if (deckDao.getDeck(id) != null) return id
            id = recoveredId(id)
            title = "$title (recovered)"
        }
        return id
    }

    private fun recoveredId(deleted: String): String =
        UUID.nameUUIDFromBytes("mnemo-recovered:$deleted".toByteArray()).toString()

    private companion object {
        const val MAX_ROUNDS = 20
    }
}
