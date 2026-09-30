package com.yahyafati.mnemo.feature.settings.ai

import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiProvidersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAiProviderRepository()
    private val viewModel by lazy { AiProvidersViewModel(repository) }

    private fun provider(id: String, sortOrder: Int, model: String? = "m-$id") =
        AiProvider(id = id, name = id.uppercase(), baseUrl = "https://$id.example/v1", defaultModel = model, sortOrder = sortOrder, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun runWithState(block: suspend () -> Unit) = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        block()
    }

    @Test
    fun theFirstReadyProviderIsTheDefault() = runWithState {
        repository.addProvider(provider("a", 0, model = null))
        repository.addProvider(provider("b", 1))
        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("b", state.defaultProviderId)
        assertTrue(AiTask.entries.all { state.effective[it]?.provider?.id == "b" })
    }

    @Test
    fun reorderToggleAndRoute() = runWithState {
        repository.addProvider(provider("a", 0))
        repository.addProvider(provider("b", 1))
        viewModel.onAction(AiProvidersAction.Move("b", -1))
        assertEquals(listOf("b", "a"), viewModel.uiState.value.providers.map { it.id })
        assertEquals("b", viewModel.uiState.value.defaultProviderId)

        viewModel.onAction(AiProvidersAction.SetEnabled("b", false))
        assertEquals("a", viewModel.uiState.value.defaultProviderId)

        viewModel.onAction(AiProvidersAction.SetRoute(AiTask.Explain, "b", "fast"))
        assertEquals(AiTaskRoute(AiTask.Explain, "b", "fast"), viewModel.uiState.value.routes[AiTask.Explain])
        // b is off, so Explain falls back to the default.
        assertEquals("a", viewModel.uiState.value.effective[AiTask.Explain]?.provider?.id)

        viewModel.onAction(AiProvidersAction.SetRoute(AiTask.Explain, null))
        assertNull(viewModel.uiState.value.routes[AiTask.Explain])
    }

    @Test
    fun orphanedKeysArePrunedOnOpen() = runTest {
        repository.keys["gone"] = "sk-x"
        AiProvidersViewModel(repository)
        assertTrue(repository.keys.isEmpty())
    }
}
