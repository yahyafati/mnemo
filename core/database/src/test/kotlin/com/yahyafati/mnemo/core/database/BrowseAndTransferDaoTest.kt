package com.yahyafati.mnemo.core.database

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.dao.BrowseQueries
import com.yahyafati.mnemo.core.database.dao.BrowseRow
import com.yahyafati.mnemo.core.database.dao.BrowseQueries.Filter
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.MediaEntity
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import com.yahyafati.mnemo.core.model.CardSort
import com.yahyafati.mnemo.core.model.CardStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class BrowseAndTransferDaoTest {
    private lateinit var db: MnemoDatabase

    @Before
    fun setUp() {
        db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    }

    @After
    fun tearDown() = db.close()

    private fun note(id: String, deckId: String, fields: List<String>, tags: List<String> = emptyList(), guid: String? = null, deleted: Boolean = false) =
        NoteEntity(id, deckId, "type", fields, tags, "Manual", createdAt = id.hashCode().toLong(), updatedAt = 0, deletedAt = if (deleted) 1 else null, guid = guid)

    private fun card(id: String, noteId: String, deckId: String, state: Int = 0, due: Long = 0, createdAt: Long = 0, suspended: Boolean = false, flagged: Boolean = false) =
        CardEntity(
            id, noteId, deckId, 0, state, due, null, null, null, null, 0, 0,
            flagged = flagged, starred = false, suspended = suspended, buriedUntil = null, createdAt = createdAt, updatedAt = 0,
        )

    private suspend fun seed() {
        db.deckDao().upsert(DeckEntity("bio", null, "Biology", "", null, false, 0, 0))
        db.deckDao().upsert(DeckEntity("jp", null, "Japanese", "", null, false, 0, 0))
        db.noteDao().insertAll(
            listOf(
                note("n1", "bio", listOf("What do mitochondria make?", "ATP"), listOf("bio::cells"), guid = "g1"),
                note("n2", "bio", listOf("100% of 50_000", "50000"), listOf("math")),
                note("n3", "jp", listOf("猫", "cat"), listOf("jp", "bio")),
                note("gone", "jp", listOf("mitochondria", "x"), deleted = true, guid = "g-gone"),
            ),
        )
        db.cardDao().insert(
            listOf(
                card("c1", "n1", "bio", state = 2, due = 50, createdAt = 1),
                card("c2", "n2", "bio", state = 0, createdAt = 2, suspended = true),
                card("c3", "n3", "jp", state = 1, due = 500, createdAt = 3, flagged = true),
                card("c4", "gone", "jp", createdAt = 4),
            ),
        )
    }

    private suspend fun ids(filter: Filter) = db.cardDao().browseIds(BrowseQueries.ids(filter)).toSet()

    @Test
    fun browseFilters() = runTest {
        seed()
        assertEquals(setOf("c1", "c2", "c3"), ids(Filter()))
        assertEquals(setOf("c1"), ids(Filter(text = "MITO")))
        assertEquals(setOf("c3"), ids(Filter(text = "猫 cat")))
        // LIKE wildcards in the search are literal.
        assertEquals(setOf("c2"), ids(Filter(text = "100%")))
        assertEquals(setOf("c2"), ids(Filter(text = "50_000")))
        // A tag matches itself and its children, not tags that merely contain it.
        assertEquals(setOf("c1", "c3"), ids(Filter(tag = "bio")))
        assertEquals(setOf("c1"), ids(Filter(tag = "bio::cells")))
        assertEquals(setOf("c3"), ids(Filter(deckIds = listOf("jp"))))
        assertEquals(emptySet(), ids(Filter(deckIds = emptyList())))
        assertEquals(setOf("c2"), ids(Filter(status = CardStatus.New)))
        assertEquals(setOf("c3"), ids(Filter(status = CardStatus.Learning)))
        assertEquals(setOf("c1"), ids(Filter(status = CardStatus.Review)))
        assertEquals(setOf("c2"), ids(Filter(status = CardStatus.Suspended)))
        assertEquals(setOf("c3"), ids(Filter(status = CardStatus.Flagged)))
        assertEquals(setOf("c1"), ids(Filter(status = CardStatus.Due, dayEnd = 100)))
        assertEquals(2, db.cardDao().browseCount(BrowseQueries.count(Filter(deckIds = listOf("bio")))).first())
    }

    @Test
    fun browsePagesJoinNotesAndDecks() = runTest {
        seed()
        val source = db.cardDao().browse(BrowseQueries.rows(Filter(sort = CardSort.DueFirst)))
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))
        val page = assertIs<PagingSource.LoadResult.Page<Int, BrowseRow>>(result)
        // Due order, new cards last.
        assertEquals(listOf("c1", "c3", "c2"), page.data.map { it.card.id })
        val first = page.data.first()
        assertEquals("Biology", first.deckName)
        assertEquals(listOf("What do mitochondria make?", "ATP"), first.note.fields)
        assertEquals("g1", first.note.guid)
    }

    @Test
    fun importLookups() = runTest {
        seed()
        assertEquals(listOf("g1"), db.noteDao().getLiveGuids(listOf("g1", "g-gone", "g-new")))
        val existing = db.noteDao().getExistingIds(listOf("n1", "gone", "new")).associate { it.id to it.deleted }
        assertEquals(mapOf("n1" to false, "gone" to true), existing)
        assertEquals(listOf("jp", "bio"), db.noteDao().getAllTagsJson().flatMap { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it.json) }.drop(2))
    }

    @Test
    fun exportPages() = runTest {
        seed()
        val first = db.noteDao().getPage(afterRowId = 0, limit = 2)
        assertEquals(listOf("n1", "n2"), first.map { it.note.id })
        assertEquals(listOf("n3"), db.noteDao().getPage(first.last().rowId, 2).map { it.note.id })
        assertEquals(listOf("n3"), db.noteDao().getPageInDecks(listOf("jp"), 0, 10).map { it.note.id })
        assertEquals(listOf("c1", "c3"), db.cardDao().getCardsForNotes(listOf("n1", "n3")).map { it.id })
    }

    @Test
    fun bulkEdits() = runTest {
        seed()
        db.cardDao().setSuspended(listOf("c1", "c3"), true, now = 9)
        assertEquals(setOf("c1", "c2", "c3"), ids(Filter(status = CardStatus.Suspended)))
        db.cardDao().setDeck(listOf("c1"), "jp", now = 9)
        assertEquals(setOf("c1", "c3"), ids(Filter(deckIds = listOf("jp"))))
        assertEquals(setOf("n1", "n3"), db.cardDao().getNoteIds(listOf("c1", "c3", "missing")).toSet())
        db.noteDao().softDelete(listOf("n1"), now = 9)
        db.cardDao().softDeleteForNotes(listOf("n1"), now = 9)
        assertEquals(setOf("c2", "c3"), ids(Filter()))
    }

    @Test
    fun mediaIsRevivedWhenStoredAgain() = runTest {
        val media = MediaEntity("a".repeat(64), "cell.png", "image/png", 3, createdAt = 1, updatedAt = 1)
        db.mediaDao().upsert(listOf(media))
        assertEquals(listOf(media.id), db.mediaDao().getIdsCreatedBefore(2))
        db.mediaDao().softDelete(listOf(media.id), now = 5)
        assertEquals(emptyList(), db.mediaDao().getAll())
        db.mediaDao().upsert(listOf(media.copy(updatedAt = 6)))
        assertEquals(listOf("cell.png"), db.mediaDao().get(listOf(media.id)).map { it.name })
    }
}
