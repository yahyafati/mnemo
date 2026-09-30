package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckSummary
import java.time.Instant

/** A deck with its subdecks. The totals include every subdeck. */
data class DeckNode(
    val summary: DeckSummary,
    val children: List<DeckNode>,
) {
    val deck: Deck get() = summary.deck
    val dueCount: Int = summary.dueCount + children.sumOf { it.dueCount }
    val newCount: Int = summary.newCount + children.sumOf { it.newCount }
    val totalCount: Int = summary.totalCount + children.sumOf { it.totalCount }
    val lastReviewedAt: Instant? = (children.map { it.lastReviewedAt } + summary.lastReviewedAt).filterNotNull().maxOrNull()

    /** This deck and all subdecks. */
    fun flatten(): List<DeckNode> = listOf(this) + children.flatMap { it.flatten() }

    companion object {
        /** Builds the forest of top-level decks, each child list sorted by name. */
        fun build(summaries: List<DeckSummary>): List<DeckNode> {
            val ids = summaries.map { it.deck.id }.toSet()
            val byParent = summaries.groupBy { it.deck.parentId?.takeIf { parent -> parent in ids } }
            fun nodes(parentId: String?): List<DeckNode> = byParent[parentId].orEmpty()
                .sortedBy { it.deck.name.lowercase() }
                .map { DeckNode(it, nodes(it.deck.id)) }
            return nodes(null)
        }

        /** [deckId] and the ids of all its subdecks. */
        fun subtreeIds(decks: List<Deck>, deckId: String): List<String> {
            val byParent = decks.groupBy { it.parentId }
            val ids = mutableListOf(deckId)
            var i = 0
            while (i < ids.size) ids += byParent[ids[i++]].orEmpty().map { it.id }
            return ids
        }
    }
}
