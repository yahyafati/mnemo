package com.yahyafati.mnemo.core.database.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import com.yahyafati.mnemo.core.model.CardSort
import com.yahyafati.mnemo.core.model.CardStatus

/**
 * SQL for the card browser. The filter decides which clauses exist, so the queries are built
 * here, with every user value bound as an argument, and run through [CardDao]'s raw queries.
 */
object BrowseQueries {
    data class Filter(
        /** Whitespace-separated terms; each must appear in a field or tag. */
        val text: String = "",
        /** A deck subtree, or null for every deck. */
        val deckIds: List<String>? = null,
        val tag: String? = null,
        val status: CardStatus? = null,
        val sort: CardSort = CardSort.Newest,
        /** End of the study day, for [CardStatus.Due]. */
        val dayEnd: Long = 0,
    )

    fun rows(filter: Filter): SimpleSQLiteQuery {
        val (where, args) = where(filter)
        val order = when (filter.sort) {
            CardSort.Newest -> "c.createdAt DESC, c.noteId, c.templateOrd"
            CardSort.Oldest -> "c.createdAt ASC, c.noteId, c.templateOrd"
            CardSort.DueFirst -> "CASE WHEN c.state = 0 THEN 1 ELSE 0 END, c.due, c.id"
        }
        return SimpleSQLiteQuery("SELECT $COLUMNS FROM $FROM WHERE $where ORDER BY $order", args)
    }

    fun ids(filter: Filter): SimpleSQLiteQuery {
        val (where, args) = where(filter)
        return SimpleSQLiteQuery("SELECT c.id FROM $FROM WHERE $where", args)
    }

    fun count(filter: Filter): SimpleSQLiteQuery {
        val (where, args) = where(filter)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM $FROM WHERE $where", args)
    }

    private fun where(filter: Filter): Pair<String, Array<Any?>> {
        val clauses = mutableListOf("c.deletedAt IS NULL", "n.deletedAt IS NULL")
        val args = mutableListOf<Any?>()
        filter.deckIds?.let { ids ->
            if (ids.isEmpty()) {
                clauses += "0"
            } else {
                clauses += "c.deckId IN (${ids.joinToString(",") { "?" }})"
                args.addAll(ids)
            }
        }
        filter.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            // Tags are a JSON array: match the whole tag, or a child of a hierarchical one.
            clauses += "(n.tags LIKE ? ESCAPE '\\' OR n.tags LIKE ? ESCAPE '\\')"
            args += "%\"${escapeLike(tag)}\"%"
            args += "%\"${escapeLike(tag)}::%"
        }
        filter.text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { term ->
            clauses += "(n.fields LIKE ? ESCAPE '\\' OR n.tags LIKE ? ESCAPE '\\')"
            val pattern = "%${escapeLike(term)}%"
            args += pattern
            args += pattern
        }
        when (filter.status) {
            null -> Unit
            CardStatus.New -> clauses += "c.state = 0"
            CardStatus.Learning -> clauses += "c.state IN (1, 3)"
            CardStatus.Review -> clauses += "c.state = 2"
            CardStatus.Due -> {
                clauses += "c.suspended = 0 AND c.state != 0 AND c.due < ?"
                args += filter.dayEnd
            }
            CardStatus.Suspended -> clauses += "c.suspended = 1"
            CardStatus.Flagged -> clauses += "c.flagged = 1"
            CardStatus.Starred -> clauses += "c.starred = 1"
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    private fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private const val FROM = "cards c JOIN notes n ON n.id = c.noteId LEFT JOIN decks d ON d.id = c.deckId"

    private val CARD_COLUMNS = listOf(
        "id", "noteId", "deckId", "templateOrd", "state", "due", "stability", "difficulty", "step", "lastReview",
        "reps", "lapses", "flagged", "starred", "suspended", "buriedUntil", "createdAt", "updatedAt", "deletedAt",
    )
    private val NOTE_COLUMNS = listOf(
        "id", "deckId", "noteTypeId", "fields", "tags", "source", "createdAt", "updatedAt", "deletedAt", "guid",
    )

    /** Every column, prefixed to match [BrowseRow]'s embedded entities. */
    private val COLUMNS = (CARD_COLUMNS.map { "c.$it AS c_$it" } + NOTE_COLUMNS.map { "n.$it AS n_$it" } + "d.name AS deckName")
        .joinToString(", ")
}
