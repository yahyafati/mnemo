package com.yahyafati.mnemo.core.database

import androidx.room.useReaderConnection
import com.yahyafati.mnemo.core.database.entity.AiAnswerEntity
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.MediaEntity
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.database.sync.SYNCED_TABLES
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The outbox and the device state (docs/sync/ROADMAP.md S1): what the triggers record, and when. */
class SyncTriggersTest : PlatformTest() {
    private lateinit var db: MnemoDatabase

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabase()
    }

    @AfterTest
    fun tearDown() = db.close()

    private val sync get() = db.syncDao()

    private suspend fun changes(): List<Triple<String, String, String>> =
        sync.getChanges(1_000).map { Triple(it.tbl, it.rowId, it.fields) }

    private fun deck(name: String = "Biology") = DeckEntity("d1", null, name, "", null, false, 1, 1)

    private fun note(fields: List<String> = listOf("q", "a")) =
        NoteEntity("n1", "d1", "type", fields, emptyList(), "Manual", 1, 1)

    private fun card() = CardEntity("c1", "n1", "d1", 0, 0, 100, null, null, null, null, 0, 0, false, false, false, null, 1, 1)

    private fun log() = ReviewLogEntity("r1", "c1", 3, 0, 1_000, 0, 1, 3_000, 2.3, 5.0, 1_000, 1_000)

    @Test
    fun aNewDatabaseHasADeviceAndSyncIsOff() = runTest {
        val state = assertNotNull(sync.getState())
        assertTrue(state.deviceId.isNotBlank())
        assertFalse(state.enabled)
        assertFalse(state.applying)
        assertEquals(0, state.clock)
        assertEquals(SYNCED_TABLES.size * 2, triggerSql().size)
    }

    @Test
    fun nothingIsRecordedWhileSyncIsOff() = runTest {
        db.deckDao().upsert(deck())
        db.deckDao().upsert(deck("Cells"))
        db.noteDao().insert(note())
        assertEquals(emptyList(), changes())
    }

    @Test
    fun anInsertRecordsTheWholeRowAtItsUpdatedAt() = runTest {
        sync.setEnabled(true)
        db.deckDao().upsert(deck().copy(updatedAt = 77))
        val change = sync.getChanges(10).single()
        assertEquals(Triple("decks", "d1", "*"), Triple(change.tbl, change.rowId, change.fields))
        assertEquals(77, change.at)
    }

    @Test
    fun anUpdateRecordsOnlyTheColumnsThatChanged() = runTest {
        sync.setEnabled(true)
        db.deckDao().upsert(deck())
        // Room's upsert is an insert that falls back to an update, not a replace: both triggers fire.
        db.deckDao().upsert(deck("Cells").copy(starred = true, updatedAt = 5))
        assertEquals(listOf(Triple("decks", "d1", "*"), Triple("decks", "d1", "name,starred")), changes())
    }

    @Test
    fun aWriteThatChangesNothingOrOnlyUpdatedAtRecordsNothing() = runTest {
        db.deckDao().upsert(deck())
        sync.setEnabled(true)
        db.deckDao().upsert(deck())
        db.deckDao().upsert(deck().copy(updatedAt = 99))
        assertEquals(emptyList(), changes())
    }

    @Test
    fun aSoftDeleteRecordsDeletedAt() = runTest {
        db.deckDao().upsert(deck())
        sync.setEnabled(true)
        db.deckDao().softDelete(listOf("d1"), now = 9)
        assertEquals(listOf(Triple("decks", "d1", "deletedAt")), changes())
        assertEquals(9, sync.getChanges(1).single().at)
    }

    @Test
    fun listColumnsAreCompared() = runTest {
        db.noteDao().insert(note())
        sync.setEnabled(true)
        db.noteDao().update(note(listOf("q", "a2")).copy(tags = listOf("t"), hint = "h", updatedAt = 2))
        assertEquals(listOf(Triple("notes", "n1", "fields,tags,hint")), changes())
    }

    @Test
    fun cardsRecordTheirScheduleAndFlags() = runTest {
        db.cardDao().insert(listOf(card()))
        sync.setEnabled(true)
        db.cardDao().update(card().copy(state = 2, due = 500, stability = 3.0, reps = 1, updatedAt = 2))
        db.cardDao().setFlagged("c1", true, now = 3)
        assertEquals(
            listOf(Triple("cards", "c1", "state,due,stability,reps"), Triple("cards", "c1", "flagged")),
            changes(),
        )
    }

    @Test
    fun reviewLogsRecordTheInsertAndTheUndo() = runTest {
        sync.setEnabled(true)
        db.reviewLogDao().insert(log())
        db.reviewLogDao().softDelete("r1", now = 2_000)
        assertEquals(listOf(Triple("review_logs", "r1", "*"), Triple("review_logs", "r1", "deletedAt")), changes())
        assertEquals(emptyList(), db.reviewLogDao().getForCards(listOf("c1")))
    }

    @Test
    fun mediaAndAnswersWithACompositeKeyAreRecorded() = runTest {
        sync.setEnabled(true)
        db.mediaDao().upsert(listOf(MediaEntity("abc", "a.png", "image/png", 3, 1, 1)))
        db.mediaDao().softDelete(listOf("abc"), now = 4)
        val answer = AiAnswerEntity("n1", "Explain", "Text", "Ollama", "llama3.2", 7, 1, 1)
        db.aiAnswerDao().upsert(answer)
        db.aiAnswerDao().upsert(answer.copy(text = "Again", updatedAt = 2))
        assertEquals(
            listOf(
                Triple("media", "abc", "*"),
                Triple("media", "abc", "deletedAt"),
                Triple("ai_answers", "n1/Explain", "*"),
                Triple("ai_answers", "n1/Explain", "text"),
            ),
            changes(),
        )
    }

    @Test
    fun nothingIsRecordedWhileRemoteChangesAreApplied() = runTest {
        sync.setEnabled(true)
        sync.setApplying(true)
        db.deckDao().upsert(deck())
        db.deckDao().upsert(deck("Cells"))
        assertEquals(emptyList(), changes())

        sync.setApplying(false)
        db.deckDao().upsert(deck("Biology"))
        assertEquals(listOf(Triple("decks", "d1", "name")), changes())
    }

    @Test
    fun theOutboxIsReadInOrderAndClearedUpToASequence() = runTest {
        sync.setEnabled(true)
        db.deckDao().upsert(deck())
        db.noteDao().insert(note())
        db.cardDao().insert(listOf(card()))
        val all = sync.getChanges(10)
        assertEquals(listOf("decks", "notes", "cards"), all.map { it.tbl })
        assertEquals(all.map { it.seq }.sorted(), all.map { it.seq })
        assertEquals(1, sync.getChanges(1).size)

        sync.deleteChangesUpTo(all[1].seq)
        assertEquals(listOf("cards"), sync.getChanges(10).map { it.tbl })
        assertEquals(1, sync.countChanges())
    }

    @Test
    fun theClockNeverGoesBackwards() = runTest {
        sync.advanceClock(1_000)
        assertEquals(1_000, sync.getClock())
        // A wall clock that is behind (or equal) still moves forward.
        sync.advanceClock(500)
        assertEquals(1_001, sync.getClock())
        sync.advanceClock(1_001)
        assertEquals(1_002, sync.getClock())
        // A remote value ahead of us is taken over; one behind changes nothing.
        sync.raiseClock(90_000)
        assertEquals(90_000, sync.getClock())
        sync.raiseClock(10)
        assertEquals(90_000, sync.getClock())
        sync.advanceClock(1_000)
        assertEquals(90_001, sync.getClock())
    }

    @Test
    fun everyColumnOfASyncedTableIsTrackedOrIsBookkeeping() = runTest {
        for (table in SYNCED_TABLES) {
            val columns = columnsOf(table.name)
            val bookkeeping = table.keyColumns.toSet() + setOf("createdAt", "updatedAt")
            assertEquals(
                columns.filterNot { it in bookkeeping }.toSet(),
                table.columns.toSet(),
                "${table.name}: a column was added to the entity or to the trigger list without the other",
            )
            assertEquals(table.columns.size, table.columns.toSet().size, "${table.name} lists a column twice")
        }
    }

    private suspend fun columnsOf(table: String): List<String> = db.useReaderConnection { connection ->
        connection.usePrepared("PRAGMA table_info($table)") { statement ->
            buildList { while (statement.step()) add(statement.getText(1)) }
        }
    }

    private suspend fun triggerSql(): List<String> = db.useReaderConnection { connection ->
        connection.usePrepared("SELECT sql FROM sqlite_master WHERE type = 'trigger' ORDER BY name") { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
    }
}
