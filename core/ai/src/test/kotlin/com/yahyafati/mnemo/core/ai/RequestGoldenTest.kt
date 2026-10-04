package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.AiJson
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.StreamOptions
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.CardGenerationPrompt
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorChatPrompt
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorContext
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorSuggestPrompt
import com.yahyafati.mnemo.core.ai.prompt.DeckCardLine
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.ai.schema.GeneratedCardsSchema
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.NoteKind
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Text-only requests must reach servers byte for byte as they always did (ADR 0014): a server
 * without vision never sees the multimodal message shape. Every request type the app builds is
 * pinned here against a file in `src/test/resources/golden`.
 *
 * To re-record after an intended change to a prompt or a request, run with `MNEMO_RECORD_GOLDEN=1`
 * (and `cleanTest`, the variable is not a Gradle input) and review the diff of the files.
 */
class RequestGoldenTest {
    private val context = CoAuthorContext(
        deckName = "Cell biology",
        cards = listOf(
            DeckCardLine(NoteKind.Basic, "What do mitochondria\nmake?", "ATP"),
            DeckCardLine(NoteKind.Cloze, "The {{c1::nucleus}} holds DNA.", ""),
        ),
        totalCards = 40,
    )

    private fun request(
        messages: List<ChatMessage>,
        format: com.yahyafati.mnemo.core.ai.dto.ResponseFormat? = null,
        streaming: Boolean = false,
    ) = ChatRequest(
        model = "test-model",
        messages = messages,
        stream = true.takeIf { streaming },
        responseFormat = format,
        streamOptions = StreamOptions(includeUsage = true).takeIf { streaming },
    )

    private val smartExtract = CardGenerationPrompt(source = "Mitochondria make ATP.\nThey have two membranes.", options = ExtractOptions(), targetCards = 3)
    private val smartExtractPart = CardGenerationPrompt(
        source = "Part two: the Krebs cycle (with \\frac{a}{b}, \"quotes\" and ünïcode).",
        options = ExtractOptions(),
        targetCards = 5,
        avoid = listOf("What makes ATP?", "Where is DNA kept?"),
        title = "Cell biology",
        part = 2,
        parts = 3,
        replacing = "Old front" to "Old back",
    )

    private val requests: Map<String, ChatRequest> = mapOf(
        "smart-extract-schema" to request(smartExtract.messages(), GeneratedCardsSchema.cardsFormat),
        "smart-extract-stream" to request(smartExtract.messages(), GeneratedCardsSchema.cardsFormat, streaming = true),
        "smart-extract-part" to request(smartExtractPart.messages()),
        "smart-extract-repair" to request(
            smartExtract.messages() + ChatMessage("assistant", "Sure! Here are some cards.") + ChatMessage.user(CardGenerationPrompt.REPAIR),
            GeneratedCardsSchema.cardsFormat,
        ),
        "co-author-chat" to request(CoAuthorChatPrompt(context, listOf(true to "Is this deck good?", false to "Mostly.", true to "What is missing?")).messages(), streaming = true),
        "co-author-suggest" to request(CoAuthorSuggestPrompt(context, focus = "enzymes", count = 5).messages(), GeneratedCardsSchema.cardsFormat),
        "study-explain" to request(StudyAssistPrompt(AssistRequest.Explain, NoteKind.Basic, listOf("Q?", "A."), deckName = "Cell biology").messages(), streaming = true),
        "study-example" to request(StudyAssistPrompt(AssistRequest.Example, NoteKind.Cloze, listOf("The {{c1::nucleus}} holds DNA.", "")).messages()),
        "study-rewrite" to request(
            StudyAssistPrompt(AssistRequest.Rewrite, NoteKind.Basic, listOf("Q", "A"), weakness = StudyAssistPrompt.Weakness(9, 20)).messages(),
            GeneratedCardsSchema.rewriteFormat,
        ),
        "probe" to ChatRequest("test-model", listOf(ChatMessage.user("Reply with OK.")), maxTokens = 1),
    )

    @Test
    fun textOnlyRequestsAreUnchanged() {
        val record = System.getenv("MNEMO_RECORD_GOLDEN") == "1"
        val dir = File("src/test/resources/golden")
        for ((name, request) in requests) {
            val file = File(dir, "$name.json")
            val actual = AiJson.encodeToString(request)
            if (record) {
                dir.mkdirs()
                file.writeText(actual)
            } else {
                if (!file.exists()) fail("Missing golden file $file; record with MNEMO_RECORD_GOLDEN=1")
                assertEquals(file.readText(), actual, "request '$name' changed on the wire")
            }
        }
    }
}
