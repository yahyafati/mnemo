package com.yahyafati.mnemo.core.data.transfer

import androidx.sqlite.SQLiteDriver
import com.yahyafati.mnemo.core.anki.AnkiDeck
import com.yahyafati.mnemo.core.anki.AnkiImportMapper
import com.yahyafati.mnemo.core.anki.ApkgReader
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.MediaRef
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/**
 * Imports an Anki package into the collection (ARCHITECTURE §5.4). Media goes first (notes refer
 * to it), then notes in batches, each batch in one transaction with its cards and review logs, so
 * a large deck never sits in memory and a failure keeps what was already imported.
 *
 * Notes already in the collection (same Anki guid, or the same id for a package Mnemo exported)
 * are skipped. Decks are matched by full name, as Anki does, and created when missing.
 */
class AnkiImporter internal constructor(
    private val driver: SQLiteDriver,
    private val deckRepository: DeckRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: UserSettingsRepository,
    private val noteDao: NoteDao,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
) {
    /** Imports [file]; [workDir] is scratch space. [onProgress] gets 0–1. */
    suspend fun import(file: File, workDir: File, onProgress: suspend (Float) -> Unit = {}): ImportSummary {
        val settings = settingsRepository.settings.first()
        return ApkgReader(driver).open(file, workDir).use { pkg ->
            val totalNotes = pkg.noteCount.coerceAtLeast(1)
            val mediaWeight = if (pkg.media.isEmpty()) 0f else MEDIA_SHARE

            val mediaRefs = HashMap<String, String>()
            pkg.media.forEachIndexed { index, entry ->
                val stored = runCatching { pkg.openMedia(entry).use { mediaRepository.store(it, entry.name) } }.getOrNull()
                if (stored != null) mediaRefs[entry.name] = MediaRef.of(stored.id)
                if (index % PROGRESS_EVERY == 0) onProgress(mediaWeight * index / pkg.media.size)
            }

            val decks = DeckResolver(pkg.decks)
            val mapper = AnkiImportMapper(
                collectionCreated = pkg.collectionCreated,
                notetypes = pkg.notetypes,
                now = clock.now(),
                learningSteps = settings.learningSteps.size,
                relearningSteps = settings.relearningSteps.size,
            )
            var notes = 0
            var cards = 0
            var reviews = 0
            var duplicates = 0
            var skipped = 0
            var seen = 0
            for (batch in pkg.notes(BATCH)) {
                val ankiCards = pkg.cardsForNotes(batch.map { it.id })
                val revlog = pkg.revlogForCards(ankiCards.map { it.id }).groupBy { it.cardId }
                val cardsByNote = ankiCards.groupBy { it.noteId }
                decks.resolve(ankiCards.map { if (it.originalDeckId != 0L) it.originalDeckId else it.deckId }.toSet())

                val liveGuids = batch.map { it.guid }.chunked(CHUNK).flatMap { noteDao.getLiveGuids(it) }.toSet()
                val stashIds = batch.mapNotNull { mapper.mnemoIdOf(it) }
                val existing = stashIds.chunked(CHUNK).flatMap { noteDao.getExistingIds(it) }
                val liveIds = existing.filterNot { it.deleted }.map { it.id }.toSet()
                val takenIds = existing.map { it.id }.toSet()

                val imported = batch.mapNotNull { note ->
                    // Anki never keeps a note without cards; a damaged one has nothing to study.
                    val noteCards = cardsByNote[note.id] ?: return@mapNotNull null
                    val stashId = mapper.mnemoIdOf(note)
                    if (note.guid in liveGuids || (stashId != null && stashId in liveIds)) {
                        duplicates++
                        return@mapNotNull null
                    }
                    val id = stashId?.takeIf { it !in takenIds } ?: UUID.randomUUID().toString()
                    mapper.map(note, noteCards, revlog, id, decks::idFor) { mediaRefs[it] }
                        .also { if (it == null) skipped += noteCards.size }
                }
                transaction {
                    noteDao.insertAll(imported.map { it.note.toEntity() })
                    cardDao.insert(imported.flatMap { it.cards }.map { it.toEntity() })
                    reviewLogDao.insertAll(imported.flatMap { it.reviews }.map { it.toEntity() })
                }
                notes += imported.size
                cards += imported.sumOf { it.cards.size }
                reviews += imported.sumOf { it.reviews.size }
                skipped += imported.sumOf { it.skippedCards }
                seen += batch.size
                onProgress(mediaWeight + (1 - mediaWeight) * seen / totalNotes)
            }
            ImportSummary(
                decks = decks.created,
                notes = notes,
                cards = cards,
                reviews = reviews,
                media = mediaRefs.size,
                duplicateNotes = duplicates,
                skippedCards = skipped,
            )
        }
    }

    /** Anki deck ids → Mnemo deck ids, creating decks on first use. */
    private inner class DeckResolver(private val ankiDecks: List<AnkiDeck>) {
        private val byId = ankiDecks.associateBy { it.id }
        private val resolved = HashMap<Long, String>()
        var created = 0
            private set

        suspend fun resolve(ids: Set<Long>) {
            val missing = ids - resolved.keys
            if (missing.isEmpty()) return
            val existing = paths(deckRepository.getDecks())
            for (id in missing) {
                val deck = byId[id]?.takeUnless { it.filtered } ?: AnkiDeck(id, FALLBACK_DECK)
                val path = deck.name.split("::").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(Deck.PATH_SEPARATOR)
                    .ifEmpty { FALLBACK_DECK }
                val found = existing[path.lowercase()]
                resolved[id] = found ?: deckRepository.saveDeck(path, deck.description.plainDescription(), deck.mnemoCategory).also { newId ->
                    created++
                    existing[path.lowercase()] = newId
                    if (deck.mnemoStarred) deckRepository.setStarred(newId, true)
                }
            }
        }

        /** Only called for decks of cards in the current batch, which [resolve] has seen. */
        fun idFor(ankiDeckId: Long): String = checkNotNull(resolved[ankiDeckId]) { "Deck $ankiDeckId not resolved" }

        private fun paths(decks: List<Deck>): MutableMap<String, String> {
            val byDeckId = decks.associateBy { it.id }
            return decks.associate { deck ->
                val names = generateSequence(deck) { d -> d.parentId?.let(byDeckId::get) }.map { it.name }.toList()
                names.asReversed().joinToString(Deck.PATH_SEPARATOR).lowercase() to deck.id
            }.toMutableMap()
        }
    }

    private fun String.plainDescription(): String =
        com.yahyafati.mnemo.core.anki.AnkiHtml.stripHtml(this).take(MAX_DESCRIPTION)

    private companion object {
        const val BATCH = 500
        const val CHUNK = 500
        const val PROGRESS_EVERY = 20
        const val MEDIA_SHARE = 0.3f
        const val MAX_DESCRIPTION = 500
        const val FALLBACK_DECK = "Imported"
    }
}
