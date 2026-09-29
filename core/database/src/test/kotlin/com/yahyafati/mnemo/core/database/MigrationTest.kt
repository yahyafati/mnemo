package com.yahyafati.mnemo.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yahyafati.mnemo.core.database.migration.ALL_MIGRATIONS
import com.yahyafati.mnemo.core.database.migration.Migration1To2
import com.yahyafati.mnemo.core.database.migration.Migration2To3
import com.yahyafati.mnemo.core.database.migration.Migration3To4
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migrations against the exported schemas in `core/database/schemas/`. Each version has a
 * `migrate<N>To<N+1>` case that creates the old schema with data, migrates, and lets
 * `runMigrationsAndValidate` compare the result with the new schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemoDatabase::class.java,
    )

    @Test
    fun exportedSchemaMatchesEntities() {
        helper.createDatabase(TEST_DB, LATEST).close()

        // Room validates the on-disk schema against the entities when it opens the database.
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
            .apply { openHelper.writableDatabase.close() }
    }

    @Test
    fun migrate1To2() = runTest {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO decks (id, parentId, name, description, category, starred, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('d1', NULL, 'Biology', '', NULL, 0, 1, 1, NULL)",
            )
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

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).close()

        // Existing rows survive, with no guid; the new table works.
        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
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
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO decks (id, parentId, name, description, category, starred, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('d1', NULL, 'Biology', '', NULL, 0, 1, 1, NULL)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration2To3).close()

        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
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
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO decks (id, parentId, name, description, category, starred, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('d1', NULL, 'Biology', '', NULL, 0, 1, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO notes (id, deckId, noteTypeId, fields, tags, source, createdAt, updatedAt, deletedAt, guid) " +
                    "VALUES ('n1', 'd1', '00000000-0000-4000-8000-000000000001', '[\"q\",\"a\"]', '[]', 'Manual', 1, 1, NULL, NULL)",
            )
            // Version 3 databases were seeded with the first three built-in types.
            NoteType.BuiltIns.take(3).forEach { type ->
                db.execSQL(
                    "INSERT INTO note_types (id, name, kind, fields, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, '[]', 0, 0, NULL)",
                    arrayOf(type.id, type.name, type.kind.name),
                )
            }
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration3To4).close()

        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
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

    private companion object {
        const val TEST_DB = "migration-test"
        const val LATEST = 4
    }
}
