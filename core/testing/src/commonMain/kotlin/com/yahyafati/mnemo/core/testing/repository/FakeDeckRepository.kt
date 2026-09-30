package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** In-memory [DeckRepository]. Card counts come from [setCounts]; decks start with none. */
class FakeDeckRepository : DeckRepository {
    private val decks = MutableStateFlow<List<Deck>>(emptyList())
    private val counts = MutableStateFlow<Map<String, DeckCounts>>(emptyMap())
    private var nextId = 1

    data class DeckCounts(
        val due: Int = 0,
        val new: Int = 0,
        val learning: Int = 0,
        val total: Int = 0,
        val lastReviewedAt: Instant? = null,
    )

    fun setCounts(deckId: String, deckCounts: DeckCounts) = counts.update { it + (deckId to deckCounts) }

    /** Adds [deck] directly, as if it had been saved earlier. */
    fun addDeck(deck: Deck) = decks.update { it + deck }

    override fun observeDecks(): Flow<List<Deck>> = decks.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun observeDeckSummaries(): Flow<List<DeckSummary>> = combine(observeDecks(), counts) { list, countMap ->
        val byId = list.associateBy { it.id }
        list.map { deck ->
            val c = countMap[deck.id] ?: DeckCounts()
            val path = generateSequence(deck) { d -> d.parentId?.let(byId::get) }
                .map { it.name }.toList().asReversed().joinToString(Deck.PATH_SEPARATOR)
            DeckSummary(deck, path, c.due, c.new, c.learning, c.total, c.lastReviewedAt)
        }
    }

    override fun observeDeck(id: String): Flow<Deck?> = decks.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getDecks(): List<Deck> = decks.value

    override suspend fun getDeck(id: String): Deck? = decks.value.firstOrNull { it.id == id }

    override suspend fun saveDeck(path: String, description: String, category: String?, id: String?, examDate: LocalDate?): String {
        val names = path.split(Deck.PATH_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        require(names.isNotEmpty())
        var parentId: String? = null
        for (name in names.dropLast(1)) {
            parentId = decks.value.firstOrNull { it.name == name && it.parentId == parentId }?.id
                ?: newDeck(name, parentId).also { addDeck(it) }.id
        }
        val deckId = id ?: "deck-${nextId++}"
        val existing = decks.value.firstOrNull { it.id == deckId }
        val deck = (existing ?: newDeck(names.last(), parentId, deckId))
            .copy(name = names.last(), parentId = parentId, description = description, category = category, examDate = examDate)
        decks.update { list -> list.filterNot { it.id == deckId } + deck }
        return deckId
    }

    override suspend fun setStarred(id: String, starred: Boolean) =
        decks.update { list -> list.map { if (it.id == id) it.copy(starred = starred) else it } }

    override suspend fun deleteDeck(id: String) = decks.update { list ->
        val removed = mutableSetOf(id)
        var changed = true
        while (changed) {
            changed = removed.addAll(list.filter { it.parentId in removed }.map { it.id })
        }
        list.filterNot { it.id in removed }
    }

    private fun newDeck(name: String, parentId: String?, id: String = "deck-${nextId++}") =
        Deck(id = id, name = name, parentId = parentId, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
}
