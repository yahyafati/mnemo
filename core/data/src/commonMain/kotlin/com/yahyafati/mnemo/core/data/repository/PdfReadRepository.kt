package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ContentPart
import com.yahyafati.mnemo.core.ai.generate.PageTranscriptionClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.PageTranscriptionPrompt
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.ingest.TextCleanup
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reading the pages of a PDF with a vision model (docs/pdf/ROADMAP.md, P5; ADR 0014). Nothing here touches the
 * database except the token-usage log; the text goes to Smart Extract's box, where the user can fix it.
 */
interface PdfReadRepository {
    /**
     * Renders [page] of [handle] at [quality], sends the image to [route] and returns what the model read, as
     * Markdown. The tokens it used are logged against the route's provider and task. A page that renders blank is a
     * [PageReadFailure.Page] with [SourceProblem.BlankPage]: the caller decides whether that is an empty page.
     */
    suspend fun transcribe(route: AiRoute, handle: PdfHandle, page: Int, quality: PdfQuality): PageTranscription
}

sealed interface PageTranscription {
    /** [text] is tidied Markdown, never blank. */
    data class Success(val text: String) : PageTranscription

    data class Failure(val failure: PageReadFailure) : PageTranscription
}

/** Why a page couldn't be read: the page couldn't be drawn, or the provider failed. */
sealed interface PageReadFailure {
    data class Page(val problem: SourceProblem, val detail: String? = null) : PageReadFailure

    data class Ai(val failure: AiFailure) : PageReadFailure
}

internal class DefaultPdfReadRepository(
    private val sources: SourceRepository,
    private val client: PageTranscriptionClient,
    private val configs: ProviderConfigs,
    private val providers: AiProviderRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfReadRepository {
    override suspend fun transcribe(route: AiRoute, handle: PdfHandle, page: Int, quality: PdfQuality): PageTranscription = withContext(ioDispatcher) {
        val config: ProviderConfig = configs.forProvider(route.provider) ?: return@withContext PageTranscription.Failure(PageReadFailure.Ai(AiFailure(AiProblem.KeyUnavailable)))
        val file = when (val rendered = sources.renderPdfPage(handle, page, quality)) {
            is PdfPageResult.Success -> rendered.file
            is PdfPageResult.Failure -> return@withContext PageTranscription.Failure(PageReadFailure.Page(rendered.problem, rendered.detail))
        }
        // The page file is the whole page: it is read here for this one request and never logged or kept.
        val image = try {
            ContentPart.Image.of(file.readBytes())
        } catch (e: IOException) {
            return@withContext PageTranscription.Failure(PageReadFailure.Page(SourceProblem.FileUnavailable))
        }
        val reply = StringBuilder()
        var failure: AiFailure? = null
        var promptTokens = 0L
        var completionTokens = 0L
        var requests = 0
        client.transcribe(config, route.modelId, route.capabilities, PageTranscriptionPrompt(page, image)).collect { event ->
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
        // Logged even when the reply failed or was cut: the provider billed what it read.
        providers.recordUsage(route.provider.id, route.task, route.modelId, promptTokens, completionTokens, requests)
        failure?.let { return@withContext PageTranscription.Failure(PageReadFailure.Ai(it)) }
        val text = TextCleanup.normalizeMarkdown(PageTranscriptionPrompt.clean(reply.toString()))
        if (text.isBlank()) {
            PageTranscription.Failure(PageReadFailure.Ai(AiFailure(AiProblem.InvalidResponse)))
        } else {
            PageTranscription.Success(text)
        }
    }
}
