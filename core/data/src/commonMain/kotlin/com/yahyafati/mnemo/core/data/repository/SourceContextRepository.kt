package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.generate.SourceSummaryClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.SourceSummaryPrompt
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.ingest.SummaryInput
import com.yahyafati.mnemo.core.ingest.TextCleanup
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The context summary of a Smart Extract source (ADR 0015, C4): one request that says what the whole source is about
 * and what its main examples illustrate, sent with every part. Nothing here touches the database except the
 * token-usage log.
 */
interface SourceContextRepository {
    /**
     * Asks [route] for the summary of [request]'s source: the whole text when it is short, else a skim of it
     * ([SummaryInput]). The tokens used are logged against the route's provider and task, even when the reply failed.
     */
    suspend fun summarize(route: AiRoute, request: SummaryRequest): SummaryResult
}

data class SummaryRequest(
    /** The whole source, as it is in the text box. */
    val text: String,
    val title: String? = null,
    /** The cards' language as an English name, which the summary is written in too; null follows the source. */
    val language: String? = null,
)

sealed interface SummaryResult {
    /** [text] is tidied, never blank. */
    data class Success(val text: String) : SummaryResult

    data class Failure(val failure: AiFailure) : SummaryResult
}

internal class DefaultSourceContextRepository(
    private val client: SourceSummaryClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : SourceContextRepository {
    override suspend fun summarize(route: AiRoute, request: SummaryRequest): SummaryResult = withContext(ioDispatcher) {
        val config = configs.forProvider(route.provider) ?: return@withContext SummaryResult.Failure(AiFailure(AiProblem.KeyUnavailable))
        val input = SummaryInput.of(request.text)
        val prompt = SourceSummaryPrompt(input.text, request.title, input.skimmed, request.language)
        val reply = StringBuilder()
        var failure: AiFailure? = null
        var promptTokens = 0L
        var completionTokens = 0L
        var requests = 0
        client.summarize(config, route.modelId, route.capabilities, prompt).collect { event ->
            when (event) {
                is TextEvent.Delta -> reply.append(event.text)
                is TextEvent.End -> {
                    promptTokens = event.usage.promptTokens
                    completionTokens = event.usage.completionTokens
                    requests = event.requests
                    failure = event.error?.toAiFailure()
                }
            }
        }
        providers.recordUsage(route.provider.id, route.task, route.modelId, promptTokens, completionTokens, requests)
        failure?.let { return@withContext SummaryResult.Failure(it) }
        val text = SourceSummaryPrompt.clean(TextCleanup.normalizeMarkdown(reply.toString()))
        if (text.isBlank()) SummaryResult.Failure(AiFailure(AiProblem.InvalidResponse)) else SummaryResult.Success(text)
    }
}
