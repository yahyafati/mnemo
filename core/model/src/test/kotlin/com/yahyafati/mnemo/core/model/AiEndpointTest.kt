package com.yahyafati.mnemo.core.model

import com.yahyafati.mnemo.core.model.AiEndpoint.Check
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiEndpointTest {
    @Test
    fun normalize() {
        assertEquals("https://api.openai.com/v1", AiEndpoint.normalize("  https://api.openai.com/v1/ "))
        assertEquals("https://api.openai.com/v1", AiEndpoint.normalize("https://api.openai.com/v1/chat/completions"))
        assertEquals("http://192.168.1.20:11434/v1", AiEndpoint.normalize("http://192.168.1.20:11434/v1/models/"))
    }

    @Test
    fun httpsIsAlwaysFine() {
        assertEquals(Check.Ok, AiEndpoint.check("https://openrouter.ai/api/v1", isLocal = false))
        assertEquals(Check.Ok, AiEndpoint.check("https://192.168.1.20/v1", isLocal = true))
    }

    @Test
    fun plainHttpOnlyForLocalProvidersOnLocalHosts() {
        assertEquals(Check.Ok, AiEndpoint.check("http://192.168.1.20:11434/v1", isLocal = true))
        assertEquals(Check.Ok, AiEndpoint.check("http://localhost:1234/v1", isLocal = true))
        assertEquals(Check.Insecure(hostIsLocal = true), AiEndpoint.check("http://192.168.1.20:11434/v1", isLocal = false))
        assertEquals(Check.Insecure(hostIsLocal = false), AiEndpoint.check("http://api.openai.com/v1", isLocal = true))
    }

    @Test
    fun invalid() {
        listOf("", "api.openai.com/v1", "ftp://host/v1", "https://", "not a url").forEach {
            assertEquals(Check.Invalid, AiEndpoint.check(it, isLocal = false), it)
        }
    }

    @Test
    fun localHosts() {
        listOf(
            "localhost", "127.0.0.1", "10.0.2.2", "172.16.0.5", "172.31.255.1", "192.168.0.10", "169.254.1.1",
            "100.101.102.103", "[::1]", "fd12:3456::1", "fe80::1", "my-pc", "my-pc.local", "nas.lan", "box.home.arpa",
        ).forEach { assertTrue(AiEndpoint.isLocalHost(it), it) }
        listOf("8.8.8.8", "172.32.0.1", "192.169.0.1", "100.128.0.1", "api.openai.com", "local.example.com", "2001:db8::1", "1.2.3.4.5")
            .forEach { assertFalse(AiEndpoint.isLocalHost(it), it) }
    }
}
