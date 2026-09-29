package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.Usage
import com.yahyafati.mnemo.core.ai.parse.CardFields
import com.yahyafati.mnemo.core.ai.parse.JsonRepair
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.ai.schema.GeneratedCardsSchema
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A rewritten card's fields, with what the request cost. */
data class RewriteResult(
    val fields: MnemoResult<List<String>>,
    val usage: Usage,
    val requests: Int,
)

/** Study-time AI: explanations and examples stream as Markdown; a rewrite comes back as fields. */
class StudyAssistClient(private val runner: ChatTextRunner) {
    /** "Explain this" or "Give me an example", as text deltas and a final [TextEvent.End]. */
    fun explain(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: StudyAssistPrompt): Flow<TextEvent> {
        require(prompt.request != AssistRequest.Rewrite) { "Use rewrite()" }
        return runner.run(config, model, prompt.messages(), RequestMode.forCapabilities(capabilities, format = null))
    }

    /** "Rewrite this card": the new front and back. */
    suspend fun rewrite(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: StudyAssistPrompt): RewriteResult {
        require(prompt.request == AssistRequest.Rewrite) { "Use explain()" }
        val text = StringBuilder()
        var end: TextEvent.End? = null
        runner.run(config, model, prompt.messages(), RequestMode.forCapabilities(capabilities, GeneratedCardsSchema.rewriteFormat))
            .collect { event ->
                when (event) {
                    is TextEvent.Delta -> text.append(event.text)
                    is TextEvent.End -> end = event
                }
            }
        val finished = checkNotNull(end)
        val fields = when (val error = finished.error) {
            null -> parseFields(text.toString())?.let { MnemoResult.Success(it) }
                ?: MnemoResult.Failure(MnemoError.Parse("The rewrite wasn't a card"))
            else -> MnemoResult.Failure(error)
        }
        return RewriteResult(fields, finished.usage, finished.requests)
    }

    private fun parseFields(reply: String): List<String>? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = JsonRepair.parse(reply.substring(start, end + 1)) as? JsonObject ?: return null
        // The schema's own names first; otherwise whatever the card reader understands.
        val front = (obj["front"] as? JsonPrimitive)?.contentOrNull
        val back = (obj["back"] as? JsonPrimitive)?.contentOrNull
        if (!front.isNullOrBlank()) return listOf(CardFields.normalizeCloze(front.trim()), back?.trim().orEmpty())
        val card = CardFields.from(obj) ?: return null
        return listOf(card.front, card.back)
    }
}
