package com.yahyafati.mnemo.feature.settings.ai

import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.data.repository.ApiKeyChange
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.ImageCheck
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAiProviderRepository()

    private fun viewModel(vararg args: Pair<String, String>) =
        ProviderEditorViewModel(SavedStateHandle(mapOf(*args)), repository)

    private val working = AiConnectionReport(
        models = listOf(AiModel("x", "llama3.2")),
        modelsFailure = null,
        testedModel = "llama3.2",
        completionFailure = null,
        capabilities = AiCapabilities(jsonOutput = true, vision = false, streaming = true),
    )

    @Test
    fun presetsFillTheForm() = runTest {
        val vm = viewModel("presetId" to "ollama")
        val state = vm.uiState.value
        assertTrue(state.isNew)
        assertEquals("Ollama", state.name)
        assertEquals("http://localhost:11434/v1", state.baseUrl)
        assertTrue(state.isLocal)
        // On a phone that's the phone itself: the editor says so.
        assertTrue(state.pointsAtThisDevice)

        // Switching presets replaces the preset's name, but not one the user typed.
        vm.onAction(ProviderEditorAction.ChoosePreset("openai"))
        assertEquals("OpenAI", vm.uiState.value.name)
        assertFalse(vm.uiState.value.isLocal)
        vm.onAction(ProviderEditorAction.Name("Work"))
        vm.onAction(ProviderEditorAction.ChoosePreset("groq"))
        assertEquals("Work", vm.uiState.value.name)
        assertTrue(vm.uiState.value.missingKey)
    }

    @Test
    fun insecureUrlsCantBeTestedOrSaved() = runTest {
        val vm = viewModel()
        vm.onAction(ProviderEditorAction.BaseUrl("http://192.168.1.20:11434/v1"))
        assertEquals(AiEndpoint.Check.Insecure(hostIsLocal = true), vm.uiState.value.urlCheck)
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(ProviderEditorAction.Save)
        vm.onAction(ProviderEditorAction.Test)
        assertTrue(repository.observeProviders().first().isEmpty())
        assertTrue(repository.tested.isEmpty())

        vm.onAction(ProviderEditorAction.Local(true))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun theFirstTestAsksBeforeSendingAnything() = runTest {
        repository.nextReport = working
        val vm = viewModel("presetId" to "custom")
        vm.onAction(ProviderEditorAction.BaseUrl("http://10.0.0.5:1234/v1/chat/completions"))
        vm.onAction(ProviderEditorAction.Local(true))
        vm.onAction(ProviderEditorAction.Test)
        assertTrue(vm.uiState.value.showDisclosure)
        assertTrue(repository.tested.isEmpty())

        vm.onAction(ProviderEditorAction.DismissDisclosure)
        assertTrue(repository.tested.isEmpty())

        vm.onAction(ProviderEditorAction.Test)
        vm.onAction(ProviderEditorAction.AcceptDisclosure)
        // The pasted endpoint path is dropped.
        assertEquals("http://10.0.0.5:1234/v1", repository.tested.single().baseUrl)
        val state = vm.uiState.value
        assertEquals(working, state.report)
        // One model on the server: it becomes the default by itself.
        assertEquals("llama3.2", state.defaultModel)
        assertEquals(working.capabilities, state.capabilities)

        // Accepted once: the next test goes straight through.
        vm.onAction(ProviderEditorAction.Test)
        assertEquals(2, repository.tested.size)
    }

    @Test
    fun changingWhereToConnectForgetsTheTest() = runTest {
        repository.nextReport = working
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.Test)
        vm.onAction(ProviderEditorAction.AcceptDisclosure)
        vm.onAction(ProviderEditorAction.DefaultModel("gpt-4.1"))
        // A different model keeps the model list…
        assertEquals(working, vm.uiState.value.report)
        vm.onAction(ProviderEditorAction.ApiKey("sk-other"))
        // …a different key doesn't.
        assertNull(vm.uiState.value.report)
    }

    @Test
    fun savingStoresTheKeyAndTheTest() = runTest {
        repository.nextReport = working
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.ApiKey(" sk-live-123 "))
        vm.onAction(ProviderEditorAction.AddHeader)
        vm.onAction(ProviderEditorAction.HeaderName(0, "OpenAI-Organization"))
        vm.onAction(ProviderEditorAction.HeaderValue(0, "org-1"))
        vm.onAction(ProviderEditorAction.AddHeader) // left blank: ignored
        vm.onAction(ProviderEditorAction.Test)
        vm.onAction(ProviderEditorAction.AcceptDisclosure)
        vm.onAction(ProviderEditorAction.Capabilities(AiCapabilities(jsonOutput = false, vision = true, streaming = true)))
        vm.onAction(ProviderEditorAction.Save)

        assertTrue(vm.uiState.value.closeRequested)
        val provider = repository.observeProviders().first().single()
        assertEquals("OpenAI", provider.name)
        assertEquals(mapOf("OpenAI-Organization" to "org-1"), provider.headers)
        assertEquals("llama3.2", provider.defaultModel)
        assertTrue(provider.hasApiKey)
        assertEquals("sk-live-123", repository.keys[provider.id])
        assertEquals(working, repository.savedReports.single())
        assertEquals(Instant.EPOCH, provider.disclosureAcceptedAt)
        val model = repository.observeModels(provider.id).first().single()
        assertEquals(AiCapabilities(jsonOutput = false, vision = true, streaming = true), model.capabilities)
        assertTrue(model.capabilitiesSetByUser)
    }

    @Test
    fun invalidHeadersBlockSaving() = runTest {
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.AddHeader)
        vm.onAction(ProviderEditorAction.HeaderName(0, "Bad Header"))
        vm.onAction(ProviderEditorAction.HeaderValue(0, "x"))
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(ProviderEditorAction.RemoveHeader(0))
        assertTrue(vm.uiState.value.canSave)
        vm.onAction(ProviderEditorAction.Timeout("2"))
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun aKeyThatCantBeStoredIsReported() = runTest {
        repository.failKeyStorage = true
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.ApiKey("sk-1"))
        vm.onAction(ProviderEditorAction.Save)
        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.closeRequested)
        assertTrue(repository.observeProviders().first().isEmpty())
    }

    @Test
    fun editingNeverShowsTheStoredKey() = runTest {
        repository.addProvider(
            AiProvider(id = "p", name = "OpenAI", baseUrl = "https://api.openai.com/v1", hasApiKey = true, defaultModel = "m", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            listOf(AiModel("p", "m", AiCapabilities(jsonOutput = true))),
        )
        repository.keys["p"] = "sk-stored"
        val vm = viewModel("providerId" to "p")
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertTrue(state.hasStoredKey)
        assertEquals("", state.apiKey)
        assertEquals(listOf("m"), state.modelChoices)
        assertEquals(AiCapabilities(jsonOutput = true), state.capabilities)

        // Saving without touching the key keeps it; removing it removes it.
        vm.onAction(ProviderEditorAction.Save)
        assertEquals("sk-stored", repository.keys["p"])
        val again = viewModel("providerId" to "p")
        again.onAction(ProviderEditorAction.RemoveKey)
        assertTrue(again.uiState.value.removeKey)
        again.onAction(ProviderEditorAction.Save)
        assertNull(repository.keys["p"])
    }

    @Test
    fun deleting() = runTest {
        repository.addProvider(AiProvider(id = "p", name = "X", baseUrl = "https://x.ai/v1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val vm = viewModel("providerId" to "p")
        vm.onAction(ProviderEditorAction.Delete)
        assertTrue(vm.uiState.value.confirmDelete)
        vm.onAction(ProviderEditorAction.ConfirmDelete)
        assertTrue(vm.uiState.value.closeRequested)
        assertIs<List<*>>(repository.observeProviders().first()).let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun aNewProviderKeepsItsIdAcrossProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val first = ProviderEditorViewModel(handle, repository).uiState.value.providerId
        assertEquals(first, ProviderEditorViewModel(handle, repository).uiState.value.providerId)
    }

    @Test
    fun theDraftSendsKeyChanges() = runTest {
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.ApiKey("sk-typed"))
        vm.onAction(ProviderEditorAction.Test)
        vm.onAction(ProviderEditorAction.AcceptDisclosure)
        assertEquals(ApiKeyChange.Set("sk-typed"), repository.tested.single().apiKey)
    }

    private fun viewModelReadyToCheck(): ProviderEditorViewModel {
        repository.nextReport = working
        val vm = viewModel("presetId" to "openai")
        vm.onAction(ProviderEditorAction.Test)
        vm.onAction(ProviderEditorAction.AcceptDisclosure)
        return vm
    }

    @Test
    fun checkingImagesNeedsAModelAndAnAcceptedNotice() = runTest {
        val vm = viewModel("presetId" to "openai")
        // Nothing to ask yet: no model, and the first-request notice not accepted.
        assertFalse(vm.uiState.value.canCheckImages)
        vm.onAction(ProviderEditorAction.CheckImages)
        assertTrue(repository.imageChecked.isEmpty())

        val ready = viewModelReadyToCheck()
        assertTrue(ready.uiState.value.canCheckImages)
    }

    @Test
    fun aCheckThatReadsTheImageSwitchesVisionOnAndSavesItWithTheProvider() = runTest {
        val vm = viewModelReadyToCheck()
        assertEquals(false, vm.uiState.value.capabilities?.vision)

        repository.nextImageCheck = ImageCheck.Reads
        vm.onAction(ProviderEditorAction.CheckImages)

        assertEquals("llama3.2", repository.imageChecked.single().defaultModel)
        val state = vm.uiState.value
        assertEquals(ImageCheck.Reads, state.imageCheck)
        assertFalse(state.checkingImages)
        // The other capabilities stay as the test found them.
        assertEquals(AiCapabilities(jsonOutput = true, vision = true, streaming = true), state.capabilities)

        vm.onAction(ProviderEditorAction.Save)
        val provider = repository.observeProviders().first().single()
        val model = repository.observeModels(provider.id).first().single()
        assertTrue(model.capabilities.vision)
        assertTrue(model.capabilitiesSetByUser)
    }

    @Test
    fun aMisreadOrARefusalSwitchesVisionOff() = runTest {
        val vm = viewModelReadyToCheck()
        vm.onAction(ProviderEditorAction.Capabilities(AiCapabilities(jsonOutput = true, vision = true, streaming = true)))

        repository.nextImageCheck = ImageCheck.Misread("I see nothing")
        vm.onAction(ProviderEditorAction.CheckImages)
        assertEquals(false, vm.uiState.value.capabilities?.vision)

        vm.onAction(ProviderEditorAction.Capabilities(AiCapabilities(vision = true)))
        repository.nextImageCheck = ImageCheck.Refused(AiFailure(AiProblem.ImagesNotAccepted))
        vm.onAction(ProviderEditorAction.CheckImages)
        assertEquals(false, vm.uiState.value.capabilities?.vision)
    }

    @Test
    fun aCheckThatProvedNothingLeavesTheCapabilitiesAlone() = runTest {
        val vm = viewModelReadyToCheck()
        vm.onAction(ProviderEditorAction.Capabilities(AiCapabilities(vision = true)))

        repository.nextImageCheck = ImageCheck.Inconclusive(AiFailure(AiProblem.Unauthorized))
        vm.onAction(ProviderEditorAction.CheckImages)

        assertEquals(true, vm.uiState.value.capabilities?.vision)
        assertEquals(ImageCheck.Inconclusive(AiFailure(AiProblem.Unauthorized)), vm.uiState.value.imageCheck)
    }

    @Test
    fun theCheckIsForgottenWhenTheModelOrTheConnectionChanges() = runTest {
        val vm = viewModelReadyToCheck()
        vm.onAction(ProviderEditorAction.CheckImages)
        assertEquals(ImageCheck.Reads, vm.uiState.value.imageCheck)

        vm.onAction(ProviderEditorAction.DefaultModel("gpt-4.1"))
        assertNull(vm.uiState.value.imageCheck)

        vm.onAction(ProviderEditorAction.CheckImages)
        assertEquals(ImageCheck.Reads, vm.uiState.value.imageCheck)
        vm.onAction(ProviderEditorAction.ApiKey("sk-other"))
        assertNull(vm.uiState.value.imageCheck)
    }
}
