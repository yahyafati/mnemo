package com.yahyafati.mnemo.core.database

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yahyafati.mnemo.core.database.migration.Migration1To2
import com.yahyafati.mnemo.core.database.migration.Migration2To3
import com.yahyafati.mnemo.core.database.migration.Migration3To4
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migrations against the exported schemas in `core/database/schemas/`, on both targets. Each
 * version has a `migrate<N>To<N+1>` case that creates the old schema with data, migrates, and lets
 * `runMigrationsAndValidate` compare the result with the new schema.
 */
class MigrationTest : MigrationTestBase() {
    @Test
    fun exportedSchemaMatchesEntities() = runTest {
        createDatabase(LATEST).close()

        // Room validates the on-disk schema against the entities when it opens the database.
        open().apply {
            useReaderConnection { }
            close()
        }
    }

    @Test
    fun migrate1To2() = runTest {
        createDatabase(1).use { db ->
            db.insertDeck()
            db.execSQL(
                "INSERT INTO notes (id, deckId, noteTypeId, fields, tags, source, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('n1', 'd1', '00000000-0000-4000-8000-000000000001', '[\"q\",\"a\"]', '[\"t\"]', 'Manual', 1, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO cards (id, noteId, deckId, templateOrd, state, due, stability, difficulty, step, lastReview, " +
                    "reps, lapses, flagged, starred, suspended, buriedUntil, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('c1', 'n1', 'd1', 0, 2, 100, 3.5, 5.0, NULL, 50, 2, 0, 0, 1, 0, NULL, 1, 1, NULL)",
            )
        }

        migrate(2, Migration1To2).close()

        // Existing rows survive, with no guid; the new table works.
        val database = open()
        try {
            val note = database.noteDao().getNote("n1")!!
            assertEquals(listOf("q", "a"), note.fields)
            assertNull(note.guid)
            assertEquals(3.5, database.cardDao().getCard("c1")?.stability)
            assertEquals(emptyList(), database.mediaDao().getAll())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate2To3() = runTest {
        createDatabase(2).use { it.insertDeck() }

        migrate(3, Migration2To3).close()

        val database = open()
        try {
            assertEquals("Biology", database.deckDao().getDeck("d1")?.name)
            assertEquals(emptyList(), database.aiProviderDao().getProviders())
            assertEquals(emptyList(), database.aiProviderDao().observeUsageTotals().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate3To4() = runTest {
        createDatabase(3).use { db ->
            db.insertDeck()
            db.execSQL(
                "INSERT INTO notes (id, deckId, noteTypeId, fields, tags, source, createdAt, updatedAt, deletedAt, guid) " +
                    "VALUES ('n1', 'd1', '00000000-0000-4000-8000-000000000001', '[\"q\",\"a\"]', '[]', 'Manual', 1, 1, NULL, NULL)",
            )
            // Version 3 databases were seeded with the first three built-in types.
            NoteType.BuiltIns.take(3).forEach { type ->
                db.execSQL(
                    "INSERT INTO note_types (id, name, kind, fields, createdAt, updatedAt, deletedAt) " +
                        "VALUES ('${type.id}', '${type.name}', '${type.kind.name}', '[]', 0, 0, NULL)",
                )
            }
        }

        migrate(4, Migration3To4).close()

        val database = open()
        try {
            val note = database.noteDao().getNote("n1")!!
            assertNull(note.hint)
            assertNull(database.deckDao().getDeck("d1")?.examDate)
            val kinds = database.noteDao().getNoteTypes().map { it.kind }.toSet()
            assertEquals(NoteKind.entries.map { it.name }.toSet(), kinds)
        } finally {
            database.close()
        }
    }

    private fun SQLiteConnection.insertDeck() = execSQL(
        "INSERT INTO decks (id, parentId, name, description, category, starred, createdAt, updatedAt, deletedAt) " +
            "VALUES ('d1', NULL, 'Biology', '', NULL, 0, 1, 1, NULL)",
    )

    private companion object {
        const val LATEST = 4
    }
}
