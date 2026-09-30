package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Study-time AI (PROJECT_OVERVIEW §4): explanations, examples and rewrites of the current card. */
interface StudyAssistRepository {
    /** "Explain this" or "Give me an example", streamed. Ends with one [AssistUpdate.Done]. */
    fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard): Flow<AssistUpdate>

    /** "Rewrite this card": proposed fields for the card's note. Nothing is saved. */
    suspend fun rewrite(route: AiRoute, card: StudyCard): RewriteOutcome
}

internal class DefaultStudyAssistRepository(
    private val client: StudyAssistClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
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
        client.explain(config, route.modelId, route.capabilities, prompt(request, card)).collect { event ->
            when (event) {
                is TextEvent.Delta -> emit(AssistUpdate.Text(event.text))
                is TextEvent.End -> {
                    record(route, event.usage.promptTokens, event.usage.completionTokens, event.requests)
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

    private fun prompt(request: AssistRequest, card: StudyCard) =
        StudyAssistPrompt(request, card.kind, card.note.fields, card.deckName.ifEmpty { null })

    private suspend fun record(route: AiRoute, promptTokens: Long, completionTokens: Long, requests: Int) =
        providers.recordUsage(route.provider.id, route.task, route.modelId, promptTokens, completionTokens, requests)
}
