package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartExtractUseCasesTest {
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val generate = GenerateCardsUseCase(generation, cards)
    private val regenerate = RegenerateCardUseCase(generation, cards)
    private val accept = AcceptGeneratedCardsUseCase(cards)

    private val route = AiRoute(
        task = AiTask.Extract,
        provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "m", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        modelId = "m",
        capabilities = AiCapabilities(),
        usesDefault = true,
    )

    private fun request(text: String, deckId: String? = "deck", fromPart: Int = 0) =
        ExtractRequest(generate.split(text), ExtractOptions(), deckId, fromPart = fromPart)

    @Test
    fun validCardsPassAndBadOnesAreSkipped() = runTest {
        generation.answer(
            card("What is ATP?", "  Energy currency  "),
            card("Question without answer"),
            card("The {{c1::mitochondria}} make ATP.", kind = NoteKind.Cloze),
            card("A {{c1::broken cloze", kind = NoteKind.Cloze),
            card("No deletion here", kind = NoteKind.Cloze),
        )
        val events = generate(route, request("Source")).toList()

        val accepted = events.filterIsInstance<ExtractEvent.Card>().map { it.card }
        assertEquals(listOf("What is ATP?", "The {{c1::mitochondria}} make ATP."), accepted.map { it.front })
        assertEquals("Energy currency", accepted.first().back)
        assertEquals(3, events.count { it == ExtractEvent.Skipped(SkipReason.Invalid) })
        assertEquals(ExtractEvent.Finished, events.last())
    }

    @Test
    fun duplicatesOfTheDeckTheQueueAndEachOtherAreDropped() = runTest {
        cards.addNote("deck", NoteKind.Basic, listOf("What is **ATP**?", "Energy"), emptyList())
        cards.addNote("other-deck", NoteKind.Basic, listOf("Where is DNA?", "Nucleus"), emptyList())
        generation.answer(
            card("what is ATP", "Energy currency"), // in the deck
            card("Where is DNA?", "In the nucleus"), // only in another deck: fine
            card("Where is DNA?!", "Nucleus"), // repeats the card before
            card("Who found DNA?", "Watson and Crick"), // already queued
        )
        val known = listOf(card("Who found DNA?", "Watson & Crick"))
        val events = generate(route, request("Source").copy(known = known)).toList()

        assertEquals(listOf("Where is DNA?"), events.filterIsInstance<ExtractEvent.Card>().map { it.card.front })
        assertEquals(3, events.count { it == ExtractEvent.Skipped(SkipReason.Duplicate) })
        // Queued fronts are named to the model; the deck's notes are not sent.
        assertEquals(listOf("Who found DNA?"), generation.requests.single().avoid)
    }

    @Test
    fun partsRunInOrderAndAFailureCanBeResumed() = runTest {
        var part1Attempts = 0
        generation.respond = { request ->
            when (request.part) {
                0 -> flowOf(GenerationUpdate.Card(card("Q1", "A1")), GenerationUpdate.Done())
                // The first attempt at part 1 fails after one card; the retry has nothing new.
                1 -> if (part1Attempts++ == 0) {
                    flowOf(GenerationUpdate.Card(card("Q2", "A2")), GenerationUpdate.Done(AiFailure(AiProblem.Unreachable)))
                } else {
                    flowOf(GenerationUpdate.Done())
                }
                else -> flowOf(GenerationUpdate.Card(card("Q3", "A3")), GenerationUpdate.Done())
            }
        }
        val text = "Part one\n---\nPart two\n---\nPart three"
        val first = generate(route, request(text)).toList()
        assertEquals(
            listOf(
                ExtractEvent.PartStarted(0, 3), ExtractEvent.Card(card("Q1", "A1")),
                ExtractEvent.PartStarted(1, 3), ExtractEvent.Card(card("Q2", "A2")),
                ExtractEvent.Failed(1, AiFailure(AiProblem.Unreachable)),
            ),
            first,
        )
        assertEquals(listOf("Q1"), generation.requests.last().avoid)

        // Q2 made it into the queue before the failure, so the retry asks the model not to repeat it.
        generation.requests.clear()
        val retry = generate(route, request(text, fromPart = 1).copy(known = listOf(card("Q1", "A1"), card("Q2", "A2")))).toList()
        assertEquals(listOf(1, 2), generation.requests.map { it.part })
        assertEquals(listOf("Q1", "Q2"), generation.requests.first().avoid)
        assertEquals(listOf("Q3"), retry.filterIsInstance<ExtractEvent.Card>().map { it.card.front })
    }

    @Test
    fun regenerateReplacesWithSomethingNew() = runTest {
        val old = card("What is ATP?", "Energy").copy(id = "old", chunkIndex = 1)
        generation.answer(card("What is atp", "Same thing"), card("Why do cells need ATP?", "To power reactions"))
        val result = regenerate(route, old, "Part two", ExtractOptions(), deckId = null)

        assertEquals("Why do cells need ATP?", assertIs<RegenerateResult.Replaced>(result).card.front)
        val sent = generation.requests.single()
        assertEquals("Part two", sent.text)
        assertEquals(old, sent.replacing)

        generation.answer(card("What is ATP?", "Energy"))
        assertEquals(RegenerateResult.NothingNew, regenerate(route, old, "b", ExtractOptions(), deckId = null))

        generation.respond = { flowOf(GenerationUpdate.Done(AiFailure(AiProblem.RateLimited))) }
        assertEquals(RegenerateResult.Failed(AiFailure(AiProblem.RateLimited)), regenerate(route, old, "b", ExtractOptions(), deckId = null))
    }

    @Test
    fun acceptSavesValidCardsInOneTransaction() = runTest {
        val queue = listOf(
            card("What is ATP?", "Energy", tags = listOf("cells")).copy(id = "a"),
            card("{{c1::DNA}} stores {{c2::genes}}.", kind = NoteKind.Cloze).copy(id = "b"),
            card("Edited into nothing", "").copy(id = "c"),
        )
        val result = accept("deck", queue)

        assertEquals(listOf("a", "b"), result.acceptedIds)
        assertEquals(3, result.cardCount) // one basic + two cloze cards
        assertEquals(1, cards.addNotesCalls)
        val notes = cards.notes.value.values
        assertTrue(notes.all { it.source == NoteSource.Ai && it.deckId == "deck" })
        assertEquals(listOf("cells"), notes.first { it.fields[0] == "What is ATP?" }.tags)

        assertEquals(AcceptResult(emptyList(), 0), accept("deck", listOf(queue[2])))
        assertEquals(1, cards.addNotesCalls)
    }

    @Test
    fun validatorProblems() {
        assertEquals(GeneratedCardProblem.EmptyFront, GeneratedCardValidator.problem(card("  ", "x")))
        assertEquals(GeneratedCardProblem.EmptyBack, GeneratedCardValidator.problem(card("Q", " ")))
        assertEquals(GeneratedCardProblem.NoCloze, GeneratedCardValidator.problem(card("Plain", kind = NoteKind.Cloze)))
        assertEquals(GeneratedCardProblem.BrokenCloze, GeneratedCardValidator.problem(card("{{c1::a}} and {{c2::b", kind = NoteKind.Cloze)))
        assertEquals(GeneratedCardProblem.TooLong, GeneratedCardValidator.problem(card("x".repeat(2_001), "y")))
        assertNull(GeneratedCardValidator.problem(card("{{c1::a}}", kind = NoteKind.Cloze)))
        assertEquals("the mitochondria make atp", GeneratedCardValidator.key("The **{{c1::mitochondria}}** make `ATP`!"))
    }
}
