package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.parse.JsonRepair
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonRepairTest {
    private fun obj(text: String, closeOpen: Boolean = false) = assertIs<JsonObject>(JsonRepair.parse(text, closeOpen), text)

    @Test
    fun strictJsonIsUntouched() {
        val text = """{"a": "x\ny", "b": [1, 2.5, -3e5, true, null]}"""
        assertEquals(Json.parseToJsonElement(text), JsonRepair.parse(text))
    }

    @Test
    fun commonMistakes() {
        assertEquals("b", obj("{'a': 'b',}")["a"]!!.jsonPrimitive.content)
        assertEquals("1", obj("{a: 1, b: [1,,2,],}")["a"]!!.jsonPrimitive.content)
        assertEquals("true", obj("{\"a\": True, \"b\": None}")["a"]!!.jsonPrimitive.content)
        assertEquals("y", obj("{\"a\": \"x\" \"b\": \"y\"}")["b"]!!.jsonPrimitive.content)
        assertEquals("it's", obj("{'a': 'it\\'s'}")["a"]!!.jsonPrimitive.content)
        assertEquals("say \"hi\"", obj("{'a': 'say \"hi\"'}")["a"]!!.jsonPrimitive.content)
        assertEquals("x", obj("{\"a\": \"x\" // a comment\n /* and another */}")["a"]!!.jsonPrimitive.content)
    }

    @Test
    fun escapes() {
        assertEquals("\\(x\\) \\frac{1}{2}", obj("""{"a": "\(x\) \frac{1}{2}"}""")["a"]!!.jsonPrimitive.content)
        // Real control escapes stay escapes; `\uXXXX` too.
        assertEquals("a\nb\tc é", obj("""{"a": "a\nb\tc é"}""")["a"]!!.jsonPrimitive.content)
        assertEquals("\\beta", obj("""{"a": "\beta"}""")["a"]!!.jsonPrimitive.content)
        assertEquals("\\user", obj("""{"a": "\user"}""")["a"]!!.jsonPrimitive.content)
    }

    @Test
    fun truncatedTextClosesOnlyWhenAsked() {
        assertNull(JsonRepair.parse("""{"a": ["x", "y"""))
        val repaired = obj("""{"a": ["x", "y""", closeOpen = true)
        assertEquals(2, assertIs<JsonArray>(repaired["a"]).size)
        assertEquals(setOf("a", "b"), obj("""{"a": 1, "b":""", closeOpen = true).keys)
    }
}
