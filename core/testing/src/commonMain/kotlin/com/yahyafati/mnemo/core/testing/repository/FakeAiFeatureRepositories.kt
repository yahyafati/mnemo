package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.GenerationRequest
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.StudyAssistRepository
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.CoAuthorDeck
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile

/**
 * [CardGenerationRepository] that answers each request with [respond]. By default a request
 * returns no cards. Use [streamed] to feed a request's updates by hand, one at a time.
 */
class FakeCardGenerationRepository : CardGenerationRepository {
    val requests = mutableListOf<GenerationRequest>()
    var respond: (GenerationRequest) -> Flow<GenerationUpdate> = { flowOf(GenerationUpdate.Done()) }

    /** Splits on "---" lines, so tests can make multi-part sources. */
    override fun split(text: String): List<String> = text.split("\n---\n").map { it.trim() }.filter { it.isNotEmpty() }

    override fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate> {
        requests += request
        return respond(request)
    }

    /** Every request answers with [cards] (as copies with fresh ids), then Done. */
    fun answer(vararg cards: GeneratedCard) {
        respond = { request ->
            flowOf(*cards.map { GenerationUpdate.Card(it.copy(id = "gen-${nextId++}", chunkIndex = request.part)) }.toTypedArray(), GenerationUpdate.Done())
        }
    }

    /** Requests read from a channel the test writes to, each ending after its [GenerationUpdate.Done]. */
    fun streamed(): Channel<GenerationUpdate> {
        val channel = Channel<GenerationUpdate>(Channel.UNLIMITED)
        respond = {
            channel.receiveAsFlow().transformWhile { update ->
                emit(update)
                update !is GenerationUpdate.Done
            }
        }
        return channel
    }

    private var nextId = 1

    companion object {
        fun card(front: String, back: String = "", kind: NoteKind = NoteKind.Basic, tags: List<String> = emptyList()) =
            GeneratedCard(id = "card", kind = kind, front = front, back = back, tags = tags)
    }
}

/** [StudyAssistRepository] with canned answers. */
class FakeStudyAssistRepository : StudyAssistRepository {
    val explained = mutableListOf<Pair<StudyAssist, StudyCard>>()
    val rewritten = mutableListOf<StudyCard>()
    var explanation: (StudyAssist) -> Flow<AssistUpdate> = { flowOf(AssistUpdate.Text("An explanation."), AssistUpdate.Done()) }
    var rewrite: RewriteOutcome = RewriteOutcome.Proposed(listOf("Rewritten front", "Rewritten back"))

    override fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard): Flow<AssistUpdate> {
        explained += assist to card
        return explanation(assist)
    }

    override suspend fun rewrite(route: AiRoute, card: StudyCard): RewriteOutcome {
        rewritten += card
        return rewrite
    }
}

/** [SourceRepository] with [results] per source; dictation is driven through [dictation]. */
class FakeSourceRepository : SourceRepository {
    val results = mutableMapOf<SourceInput, SourceResult>()
    val dictation = MutableSharedFlow<DictationEvent>(extraBufferCapacity = 16)
    var dictationAvailable = true

    override suspend fun read(source: SourceInput): SourceResult =
        results[source] ?: SourceResult.Failure(SourceProblem.FileUnavailable)

    override fun isDictationAvailable(): Boolean = dictationAvailable

    override fun dictate(languageTag: String?): Flow<DictationEvent> = dictation
}

/** [CoAuthorRepository] with canned replies; records what each request was given. */
class FakeCoAuthorRepository : CoAuthorRepository {
    val chats = mutableListOf<Pair<CoAuthorDeck, List<ChatTurn>>>()
    val suggestions = mutableListOf<Pair<CoAuthorDeck, String?>>()
    val improved = mutableListOf<StudyCard>()
    var reply: (List<ChatTurn>) -> Flow<AssistUpdate> = { flowOf(AssistUpdate.Text("A reply."), AssistUpdate.Done()) }
    var suggest: () -> Flow<GenerationUpdate> = { flowOf(GenerationUpdate.Done()) }
    var improve: (StudyCard) -> RewriteOutcome = { RewriteOutcome.Proposed(listOf("Clearer ${it.note.field(0)}", it.note.field(1))) }

    override fun chat(route: AiRoute, deck: CoAuthorDeck, history: List<ChatTurn>): Flow<AssistUpdate> {
        chats += deck to history
        return reply(history)
    }

    override fun suggest(route: AiRoute, deck: CoAuthorDeck, focus: String?): Flow<GenerationUpdate> {
        suggestions += deck to focus
        return suggest()
    }

    override suspend fun improve(route: AiRoute, card: StudyCard): RewriteOutcome {
        improved += card
        return improve(card)
    }
}
