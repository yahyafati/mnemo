package com.yahyafati.mnemo.core.data.transfer

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.ReviewLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream

/**
 * The whole collection as one JSON document: a readable, tool-friendly export of everything
 * (decks, notes with their cards and review history, media metadata). Media bytes stay out; the
 * Anki package and the backup carry those. Streamed note by note, so size doesn't matter.
 */
class JsonExporter internal constructor(
    private val deckDao: DeckDao,
    private val noteDao: NoteDao,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val mediaRepository: MediaRepository,
    private val clock: Clock,
) {
    suspend fun export(output: OutputStream, onProgress: suspend (Float) -> Unit = {}) {
        val writer = output.bufferedWriter()
        val decks = deckDao.getDecks().map { it.toModel() }
        val paths = AnkiExporter.paths(decks)
        writer.write("{\"format\":\"$FORMAT\",\"version\":$VERSION,\"exportedAt\":\"${clock.now()}\",\"decks\":")
        writer.write(json.encodeToString(decks.map { d -> JsonDeck(d.id, d.name, paths.getValue(d.id), d.parentId, d.description, d.category, d.starred, "${d.createdAt}", "${d.updatedAt}", d.examDate?.toString()) }))
        writer.write(",\"notes\":[")
        val total = noteDao.count().coerceAtLeast(1)
        var done = 0
        var after = 0L
        var first = true
        while (true) {
            val page = noteDao.getPage(after, AnkiExporter.PAGE)
            if (page.isEmpty()) break
            val cards = cardDao.getCardsForNotes(page.map { it.note.id }).map { it.toModel() }.groupBy { it.noteId }
            val reviews = cards.values.flatten().map { it.id }.chunked(AnkiExporter.PAGE)
                .flatMap { reviewLogDao.getForCards(it) }.map { it.toModel() }.groupBy { it.cardId }
            for (row in page) {
                val n = row.note.toModel()
                val note = JsonNote(
                    id = n.id,
                    deckId = n.deckId,
                    type = NoteType.byId(n.noteTypeId)?.kind?.name ?: n.noteTypeId,
                    fields = n.fields,
                    tags = n.tags,
                    source = n.source.name,
                    guid = n.guid,
                    hint = n.hint,
                    createdAt = "${n.createdAt}",
                    updatedAt = "${n.updatedAt}",
                    cards = cards[n.id].orEmpty().map { it.toJson(reviews[it.id].orEmpty()) },
                )
                if (!first) writer.write(",")
                first = false
                writer.write(json.encodeToString(note))
            }
            done += page.size
            after = page.last().rowId
            onProgress(done.toFloat() / total)
        }
        writer.write("],\"media\":")
        writer.write(json.encodeToString(mediaRepository.getAll().map { JsonMedia(it.id, it.name, it.mimeType, it.size) }))
        writer.write("}")
        writer.flush()
    }

    private fun Card.toJson(reviews: List<ReviewLog>) = JsonCard(
        id, deckId, templateOrd, state.name, "$due", stability, difficulty, step, lastReview?.toString(), reps, lapses,
        flagged, starred, suspended, buriedUntil?.toString(), "$createdAt", "$updatedAt",
        reviews.map { r ->
            JsonReview(r.id, r.rating.name, r.stateBefore.name, "${r.reviewedAt}", r.elapsedDays, r.scheduledDays, r.durationMs, r.stabilityAfter, r.difficultyAfter)
        },
    )

    @Serializable
    internal data class JsonDeck(
        val id: String, val name: String, val path: String, val parentId: String?, val description: String,
        val category: String?, val starred: Boolean, val createdAt: String, val updatedAt: String,
        /** ISO date (format version 2). */
        val examDate: String?,
    )

    @Serializable
    internal data class JsonNote(
        val id: String, val deckId: String, val type: String, val fields: List<String>, val tags: List<String>,
        val source: String, val guid: String?,
        /** Format version 2. */
        val hint: String?,
        val createdAt: String, val updatedAt: String, val cards: List<JsonCard>,
    )

    @Serializable
    internal data class JsonCard(
        val id: String, val deckId: String, val templateOrd: Int, val state: String, val due: String,
        val stability: Double?, val difficulty: Double?, val step: Int?, val lastReview: String?, val reps: Int,
        val lapses: Int, val flagged: Boolean, val starred: Boolean, val suspended: Boolean, val buriedUntil: String?,
        val createdAt: String, val updatedAt: String, val reviews: List<JsonReview>,
    )

    @Serializable
    internal data class JsonReview(
        val id: String, val rating: String, val stateBefore: String, val reviewedAt: String, val elapsedDays: Int,
        val scheduledDays: Int, val durationMs: Long, val stabilityAfter: Double, val difficultyAfter: Double,
    )

    @Serializable
    internal data class JsonMedia(val id: String, val name: String, val mimeType: String, val size: Long)

    internal companion object {
        const val FORMAT = "mnemo-json"
        /** 2: deck exam dates and note hints (Phase 6). */
        const val VERSION = 2
        private val json = Json { explicitNulls = true }
    }
}
