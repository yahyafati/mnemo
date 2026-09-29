package com.yahyafati.mnemo.core.model

import java.net.URI

/**
 * Base-URL rules for AI providers (ARCHITECTURE §10): HTTPS is required, except for providers the
 * user marked local whose host is a local address. The editor uses these to explain problems as
 * the user types; the HTTP client checks them again before every request.
 */
object AiEndpoint {
    sealed interface Check {
        data object Ok : Check

        /** Not an absolute http(s) URL with a host. */
        data object Invalid : Check

        /** Plain HTTP to a provider that isn't marked local, or to a host that isn't local. */
        data class Insecure(val hostIsLocal: Boolean) : Check
    }

    /**
     * Trims [url], drops a trailing slash, and drops an endpoint path the user pasted by mistake
     * (`…/v1/chat/completions` → `…/v1`).
     */
    fun normalize(url: String): String {
        var result = url.trim().trimEnd('/')
        for (suffix in ENDPOINT_SUFFIXES) {
            if (result.endsWith(suffix, ignoreCase = true)) {
                result = result.dropLast(suffix.length).trimEnd('/')
                break
            }
        }
        return result
    }

    fun check(url: String, isLocal: Boolean): Check {
        val uri = runCatching { URI(url) }.getOrNull() ?: return Check.Invalid
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return Check.Invalid
        return when (uri.scheme?.lowercase()) {
            "https" -> Check.Ok
            "http" -> {
                val local = isLocalHost(host)
                if (isLocal && local) Check.Ok else Check.Insecure(hostIsLocal = local)
            }
            else -> Check.Invalid
        }
    }

    /** The host part of [url], or null if it has none. */
    fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Whether [host] names this device or the local network: loopback, private and link-local
     * addresses, carrier-grade NAT (used by Tailscale), single-label names and local-only domains.
     * Never resolves DNS: a public name that happens to point at a private address doesn't count.
     */
    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (h.isEmpty()) return false
        ipv4(h)?.let { (a, b) ->
            return a == 127 || a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        if (':' in h) {
            return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") ||
                h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")
        }
        if ('.' !in h) return true
        return LOCAL_SUFFIXES.any { h.endsWith(it) }
    }

    /** An HTTP header name: a token (RFC 9110). */
    fun isValidHeaderName(name: String): Boolean = name.isNotEmpty() && name.all { it in TOKEN_CHARS }

    /** An HTTP header value: printable ASCII, no line breaks (what OkHttp accepts). */
    fun isValidHeaderValue(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }

    /** The first two octets of a dotted IPv4 literal. */
    private fun ipv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 && part.all(Char::isDigit) } ?: return null }
        return octets[0] to octets[1]
    }

    private val TOKEN_CHARS = ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + "!#$%&'*+-.^_`|~".toSet()
    private val ENDPOINT_SUFFIXES = listOf("/chat/completions", "/completions", "/models")
    private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal")
}
