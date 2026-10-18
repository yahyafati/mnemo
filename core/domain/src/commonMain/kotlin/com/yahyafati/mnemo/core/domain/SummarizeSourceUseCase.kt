package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.SourceContextRepository
import com.yahyafati.mnemo.core.data.repository.SummaryRequest
import com.yahyafati.mnemo.core.data.repository.SummaryResult
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.ExtractOptions

/**
 * The context summary of a Smart Extract source (ADR 0015, C4): one request, before the first part, whose answer is
 * sent with every part. Whether a source gets one is [ExtractOptions.makesSummary]: Auto, and at least three parts.
 */
class SummarizeSourceUseCase(private val contexts: SourceContextRepository) {
    suspend operator fun invoke(route: AiRoute, text: String, title: String?, language: String?): SummaryResult =
        contexts.summarize(route, SummaryRequest(text, title, language))
}

/**
 * The summaries made in this session, so Resume, Regenerate and a second Generate on the same text don't ask again.
 * Keyed by the text, the model that wrote it and the language, since any of the three changes the summary. An edit by
 * the user replaces the entry. Like [PageTranscriptionCache], one ViewModel owns one.
 */
class SourceSummaryCache {
    private data class Key(val text: String, val model: String, val language: String?)

    private val summaries = HashMap<Key, String>()

    operator fun get(text: String, model: String, language: String?): String? = summaries[Key(text, model, language)]

    operator fun set(text: String, model: String, language: String?, summary: String) {
        summaries[Key(text, model, language)] = summary
    }

    fun clear() = summaries.clear()
}
