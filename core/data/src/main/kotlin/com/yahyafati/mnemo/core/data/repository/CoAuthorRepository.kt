package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.generate.CardEvent
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.CoAuthorClient
import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorChatPrompt
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorContext
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorSuggestPrompt
import com.yahyafati.mnemo.core.ai.prompt.DeckCardLine
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.common.dispatchers.Dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.CoAuthorDeck
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.markdown.Markdown
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * AI Co-Author (PROJECT_OVERVIEW §4.3): chat about a deck, suggest the cards it's missing, and
 * rewrite the ones the learner keeps forgetting. What is sent: the deck's name and its notes as
 * plain text, at most [CoAuthorDeck.MAX_NOTES_SENT] of them, each side cut to
 * [CoAuthorDeck.MAX_SIDE_CHARS]; for a rewrite, that one card and its lapse count. Duplicates are
 * found on the device (`FindDuplicateNotesUseCase`), with no request at all.
 */
interface CoAuthorRepository {
    /** A chat reply about [deck], streamed. [history] ends with the user's new message. Ends with one [AssistUpdate.Done]. */
    fun chat(route: AiRoute, deck: CoAuthorDeck, history: List<ChatTurn>): Flow<AssistUpdate>

    /** New cards [deck] is missing, as they arrive, then one [GenerationUpdate.Done]. Nothing is saved. */
    fun suggest(route: AiRoute, deck: CoAuthorDeck, focus: String?): Flow<GenerationUpdate>

    /** A clearer version of a card the learner keeps forgetting: proposed fields, not saved. */
    suspend fun improve(route: AiRoute, card: StudyCard): RewriteOutcome
}

internal class DefaultCoAuthorRepository @Inject constructor(
    private val coAuthorClient: CoAuthorClient,
    private val cardClient: CardGenerationClient,
    private val assistClient: StudyAssistClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    @param:Dispatcher(MnemoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : CoAuthorRepository {
    override fun chat(route: AiRoute, deck: CoAuthorDeck, history: List<ChatTurn>): Flow<AssistUpdate> = flow {
        val config = configs.forProvider(route.provider)
        if (config == null) {
            emit(AssistUpdate.Done(AiFailure(AiProblem.KeyUnavailable)))
            return@flow
        }
        val prompt = CoAuthorChatPrompt(context(deck), history.map { it.fromUser to it.text })
        coAuthorClient.chat(config, route.modelId, route.capabilities, prompt).collect { event ->
            when (event) {
                is TextEvent.Delta -> emit(AssistUpdate.Text(event.text))
                is TextEvent.End -> {
                    record(route, event.usage.promptTokens, event.usage.completionTokens, event.requests)
                    emit(AssistUpdate.Done(event.error?.toAiFailure()))
                }
            }
        }
    }.flowOn(ioDispatcher)

    override fun suggest(route: AiRoute, deck: CoAuthorDeck, focus: String?): Flow<GenerationUpdate> = flow {
        val config = configs.forProvider(route.provider)
        if (config == null) {
            emit(GenerationUpdate.Done(AiFailure(AiProblem.KeyUnavailable)))
            return@flow
        }
        cardClient.generate(config, route.modelId, route.capabilities, CoAuthorSuggestPrompt(context(deck), focus)).collect { event ->
            when (event) {
                is CardEvent.Card -> emit(
                    GenerationUpdate.Card(
                        GeneratedCard(
                            id = UUID.randomUUID().toString(),
                            kind = event.card.kind,
                            front = event.card.front,
                            back = event.card.back,
                            tags = event.card.tags,
                            wrongAnswers = event.card.wrongAnswers,
                        ),
                    ),
                )
                is CardEvent.Done -> {
                    record(route, event.usage.promptTokens, event.usage.completionTokens, event.requests)
                    emit(GenerationUpdate.Done(event.error?.toAiFailure()))
                }
            }
        }
    }.flowOn(ioDispatcher)

    override suspend fun improve(route: AiRoute, card: StudyCard): RewriteOutcome = withContext(ioDispatcher) {
        val config = configs.forProvider(route.provider) ?: return@withContext RewriteOutcome.Failed(AiFailure(AiProblem.KeyUnavailable))
        val prompt = StudyAssistPrompt(
            request = AssistRequest.Rewrite,
            kind = card.kind,
            fields = card.note.fields,
            deckName = card.deckName.ifEmpty { null },
            weakness = StudyAssistPrompt.Weakness(lapses = card.card.lapses, reviews = card.card.reps),
        )
        val result = assistClient.rewrite(config, route.modelId, route.capabilities, prompt)
        record(route, result.usage.promptTokens, result.usage.completionTokens, result.requests)
        when (val fields = result.fields) {
            is MnemoResult.Success -> RewriteOutcome.Proposed(fields.data + card.note.fields.drop(fields.data.size))
            is MnemoResult.Failure -> RewriteOutcome.Failed(fields.error.toAiFailure())
        }
    }

    private suspend fun record(route: AiRoute, promptTokens: Long, completionTokens: Long, requests: Int) =
        providers.recordUsage(route.provider.id, route.task, route.modelId, promptTokens, completionTokens, requests)

    internal companion object {
        /** The deck as plain text lines, sampled evenly when it has more notes than are sent. */
        fun context(deck: CoAuthorDeck): CoAuthorContext {
            val notes = deck.notes
            val sent = if (notes.size <= CoAuthorDeck.MAX_NOTES_SENT) {
                notes
            } else {
                val step = notes.size.toDouble() / CoAuthorDeck.MAX_NOTES_SENT
                List(CoAuthorDeck.MAX_NOTES_SENT) { notes[(it * step).toInt()] }
            }
            val lines = sent.map { note ->
                val kind = NoteType.byId(note.noteTypeId)?.kind ?: NoteKind.Basic
                // Cloze text keeps its markup, so the model can see what is being tested.
                val front = if (kind == NoteKind.Cloze) note.field(0).trim() else Markdown.plainText(note.field(0))
                DeckCardLine(kind, front.take(CoAuthorDeck.MAX_SIDE_CHARS), Markdown.plainText(note.field(1)).take(CoAuthorDeck.MAX_SIDE_CHARS))
            }
            return CoAuthorContext(deck.name, lines, totalCards = notes.size)
        }
    }
}
