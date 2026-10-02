package com.yahyafati.mnemo.core.database

import androidx.room.useReaderConnection
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Databases of every schema version, made by the Android app (`resources/fixtures`), open and
 * migrate to the current version on both targets with their data intact. This is what a desktop
 * install sees when it is handed a collection made on the phone.
 */
class FixtureDatabasesTest : MigrationTestBase() {
    private fun openFixture(version: Int): MnemoDatabase {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/mnemo-v$version.db")) { "No fixture for v$version" }
        stream.use { input -> databaseFile.outputStream().use { input.copyTo(it) } }
        return open()
    }

    @Test
    fun everyVersionMigratesToTheCurrentOne() = runTest {
        for (version in 1..5) {
            val db = openFixture(version)
            try {
                assertEquals("Biology", db.deckDao().getDeck("d1")?.name, "v$version deck")
                val note = assertNotNull(db.noteDao().getNote("n1"), "v$version note")
                assertEquals(2, note.fields.size, "v$version fields")
                assertEquals(3.5, db.cardDao().getCard("c1")?.stability, "v$version card")
                // Version 4 seeded the two card types the older databases lack.
                val types = db.noteDao().getNoteTypes().map { it.id }
                assertTrue(NoteType.TypeIn.id in types && NoteType.MultipleChoice.id in types, "v$version types")
                assertEquals(5, DatabaseSnapshot(db).version(), "v$version schema version")
            } finally {
                db.close()
                databaseFile.delete()
            }
        }
    }

    @Test
    fun aCurrentDatabaseKeepsItsRowsAndAcceptsWrites() = runTest {
        val db = openFixture(5)
        try {
            assertEquals(listOf("bio", "cells::organelles"), db.noteDao().getNote("n1")?.tags)
            assertEquals("Starts with M", db.noteDao().getNote("n1")?.hint)
            assertEquals(1, db.reviewLogDao().getForCards(listOf("c1")).size)
            assertEquals(listOf("Mitochondria make ATP."), db.aiAnswerDao().getForNote("n1").map { it.text })
            db.deckDao().getDeck("d1")!!.let { db.deckDao().upsert(it.copy(name = "Cells")) }
        } finally {
            db.close()
        }

        val reopened = open()
        try {
            assertEquals("Cells", reopened.deckDao().getDeck("d1")?.name)
            reopened.useReaderConnection { }
        } finally {
            reopened.close()
        }
    }
}
