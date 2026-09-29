package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.dispatchers.Dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal class OfflineDeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val noteDao: NoteDao,
    private val cardDao: CardDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
    @Dispatcher(MnemoDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) : DeckRepository {
    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeDecks().map { decks -> decks.map { it.toModel() } }

    override fun observeDeckSummaries(): Flow<List<DeckSummary>> {
        val now = clock.now()
        val dayEnd = StudyDay.end(now, clock.zone())
        return combine(
            observeDecks(),
            deckDao.observeDeckCounts(now.toEpochMilli(), dayEnd.toEpochMilli()),
        ) { decks, counts ->
            val countsById = counts.associateBy { it.deckId }
            val paths = paths(decks)
            decks.map { deck ->
                val c = countsById[deck.id]
                DeckSummary(
                    deck = deck,
                    path = paths.getValue(deck.id),
                    dueCount = (c?.reviewDue ?: 0) + (c?.learningDue ?: 0),
                    newCount = c?.newCount ?: 0,
                    learningCount = c?.learningDue ?: 0,
                    totalCount = c?.total ?: 0,
                    lastReviewedAt = c?.lastReviewedAt?.let(Instant::ofEpochMilli),
                )
            }
        }.flowOn(defaultDispatcher)
    }

    override fun observeDeck(id: String): Flow<Deck?> = deckDao.observeDeck(id).map { it?.toModel() }

    override suspend fun getDecks(): List<Deck> = deckDao.getDecks().map { it.toModel() }

    override suspend fun getDeck(id: String): Deck? = deckDao.getDeck(id)?.toModel()

    override suspend fun saveDeck(path: String, description: String, category: String?, id: String?, examDate: LocalDate?): String {
        val names = path.split(Deck.PATH_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        require(names.isNotEmpty()) { "A deck needs a name" }
        val cleanCategory = category?.trim()?.ifEmpty { null }

        return transaction {
            val now = clock.now()
            val decks = deckDao.getDecks().map { it.toModel() }.toMutableList()

            fun find(name: String, parentId: String?) = decks.firstOrNull {
                it.parentId == parentId && it.name.equals(name, ignoreCase = true) && it.id != id
            }

            var parentId: String? = null
            for (name in names.dropLast(1)) {
                val parent = find(name, parentId) ?: Deck(
                    id = UUID.randomUUID().toString(), name = name, parentId = parentId,
                    createdAt = now, updatedAt = now,
                ).also {
                    deckDao.upsert(it.toEntity())
                    decks += it
                }
                require(parent.id != id) { "A deck can't be inside itself" }
                parentId = parent.id
            }

            val existing = id?.let { deckId -> decks.first { it.id == deckId } } ?: find(names.last(), parentId)
            val deck = existing?.copy(
                name = names.last(),
                parentId = parentId,
                description = description.trim(),
                category = cleanCategory,
                // Saving by path onto an existing deck (not an edit by id) keeps its exam date.
                examDate = if (id != null) examDate else examDate ?: existing.examDate,
                updatedAt = now,
            ) ?: Deck(
                id = UUID.randomUUID().toString(),
                name = names.last(),
                parentId = parentId,
                description = description.trim(),
                category = cleanCategory,
                examDate = examDate,
                createdAt = now,
                updatedAt = now,
            )
            deckDao.upsert(deck.toEntity())
            deck.id
        }
    }

    override suspend fun setStarred(id: String, starred: Boolean) {
        deckDao.setStarred(id, starred, clock.now().toEpochMilli())
    }

    override suspend fun deleteDeck(id: String) = transaction {
        val decks = deckDao.getDecks()
        val ids = mutableListOf(id)
        var i = 0
        while (i < ids.size) {
            val parent = ids[i++]
            ids += decks.filter { it.parentId == parent }.map { it.id }
        }
        val now = clock.now().toEpochMilli()
        deckDao.softDelete(ids, now)
        noteDao.softDeleteInDecks(ids, now)
        cardDao.softDeleteInDecks(ids, now)
    }

    private suspend fun paths(decks: List<Deck>): Map<String, String> = withContext(defaultDispatcher) {
        val byId = decks.associateBy { it.id }
        decks.associate { deck ->
            val names = generateSequence(deck) { d -> d.parentId?.let(byId::get) }.map { it.name }.toList()
            deck.id to names.asReversed().joinToString(Deck.PATH_SEPARATOR)
        }
    }
}
