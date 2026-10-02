package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.database.dao.AiAnswerDao
import com.yahyafati.mnemo.core.database.entity.AiAnswerEntity
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.SavedAssistAnswer
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

/** Study-time AI (PROJECT_OVERVIEW §4): explanations, examples and rewrites of the current card. */
interface StudyAssistRepository {
    /**
     * "Explain this" or "Give me an example", streamed. Ends with one [AssistUpdate.Done]. A
     * complete answer is saved for the card's note, replacing the one before.
     */
    fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard): Flow<AssistUpdate>

    /** "Rewrite this card": proposed fields for the card's note. Nothing is saved. */
    suspend fun rewrite(route: AiRoute, card: StudyCard): RewriteOutcome

    /** The saved Explain and Example answers for the card's note. */
    suspend fun savedAnswers(card: StudyCard): Map<StudyAssist, SavedAssistAnswer>
}

internal class DefaultStudyAssistRepository(
    private val client: StudyAssistClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    private val answers: AiAnswerDao,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : StudyAssistRepository {
    override fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard): Flow<AssistUpdate> = flow {
        require(assist != StudyAssist.Rewrite) { "Use rewrite()" }
        val config = configs.forProvider(route.provider)
        if (config == null) {
            emit(AssistUpdate.Done(AiFailure(AiProblem.KeyUnavailable)))
            return@flow
        }
        val request = if (assist == StudyAssist.Explain) AssistRequest.Explain else AssistRequest.Example
        val text = StringBuilder()
        client.explain(config, route.modelId, route.capabilities, prompt(request, card)).collect { event ->
            when (event) {
                is TextEvent.Delta -> {
                    text.append(event.text)
                    emit(AssistUpdate.Text(event.text))
                }
                is TextEvent.End -> {
                    record(route, event.usage.promptTokens, event.usage.completionTokens, event.requests)
                    // Only a whole answer is kept: one cut short would come back as if it were complete.
                    if (event.error == null && text.isNotBlank()) save(route, assist, card, text.toString())
                    emit(AssistUpdate.Done(event.error?.toAiFailure()))
                }
            }
        }
    }.flowOn(ioDispatcher)

    override suspend fun rewrite(route: AiRoute, card: StudyCard): RewriteOutcome = withContext(ioDispatcher) {
        val config = configs.forProvider(route.provider) ?: return@withContext RewriteOutcome.Failed(AiFailure(AiProblem.KeyUnavailable))
        val result = client.rewrite(config, route.modelId, route.capabilities, prompt(AssistRequest.Rewrite, card))
        record(route, result.usage.promptTokens, result.usage.completionTokens, result.requests)
        when (val fields = result.fields) {
            // A rewrite only proposes the question and answer; other fields (wrong options) stay.
            is MnemoResult.Success -> RewriteOutcome.Proposed(fields.data + card.note.fields.drop(fields.data.size))
            is MnemoResult.Failure -> RewriteOutcome.Failed(fields.error.toAiFailure())
        }
    }

    override suspend fun savedAnswers(card: StudyCard): Map<StudyAssist, SavedAssistAnswer> = withContext(ioDispatcher) {
        val hash = fieldsHash(card)
        answers.getForNote(card.note.id).mapNotNull { row ->
            val assist = StudyAssist.entries.firstOrNull { it.name == row.kind && it != StudyAssist.Rewrite } ?: return@mapNotNull null
            assist to SavedAssistAnswer(row.text, row.providerName, row.modelId, Instant.ofEpochMilli(row.updatedAt), outdated = row.fieldsHash != hash)
        }.toMap()
    }

    private suspend fun save(route: AiRoute, assist: StudyAssist, card: StudyCard, text: String) {
        val now = clock.now().toEpochMilli()
        answers.upsert(AiAnswerEntity(card.note.id, assist.name, text, route.provider.name, route.modelId, fieldsHash(card), now, now))
    }

    /** What the answer is about: an edit to the note's fields makes it out of date (deck and tags don't). */
    private fun fieldsHash(card: StudyCard) = card.note.fields.hashCode()

    private fun prompt(request: AssistRequest, card: StudyCard) =
        StudyAssistPrompt(request, card.kind, card.note.fields, card.deckName.ifEmpty { null })

    private suspend fun record(route: AiRoute, promptTokens: Long, completionTokens: Long, requests: Int) =
        providers.recordUsage(route.provider.id, route.task, route.modelId, promptTokens, completionTokens, requests)
}
