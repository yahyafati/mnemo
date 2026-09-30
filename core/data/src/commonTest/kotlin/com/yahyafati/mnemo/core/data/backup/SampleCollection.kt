package com.yahyafati.mnemo.core.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.test.assertEquals

/**
 * The small collection inside the fixture backups of `resources/backups`: a nested deck, a Basic
 * note with an image, one review, and a preference. [create] builds it, and [assertRestored]
 * checks a restored one has all of it. Both are for tests; the fixtures were made by [create] on
 * each platform (see `BackupFixtureGenerator`).
 */
internal object SampleCollection {
    const val IMAGE_BYTES = "png-bytes"
    const val RETENTION = 0.85

    /** Builds the collection in [db] and in [directories]' media and preferences files. */
    suspend fun create(directories: AppDirectories, db: MnemoDatabase, clock: Clock) {
        val transaction = RoomTransactionRunner(db)
        val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
        val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
        val media = FileMediaRepository(directories.media, db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined)
        val image = media.store(IMAGE_BYTES.byteInputStream(), "cell.png")
        val deck = decks.saveDeck("Biology::Cells")
        val note = cards.addNote(deck, NoteKind.Basic, listOf("What is this? ![](${MediaRef.of(image.id)})", "A cell"), listOf("bio"))
        val cardId = db.cardDao().getCardsForNotes(listOf(note.id)).single().id
        db.reviewLogDao().insert(
            ReviewLogEntity(
                id = "log-1", cardId = cardId, rating = 3, stateBefore = 0, reviewedAt = 1_000_000L, elapsedDays = 0,
                scheduledDays = 1, durationMs = 4_000, stabilityAfter = 2.3, difficultyAfter = 5.0, createdAt = 1_000_000L, updatedAt = 1_000_000L,
            ),
        )
        preferences(directories) { it.setDesiredRetention(RETENTION) }
    }

    /** What a restore must bring back. */
    suspend fun assertRestored(directories: AppDirectories, db: MnemoDatabase) {
        assertEquals(setOf("Biology", "Cells"), db.deckDao().getDecks().map { it.name }.toSet())
        val note = db.noteDao().getPage(0, 10).single().note
        assertEquals("A cell", note.fields[1])
        assertEquals(listOf("bio"), note.tags)
        assertEquals(1, db.cardDao().observeTotalCount().first())
        assertEquals(1, db.reviewLogDao().getForCards(db.cardDao().getCardsForNotes(listOf(note.id)).map { it.id }).size)
        val image = db.mediaDao().getAll().single()
        assertEquals("cell.png", image.name)
        assertEquals(IMAGE_BYTES, File(directories.media, image.id).readText())
        var retention = 0.0
        preferences(directories) { retention = it.settings.first().desiredRetention }
        assertEquals(RETENTION, retention)
    }

    /** The preferences DataStore on the file backups carry, open only for [block]. */
    private suspend fun preferences(directories: AppDirectories, block: suspend (UserPreferencesDataSource) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { directories.dataStoreFile(DataStoreFiles.USER_PREFERENCES) }
            block(UserPreferencesDataSource(store))
        } finally {
            scope.cancel()
        }
    }
}
