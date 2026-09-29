package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.generate.CardEvent
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.prompt.CardGenerationPrompt
import com.yahyafati.mnemo.core.common.dispatchers.Dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.ingest.TextChunker
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject

internal class DefaultCardGenerationRepository @Inject constructor(
    private val client: CardGenerationClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    @param:Dispatcher(MnemoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : CardGenerationRepository {
    override fun split(text: String): List<String> = TextChunker.chunk(text)

    override fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate> = flow {
        val config = configs.forProvider(route.provider)
        if (config == null) {
            emit(GenerationUpdate.Done(AiFailure(AiProblem.KeyUnavailable)))
            return@flow
        }
        val replacing = request.replacing
        val prompt = CardGenerationPrompt(
            source = request.text,
            options = request.options,
            targetCards = if (replacing != null) 1 else request.options.targetCards(SourceText.countWords(request.text)),
            avoid = request.avoid,
            title = request.title,
            part = request.part + 1,
            parts = request.parts,
            replacing = replacing?.let { it.front to it.back },
        )
        client.generate(config, route.modelId, route.capabilities, prompt).collect { event ->
            when (event) {
                is CardEvent.Card -> emit(
                    GenerationUpdate.Card(
                        GeneratedCard(
                            id = UUID.randomUUID().toString(),
                            kind = event.card.kind,
                            front = event.card.front,
                            back = event.card.back,
                            tags = event.card.tags,
                            chunkIndex = request.part,
                            wrongAnswers = event.card.wrongAnswers,
                        ),
                    ),
                )
                is CardEvent.Done -> {
                    providers.recordUsage(
                        providerId = route.provider.id,
                        task = route.task,
                        modelId = route.modelId,
                        promptTokens = event.usage.promptTokens,
                        completionTokens = event.usage.completionTokens,
                        requests = event.requests,
                    )
                    emit(GenerationUpdate.Done(event.error?.toAiFailure()))
                }
            }
        }
    }.flowOn(ioDispatcher)
}
