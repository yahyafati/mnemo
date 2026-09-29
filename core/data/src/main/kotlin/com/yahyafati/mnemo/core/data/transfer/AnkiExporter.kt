package com.yahyafati.mnemo.core.data.transfer

import androidx.sqlite.SQLiteDriver
import com.yahyafati.mnemo.core.anki.AnkiExportMapper
import com.yahyafati.mnemo.core.anki.AnkiPackageWriter
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.dao.NoteRow
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject

/**
 * Exports decks as an Anki package (`.apkg`, legacy schema so every Anki can import it), with
 * scheduling, review history and the media the exported notes use (ADR 0003).
 */
class AnkiExporter @Inject internal constructor(
    private val driver: SQLiteDriver,
    private val deckDao: DeckDao,
    private val noteDao: NoteDao,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) {
    /**
     * Writes [deckId] and its subdecks, or the whole collection when null, to [output].
     * [workDir] is scratch space. [onProgress] gets 0–1.
     */
    suspend fun export(deckId: String?, output: OutputStream, workDir: File, onProgress: suspend (Float) -> Unit = {}) {
        val settings = settingsRepository.settings.first()
        val allDecks = deckDao.getDecks().map { it.toModel() }
        val decks = if (deckId == null) allDecks else subtree(allDecks, deckId)
        val deckIds = decks.map { it.id }.toSet()
        val paths = paths(allDecks)

        val earliest = listOfNotNull(noteDao.getEarliestCreatedAt()?.let(Instant::ofEpochMilli), decks.minOfOrNull { it.createdAt }, clock.now())
            .min()
        val mapper = AnkiExportMapper(
            collectionCreated = StudyDay.start(earliest, clock.zone()),
            desiredRetention = settings.desiredRetention,
            learningSteps = settings.learningSteps.size,
            relearningSteps = settings.relearningSteps.size,
        )
        val media = mediaRepository.getAll().associateBy { it.id }
        val names = HashMap<String, String>()
        val usedNames = HashSet<String>()
        fun nameFor(hash: String): String? = names[hash] ?: media[hash]?.let { m ->
            // Anki refers to media by name, so names must be unique in the package.
            var name = m.name.ifBlank { hash }
            if (!usedNames.add(name)) {
                name = name.substringBeforeLast('.') + "-" + hash.take(8) + name.substring(name.substringBeforeLast('.').length)
                usedNames += name
            }
            names[hash] = name
            name
        }

        AnkiPackageWriter(driver, workDir).use { writer ->
            writer.begin(mapper.collectionCreated, decks.map { mapper.deck(it, paths.getValue(it.id)) }, mapper.notetypes)
            val total = noteDao.count().coerceAtLeast(1)
            var done = 0
            var after = 0L
            while (true) {
                val page: List<NoteRow> =
                    if (deckId == null) noteDao.getPage(after, PAGE) else noteDao.getPageInDecks(deckIds.toList(), after, PAGE)
                if (page.isEmpty()) break
                val cards = cardDao.getCardsForNotes(page.map { it.note.id })
                    .map { it.toModel() }
                    .filter { it.deckId in deckIds }
                    .groupBy { it.noteId }
                val reviews = cards.values.flatten().map { it.id }.chunked(PAGE)
                    .flatMap { reviewLogDao.getForCards(it) }
                    .map { it.toModel() }
                    .groupBy { it.cardId }
                for (row in page) {
                    val note = row.note.toModel()
                    val kind = NoteType.byId(note.noteTypeId)?.kind ?: continue
                    val noteCards = cards[note.id].orEmpty().ifEmpty { continue }
                    val exported = mapper.note(note, kind, noteCards, noteCards.flatMap { reviews[it.id].orEmpty() }, ::nameFor)
                    writer.addNotes(listOf(exported.note))
                    writer.addCards(exported.cards)
                    writer.addRevlog(exported.revlog)
                }
                done += page.size
                after = page.last().rowId
                onProgress((done.toFloat() / total).coerceAtMost(0.95f))
            }
            names.forEach { (hash, name) ->
                val file = mediaRepository.file(hash)
                if (file.exists()) writer.addMedia(name) { file.inputStream() }
            }
            writer.finish(output)
        }
        onProgress(1f)
    }

    internal companion object {
        const val PAGE = 500

        fun subtree(decks: List<Deck>, deckId: String): List<Deck> {
            val byParent = decks.groupBy { it.parentId }
            val out = mutableListOf<Deck>()
            val queue = ArrayDeque(decks.filter { it.id == deckId })
            while (queue.isNotEmpty()) {
                val deck = queue.removeFirst()
                out += deck
                queue += byParent[deck.id].orEmpty()
            }
            return out
        }

        fun paths(decks: List<Deck>): Map<String, String> {
            val byId = decks.associateBy { it.id }
            return decks.associate { deck ->
                deck.id to generateSequence(deck) { d -> d.parentId?.let(byId::get) }.map { it.name }.toList()
                    .asReversed().joinToString(Deck.PATH_SEPARATOR)
            }
        }
    }
}
