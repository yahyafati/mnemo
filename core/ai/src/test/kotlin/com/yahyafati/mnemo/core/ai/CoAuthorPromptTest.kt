package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorChatPrompt
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorContext
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorSuggestPrompt
import com.yahyafati.mnemo.core.ai.prompt.DeckCardLine
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.model.NoteKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoAuthorPromptTest {
    private val context = CoAuthorContext(
        deckName = "Cell biology",
        cards = listOf(
            DeckCardLine(NoteKind.Basic, "What do mitochondria\nmake?", "ATP"),
            DeckCardLine(NoteKind.Cloze, "The {{c1::nucleus}} holds DNA.", ""),
        ),
        totalCards = 40,
    )

    @Test
    fun chatSendsTheDeckFencedAndTheRecentTurns() {
        val history = (1..20).map { (it % 2 == 1) to "turn $it" }
        val messages = CoAuthorChatPrompt(context, history).messages()
        val system = messages.first()
        assertEquals("system", system.role)
        assertTrue("<deck>\n- [basic] What do mitochondria make? | ATP\n- [cloze] The {{c1::nucleus}} holds DNA.\n</deck>" in system.content!!)
        assertTrue("(2 of its 40 cards shown)" in system.content!!)
        assertEquals(1 + CoAuthorChatPrompt.MAX_TURNS, messages.size)
        assertEquals("turn 20", messages.last().content)
        assertEquals("assistant", messages.last().role)
    }

    @Test
    fun suggestionsAskForTheCardFormatAndTheFocus() {
        val messages = CoAuthorSuggestPrompt(context, focus = "enzymes", count = 5).messages()
        assertTrue("\"cards\"" in messages.first().content!!)
        val user = messages.last().content!!
        assertTrue(user.startsWith("Suggest 5 new cards for this deck. Focus on: enzymes."))
        assertTrue("<deck>" in user)
    }

    @Test
    fun rewritesOfWeakCardsMentionTheirLapses() {
        val prompt = StudyAssistPrompt(AssistRequest.Rewrite, NoteKind.Basic, listOf("Q", "A"), weakness = StudyAssistPrompt.Weakness(9, 20))
        assertTrue("forgotten this card 9 times in 20 reviews" in prompt.messages().last().content!!)
    }
}
