package com.yahyafati.mnemo.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.BrowseQueries
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineCardBrowserRepository @Inject constructor(
    private val cardDao: CardDao,
    private val noteDao: NoteDao,
    private val deckDao: DeckDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
) : CardBrowserRepository {
    override fun browse(query: CardQuery): Flow<PagingData<StudyCard>> = flow { emit(filter(query)) }.flatMapLatest { filter ->
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) { cardDao.browse(BrowseQueries.rows(filter)) }
            .flow
            .map { data ->
                data.map { row ->
                    val note = row.note.toModel()
                    // Every note uses a built-in type today; an unknown one lists as Basic.
                    val kind = NoteType.byId(note.noteTypeId)?.kind ?: NoteType.Basic.kind
                    StudyCard(row.card.toModel(), note, kind, row.deckName.orEmpty())
                }
            }
    }

    override fun count(query: CardQuery): Flow<Int> = flow { emit(filter(query)) }.flatMapLatest { cardDao.browseCount(BrowseQueries.count(it)) }

    override suspend fun cardIds(query: CardQuery): List<String> = cardDao.browseIds(BrowseQueries.ids(filter(query)))

    override suspend fun tags(): List<String> = noteDao.getAllTagsJson()
        .flatMap { runCatching { Json.decodeFromString<List<String>>(it.json) }.getOrDefault(emptyList()) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

    override suspend fun setSuspended(cardIds: Collection<String>, suspended: Boolean) = inChunks(cardIds) {
        cardDao.setSuspended(it, suspended, now())
    }

    override suspend fun setFlagged(cardIds: Collection<String>, flagged: Boolean) = inChunks(cardIds) {
        cardDao.setFlagged(it, flagged, now())
    }

    override suspend fun moveToDeck(cardIds: Collection<String>, deckId: String) = inChunks(cardIds) { chunk ->
        cardDao.setDeck(chunk, deckId, now())
        noteDao.setDeck(cardDao.getNoteIds(chunk), deckId, now())
    }

    override suspend fun addTag(cardIds: Collection<String>, tag: String) = editTags(cardIds) { tags ->
        if (tags.any { it.equals(tag, ignoreCase = true) }) tags else tags + tag
    }

    override suspend fun removeTag(cardIds: Collection<String>, tag: String) = editTags(cardIds) { tags ->
        tags.filterNot { it.equals(tag, ignoreCase = true) }
    }

    override suspend fun deleteNotes(cardIds: Collection<String>) = inChunks(cardIds) { chunk ->
        val noteIds = cardDao.getNoteIds(chunk)
        noteDao.softDelete(noteIds, now())
        cardDao.softDeleteForNotes(noteIds, now())
    }

    private suspend fun editTags(cardIds: Collection<String>, edit: (List<String>) -> List<String>) = inChunks(cardIds) { chunk ->
        for (note in noteDao.getNotes(cardDao.getNoteIds(chunk))) {
            val tags = edit(note.tags)
            if (tags != note.tags) noteDao.setTags(note.id, tags, now())
        }
    }

    private suspend fun inChunks(ids: Collection<String>, block: suspend (List<String>) -> Unit) = transaction {
        ids.toList().chunked(CHUNK).forEach { block(it) }
    }

    private suspend fun filter(query: CardQuery): BrowseQueries.Filter {
        val deckIds = query.deckId?.let { id ->
            AnkiExporter.subtree(deckDao.getDecks().map { it.toModel() }, id).map { it.id }
        }
        return BrowseQueries.Filter(
            text = query.text.trim(),
            deckIds = deckIds,
            tag = query.tag,
            status = query.status,
            sort = query.sort,
            dayEnd = StudyDay.end(clock.now(), clock.zone()).toEpochMilli(),
        )
    }

    private fun now() = clock.now().toEpochMilli()

    private companion object {
        const val PAGE_SIZE = 50
        const val CHUNK = 500
    }
}
