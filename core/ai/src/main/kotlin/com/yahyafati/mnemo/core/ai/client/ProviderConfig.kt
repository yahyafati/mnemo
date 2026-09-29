package com.yahyafati.mnemo.core.ai.client

import java.time.Duration

/**
 * Everything needed to call one provider. Built by the data layer for a single request, with the
 * API key decrypted just before, and dropped afterwards. [toString] never shows the key or header
 * values, so a config that ends up in a log or crash report leaks nothing.
 */
class ProviderConfig(
    /** Up to and including the version segment, e.g. `https://api.openai.com/v1`. */
    val baseUrl: String,
    val apiKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val timeout: Duration = Duration.ofSeconds(60),
    /** User-marked local server: plain HTTP is allowed if the host is a local address. */
    val isLocal: Boolean = false,
) {
    override fun toString(): String =
        "ProviderConfig(baseUrl=$baseUrl, apiKey=${if (apiKey == null) "none" else "<redacted>"}, " +
            "headers=${headers.keys}, timeout=$timeout, isLocal=$isLocal)"
}
