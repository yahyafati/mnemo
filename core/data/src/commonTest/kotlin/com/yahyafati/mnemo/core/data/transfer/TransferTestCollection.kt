package com.yahyafati.mnemo.core.data.transfer

import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import com.yahyafati.mnemo.core.testing.testSqliteDriver
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers

/** A whole collection (in-memory database + media directory) with the real importer and exporters. */
internal class TransferTestCollection(root: File, val clock: TestClock = TestClock(Instant.now())) : AutoCloseable {
    val db = inMemoryDatabase()
    private val transaction = RoomTransactionRunner(db)
    val settings = FakeUserSettingsRepository()
    val media = FileMediaRepository(File(root, "media"), db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined)
    val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    val importer = AnkiImporter(
        testSqliteDriver(), decks, media, settings, db.noteDao(), db.cardDao(), db.reviewLogDao(), transaction, clock,
    )
    val exporter = AnkiExporter(
        testSqliteDriver(), db.deckDao(), db.noteDao(), db.cardDao(), db.reviewLogDao(), media, settings, clock,
    )
    val json = JsonExporter(db.deckDao(), db.noteDao(), db.cardDao(), db.reviewLogDao(), media, clock)

    override fun close() = db.close()

    companion object {
        /** A package from `:core:anki`'s fixtures, written by real Anki. */
        fun fixture(name: String): File = File("../anki/src/test/resources/$name").also { check(it.exists()) { it.absolutePath } }
    }
}
