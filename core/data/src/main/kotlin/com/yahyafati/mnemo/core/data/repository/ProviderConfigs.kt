package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.security.SecretStore
import com.yahyafati.mnemo.core.security.StoredSecret
import java.time.Duration
import javax.inject.Inject

/**
 * Builds the [ProviderConfig] for one request, decrypting the provider's key just before
 * (ADR 0005). Callers use the config for that request and drop it.
 */
internal class ProviderConfigs @Inject constructor(private val secrets: SecretStore) {
    /** Null when the provider has a key that can't be decrypted (after a restore on another device). */
    suspend fun forProvider(provider: AiProvider): ProviderConfig? {
        val key = when (val stored = secrets.get(provider.id)) {
            is StoredSecret.Present -> stored.value
            StoredSecret.Missing -> null
            StoredSecret.Unreadable -> return null
        }
        return ProviderConfig(
            baseUrl = AiEndpoint.normalize(provider.baseUrl),
            apiKey = key,
            headers = provider.headers,
            timeout = Duration.ofSeconds(provider.timeoutSeconds.coerceIn(AiProvider.MIN_TIMEOUT_SECONDS, AiProvider.MAX_TIMEOUT_SECONDS).toLong()),
            isLocal = provider.isLocal,
        )
    }
}
