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
        // Anthropic's OpenAI SDK compatibility layer.
        AiProviderPreset("anthropic", "Anthropic", "https://api.anthropic.com/v1", requiresApiKey = true, keyUrl = "https://console.anthropic.com/settings/keys"),
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
        AiProviderPreset("xai", "xAI", "https://api.x.ai/v1", requiresApiKey = true, keyUrl = "https://console.x.ai"),
        AiProviderPreset("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1", requiresApiKey = true, keyUrl = "https://fireworks.ai/account/api-keys"),
        AiProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1", requiresApiKey = true, keyUrl = "https://cloud.cerebras.ai"),
        AiProviderPreset("sambanova", "SambaNova", "https://api.sambanova.ai/v1", requiresApiKey = true, keyUrl = "https://cloud.sambanova.ai/apis"),
        AiProviderPreset("deepinfra", "DeepInfra", "https://api.deepinfra.com/v1/openai", requiresApiKey = true, keyUrl = "https://deepinfra.com/dash/api_keys"),
        AiProviderPreset("huggingface", "Hugging Face", "https://router.huggingface.co/v1", requiresApiKey = true, keyUrl = "https://huggingface.co/settings/tokens"),
        AiProviderPreset("moonshot", "Moonshot (Kimi)", "https://api.moonshot.ai/v1", requiresApiKey = true, keyUrl = "https://platform.moonshot.ai/console/api-keys"),
        AiProviderPreset(
            "qwen", "Alibaba Qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", requiresApiKey = true,
            keyUrl = "https://modelstudio.console.alibabacloud.com",
        ),
        AiProviderPreset("zai", "Z.ai (GLM)", "https://api.z.ai/api/paas/v4", requiresApiKey = true, keyUrl = "https://z.ai/manage-apikey/apikey-list"),
        // On a phone, localhost is the phone itself: the user replaces it with the computer's LAN address.
        AiProviderPreset("ollama", "Ollama", "http://localhost:11434/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset("lmstudio", "LM Studio", "http://localhost:1234/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset("llamacpp", "llama.cpp", "http://localhost:8080/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset("vllm", "vLLM", "http://localhost:8000/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset("jan", "Jan", "http://localhost:1337/v1", requiresApiKey = false, isLocal = true),
        AiProviderPreset(CUSTOM_ID, "Custom", "", requiresApiKey = false),
    )

    fun byId(id: String?): AiProviderPreset? = all.firstOrNull { it.id == id }
}
