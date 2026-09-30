package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.DuplicateGroup
import com.yahyafati.mnemo.core.model.Note

/**
 * AI Co-Author's "Find duplicates", done on the device: nothing is sent anywhere. Looks at [deckId]
 * and its subdecks.
 */
class FindDuplicateNotesUseCase(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) {
    suspend operator fun invoke(deckId: String): List<DuplicateGroup> {
        val ids = DeckNode.subtreeIds(deckRepository.getDecks(), deckId)
        return DuplicateFinder.find(cardRepository.getNotesInDecks(ids, MAX_NOTES))
    }

    private companion object {
        const val MAX_NOTES = 20_000
    }
}

/**
 * Groups notes that ask the same thing: the same question once case, punctuation, Markdown and
 * cloze markup are ignored ([GeneratedCardValidator.key]), or nearly the same words (Jaccard
 * similarity of at least [NEAR]). Only notes that share an uncommon word are compared, so a deck
 * of thousands of notes takes milliseconds rather than comparing every pair.
 */
object DuplicateFinder {
    /** Word overlap from which two questions count as the same. */
    const val NEAR = 0.8

    /** Questions with fewer words than this are only matched exactly: "What is X?" pairs are too alike. */
    private const val MIN_WORDS_FOR_NEAR = 4

    /** Words in more notes than this don't pick candidates: they are the deck's common words. */
    private const val MAX_POSTINGS = 60

    fun find(notes: List<Note>): List<DuplicateGroup> {
        val keys = notes.map { GeneratedCardValidator.key(it.field(0)) }
        val words = keys.map { key -> key.split(' ').filter { it.length > 1 }.toSet() }
        val parent = IntArray(notes.size) { it }
        val similarity = DoubleArray(notes.size) { 1.0 }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        fun join(a: Int, b: Int, score: Double) {
            val ra = root(a)
            val rb = root(b)
            if (ra == rb) return
            parent[rb] = ra
            similarity[ra] = minOf(similarity[ra], similarity[rb], score)
        }

        // Same text.
        keys.indices.filter { keys[it].isNotEmpty() }.groupBy { keys[it] }.values.forEach { same ->
            same.drop(1).forEach { join(same.first(), it, 1.0) }
        }

        // Nearly the same words.
        val postings = HashMap<String, MutableList<Int>>()
        words.forEachIndexed { i, set -> if (set.size >= MIN_WORDS_FOR_NEAR) set.forEach { postings.getOrPut(it) { mutableListOf() } += i } }
        val compared = HashSet<Long>()
        for (list in postings.values) {
            if (list.size < 2 || list.size > MAX_POSTINGS) continue
            for (x in list.indices) {
                for (y in x + 1 until list.size) {
                    val a = list[x]
                    val b = list[y]
                    if (!compared.add(a.toLong() shl 32 or b.toLong())) continue
                    val score = jaccard(words[a], words[b])
                    if (score >= NEAR) join(a, b, score)
                }
            }
        }

        return notes.indices.groupBy(::root).values
            .filter { it.size > 1 }
            .map { members -> DuplicateGroup(members.map { notes[it] }.sortedBy { it.createdAt }, similarity[root(members.first())]) }
            .sortedWith(compareByDescending<DuplicateGroup> { it.similarity }.thenBy { it.notes.first().createdAt })
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        val shared = a.count { it in b }
        return shared.toDouble() / (a.size + b.size - shared)
    }
}
