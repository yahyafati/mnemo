package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmartExtractViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAiProviderRepository()

    @Test
    fun setupPromptUntilAProviderIsReady() = runTest {
        val viewModel = SmartExtractViewModel(repository)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        assertEquals(SmartExtractUiState.NeedsProvider, viewModel.uiState.value)

        // Without a model a provider isn't ready yet.
        val provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        repository.addProvider(provider)
        assertEquals(SmartExtractUiState.NeedsProvider, viewModel.uiState.value)

        repository.addProvider(provider.copy(defaultModel = "llama-3.3-70b"))
        val ready = assertIs<SmartExtractUiState.Ready>(viewModel.uiState.value)
        assertEquals(AiTask.Extract, ready.route.task)
        assertEquals("llama-3.3-70b", ready.route.modelId)
    }
}
