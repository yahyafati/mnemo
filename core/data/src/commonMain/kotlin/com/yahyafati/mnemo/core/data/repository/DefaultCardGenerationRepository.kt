package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.dto.ContentPart
import com.yahyafati.mnemo.core.ai.generate.CardEvent
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.prompt.CardGenerationPrompt
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.ingest.SourcePlanner
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.Overlap
import com.yahyafati.mnemo.core.model.PageImageBatches
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.SourcePart
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.util.UUID

internal class DefaultCardGenerationRepository(
    private val client: CardGenerationClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    private val sources: SourceRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : CardGenerationRepository {
    override fun split(text: String, root: List<String>, overlap: Overlap): List<SourcePart> = splitSource(text, root, overlap)

    override fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate> = flow {
        val config = configs.forProvider(route.provider)
        if (config == null) {
            emit(GenerationUpdate.Done(AiFailure(AiProblem.KeyUnavailable)))
            return@flow
        }
        val pages = request.pages?.let { renderPages(it) }
        if (pages != null) {
            pages.unreadable?.let {
                emit(GenerationUpdate.Done(unreadablePage = it))
                return@flow
            }
            if (pages.images.isEmpty()) {
                // Every page came out blank: there is nothing to read, so nothing is sent or paid for.
                emit(GenerationUpdate.Done())
                return@flow
            }
        }
        val prompt = cardsPrompt(request, pages?.numbers.orEmpty(), pages?.images.orEmpty())
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
                            // A page the model made up (or one of another request's) is no page.
                            page = event.card.page?.takeIf { it in pages?.numbers.orEmpty() },
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

    /** The images of [request]'s pages, or the first page that could not be drawn. Blank pages are left out. */
    private suspend fun renderPages(request: PageImages): RenderedPages {
        val numbers = mutableListOf<Int>()
        val images = mutableListOf<ContentPart.Image>()
        for (page in request.pages) {
            when (val result = sources.renderPdfPage(request.handle, page, request.quality)) {
                is PdfPageResult.Success -> {
                    // The page file is the whole page: read for this one request, never logged or kept.
                    val bytes = try {
                        result.file.readBytes()
                    } catch (e: IOException) {
                        return RenderedPages(numbers, images, UnreadablePage(page, SourceProblem.FileUnavailable))
                    }
                    numbers += page
                    images += ContentPart.Image.of(bytes)
                }
                is PdfPageResult.Failure ->
                    if (result.problem != SourceProblem.BlankPage) return RenderedPages(numbers, images, UnreadablePage(page, result.problem))
            }
        }
        return RenderedPages(numbers, images, unreadable = null)
    }

    private class RenderedPages(val numbers: List<Int>, val images: List<ContentPart.Image>, val unreadable: UnreadablePage?)
}

/** How Smart Extract splits a source into the parts sent one request each, along its headings (ADR 0015, C2). */
internal fun splitSource(text: String, root: List<String> = emptyList(), overlap: Overlap = Overlap.WithinSections): List<SourcePart> =
    SourcePlanner.plan(text, root, overlap = overlap)

/**
 * The prompt for one [request]; with page images, [pageNumbers] are the pages that rendered and [images] their pictures.
 * Shared with the QA request dump (`ExtractRequestDump`), so `scripts/qa/extract-checks.py` sends what the app sends.
 */
internal fun cardsPrompt(request: GenerationRequest, pageNumbers: List<Int> = emptyList(), images: List<ContentPart.Image> = emptyList()): CardGenerationPrompt {
    val words = SourceText.countWords(request.text)
    val replacing = request.replacing
    return CardGenerationPrompt(
        source = request.text,
        options = request.options,
        targetCards = when {
            replacing != null -> 1
            images.isNotEmpty() -> request.options.targetCards(PageImageBatches.words(images.size, words))
            else -> request.options.targetCards(words)
        },
        avoid = request.avoid,
        title = request.title,
        part = request.part + 1,
        parts = request.parts,
        path = request.source.path,
        continued = request.source.continued,
        overlap = request.source.overlap,
        context = request.context,
        replacing = replacing?.let { it.front to it.back },
        pages = pageNumbers,
        images = images,
    )
}
