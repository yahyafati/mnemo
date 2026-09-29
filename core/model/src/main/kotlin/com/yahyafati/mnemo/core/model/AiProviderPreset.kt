package com.yahyafati.mnemo.core.model

/**
 * A starting point for a new provider: it pre-fills the base URL and a few defaults. Everything
 * stays editable; a provider made from a preset is an ordinary provider.
 */
data class AiProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val requiresApiKey: Boolean,
    /** A server on the user's own machine or network, reached over plain HTTP. */
    val isLocal: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    /** Where to create an API key. */
    val keyUrl: String? = null,
)

object AiProviderPresets {
    const val CUSTOM_ID = "custom"

    val all: List<AiProviderPreset> = listOf(
        AiProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", requiresApiKey = true, keyUrl = "https://platform.openai.com/api-keys"),
        AiProviderPreset(
            "openrouter", "OpenRouter", "https://openrouter.ai/api/v1", requiresApiKey = true,
            // OpenRouter's optional attribution header; the user can remove it.
            headers = mapOf("X-Title" to "Mnemo"),
            keyUrl = "https://openrouter.ai/keys",
        ),
        AiProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", requiresApiKey = true, keyUrl = "https://console.groq.com/keys"),
        AiProviderPreset("together", "Together AI", "https://api.together.xyz/v1", requiresApiKey = true, keyUrl = "https://api.together.ai/settings/api-keys"),
        AiProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", requiresApiKey = true, keyUrl = "https://platform.deepseek.com/api_keys"),
        AiProviderPreset("mistral", "Mistral", "https://api.mistral.ai/v1", requiresApiKey = true, keyUrl = "https://console.mistral.ai/api-keys"),
        AiProviderPreset(
            "gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", requiresApiKey = true,
            keyUrl = "https://aistudio.google.com/apikey",
        ),
        // On a phone, localhost is the phone itself: the user replaces it with the computer's LAN address.
        AiProviderPreset("ollama", "Ollama", "http://localhost:11434/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset("lmstudio", "LM Studio", "http://localhost:1234/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset(CUSTOM_ID, "Custom", "", requiresApiKey = false),
    )

    fun byId(id: String?): AiProviderPreset? = all.firstOrNull { it.id == id }
}
