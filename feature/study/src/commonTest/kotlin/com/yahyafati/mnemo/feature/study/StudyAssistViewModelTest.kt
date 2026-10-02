package com.yahyafati.mnemo.feature.study

import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyAssistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = StudyTestFixture()

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private suspend fun studyCard(kind: NoteKind = NoteKind.Basic, fields: List<String> = listOf("Q", "A")): StudyCard {
        val note = fixture.cards.addNote(fixture.deckId, kind, fields, listOf("tag"))
        val card = fixture.cards.cards.value.values.first { it.noteId == note.id }
        return fixture.cards.getStudyCards(listOf(card.id)).single()
    }

    @Test
    fun onlyAvailableWithAProvider() = runTest {
        val vm = fixture.assistViewModel()
        assertFalse(vm.uiState.value.available)
        fixture.aiProviders.addProvider(provider)
        assertTrue(vm.uiState.value.available)
        // Rewrite can go to its own provider; Explain falls back to the default.
        fixture.aiProviders.addProvider(provider.copy(id = "local", name = "Ollama", sortOrder = 1))
        fixture.aiProviders.setRoute(AiTask.Rewrite, "local")
        assertEquals("local", vm.uiState.value.rewriteRoute?.provider?.id)
        assertEquals("p", vm.uiState.value.explainRoute?.provider?.id)
    }

    @Test
    fun explanationsStreamIntoTheSheet() = runTest {
        fixture.aiProviders.addProvider(provider)
        fixture.assist.explanation = {
            flowOf(AssistUpdate.Text("Mito"), AssistUpdate.Text("chondria."), AssistUpdate.Done(AiFailure(AiProblem.Unreachable)))
        }
        val vm = fixture.assistViewModel()
        val card = studyCard()
        vm.onAction(AssistAction.Open(card))
        vm.onAction(AssistAction.Run(StudyAssist.Example))

        val sheet = assertNotNull(vm.uiState.value.sheet)
        assertEquals("Mitochondria.", sheet.text)
        assertFalse(sheet.running)
        assertEquals(AiFailure(AiProblem.Unreachable), sheet.failure)
        assertEquals(StudyAssist.Example, fixture.assist.explained.single().first)

        vm.onAction(AssistAction.Back)
        assertNull(vm.uiState.value.sheet?.assist)
        vm.onAction(AssistAction.Close)
        assertNull(vm.uiState.value.sheet)
    }

    @Test
    fun aSavedAnswerOpensWithoutARequestUntilRegenerated() = runTest {
        fixture.aiProviders.addProvider(provider)
        val vm = fixture.assistViewModel()
        val card = studyCard()
        vm.onAction(AssistAction.Open(card))
        vm.onAction(AssistAction.Run(StudyAssist.Explain))
        assertEquals("An explanation.", vm.uiState.value.sheet?.text)
        assertNull(vm.uiState.value.sheet?.saved)
        assertEquals(setOf(StudyAssist.Explain), vm.uiState.value.sheet?.savedAnswers?.keys)

        // Opened again (even in a new session): the saved answer, with no request.
        val again = fixture.assistViewModel()
        again.onAction(AssistAction.Open(card))
        assertEquals(setOf(StudyAssist.Explain), again.uiState.value.sheet?.savedAnswers?.keys)
        again.onAction(AssistAction.Run(StudyAssist.Explain))
        val sheet = assertNotNull(again.uiState.value.sheet)
        assertEquals("An explanation.", sheet.text)
        assertEquals("llama-3.3-70b", sheet.saved?.modelId)
        assertFalse(sheet.running)
        assertEquals(1, fixture.assist.explained.size)

        // Example has none yet, so it asks.
        again.onAction(AssistAction.Back)
        again.onAction(AssistAction.Run(StudyAssist.Example))
        assertEquals(2, fixture.assist.explained.size)

        // Regenerate asks again and replaces the saved one.
        fixture.assist.explanation = { flowOf(AssistUpdate.Text("A better one."), AssistUpdate.Done()) }
        again.onAction(AssistAction.Back)
        again.onAction(AssistAction.Run(StudyAssist.Explain, regenerate = true))
        assertEquals(3, fixture.assist.explained.size)
        assertEquals("A better one.", again.uiState.value.sheet?.text)
        assertNull(again.uiState.value.sheet?.saved)
        assertEquals("A better one.", fixture.assist.saved.getValue(card.note.id to StudyAssist.Explain).text)
    }

    @Test
    fun aFailedAnswerIsNotSaved() = runTest {
        fixture.aiProviders.addProvider(provider)
        fixture.assist.explanation = { flowOf(AssistUpdate.Text("Mito"), AssistUpdate.Done(AiFailure(AiProblem.Unreachable))) }
        val vm = fixture.assistViewModel()
        vm.onAction(AssistAction.Open(studyCard()))
        vm.onAction(AssistAction.Run(StudyAssist.Explain))
        assertTrue(fixture.assist.saved.isEmpty())
        assertEquals(emptyMap(), vm.uiState.value.sheet?.savedAnswers)

        // "Try again" asks the provider again.
        vm.onAction(AssistAction.Run(StudyAssist.Explain, regenerate = true))
        assertEquals(2, fixture.assist.explained.size)
    }

    @Test
    fun theProviderNoticeComesFirst() = runTest {
        fixture.aiProviders.addProvider(provider.copy(disclosureAcceptedAt = null))
        val vm = fixture.assistViewModel()
        vm.onAction(AssistAction.Open(studyCard()))
        vm.onAction(AssistAction.Run(StudyAssist.Explain))
        assertEquals("p", vm.uiState.value.disclosure?.provider?.id)
        assertTrue(fixture.assist.explained.isEmpty())

        vm.onAction(AssistAction.AcceptDisclosure)
        assertEquals(1, fixture.assist.explained.size)
        assertNotNull(fixture.aiProviders.getProvider("p")?.disclosureAcceptedAt)
    }

    @Test
    fun anAppliedRewriteKeepsDeckTagsAndCards() = runTest {
        fixture.aiProviders.addProvider(provider)
        val vm = fixture.assistViewModel()
        val card = studyCard()
        fixture.assist.rewrite = RewriteOutcome.Proposed(listOf("Which organelle makes ATP?", "The mitochondrion"))
        vm.onAction(AssistAction.Open(card))
        vm.onAction(AssistAction.Run(StudyAssist.Rewrite))
        assertEquals(listOf("Which organelle makes ATP?", "The mitochondrion"), vm.uiState.value.sheet?.proposal)
        assertEquals(listOf("Q", "A"), fixture.cards.getNote(card.note.id)?.fields) // not saved yet

        vm.onAction(AssistAction.ApplyRewrite)
        val note = fixture.cards.getNote(card.note.id)!!
        assertEquals(listOf("Which organelle makes ATP?", "The mitochondrion"), note.fields)
        assertEquals(listOf("tag"), note.tags)
        assertEquals(card.card, fixture.cards.getCard(card.card.id))
        assertTrue(vm.uiState.value.sheet!!.applied)
    }

    @Test
    fun rewritesThatWouldLoseCardsCantBeApplied() = runTest {
        fixture.aiProviders.addProvider(provider)
        val vm = fixture.assistViewModel()
        val card = studyCard(NoteKind.Cloze, listOf("{{c1::ATP}} is made by {{c2::mitochondria}}.", ""))
        fixture.assist.rewrite = RewriteOutcome.Proposed(listOf("{{c1::ATP}} comes from mitochondria.", ""))
        vm.onAction(AssistAction.Open(card))
        vm.onAction(AssistAction.Run(StudyAssist.Rewrite))
        assertEquals(RewriteProblem.ClozeChanged, vm.uiState.value.sheet?.proposalProblem)

        vm.onAction(AssistAction.ApplyRewrite)
        assertEquals("{{c1::ATP}} is made by {{c2::mitochondria}}.", fixture.cards.getNote(card.note.id)?.fields?.first())

        fixture.assist.rewrite = RewriteOutcome.Proposed(listOf("Mitochondria make {{c1::ATP}} via {{c2::oxidative phosphorylation}}.", ""))
        vm.onAction(AssistAction.Run(StudyAssist.Rewrite))
        assertNull(vm.uiState.value.sheet?.proposalProblem)

        fixture.assist.rewrite = RewriteOutcome.Proposed(listOf("A question", " "))
        vm.onAction(AssistAction.Open(studyCard()))
        vm.onAction(AssistAction.Run(StudyAssist.Rewrite))
        assertEquals(RewriteProblem.Empty, vm.uiState.value.sheet?.proposalProblem)
    }
}
