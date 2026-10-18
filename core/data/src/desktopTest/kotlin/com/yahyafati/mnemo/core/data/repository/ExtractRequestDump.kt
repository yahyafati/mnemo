package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a test: writes the Smart Extract requests the app would send for the QA texts (ADR 0015, C0), split and
 * prompted exactly as [DefaultCardGenerationRepository] does, for `scripts/qa/extract-checks.py` to send to a
 * provider. Skipped unless `MNEMO_EXTRACT_DUMP` names the folder to write to. The variables are not Gradle inputs,
 * so run it with `--rerun`.
 *
 * - `MNEMO_EXTRACT_SOURCES`: the folder of `.md`/`.txt` texts (default `scripts/qa/extract`)
 * - `MNEMO_EXTRACT_DENSITY`: Concise, Balanced (default) or Comprehensive
 * - `MNEMO_EXTRACT_ARCHETYPES`: comma-separated [CardArchetype] names (default Definition,Cloze)
 * - `MNEMO_EXTRACT_SKIP_ILLUSTRATIONS`: `0` turns *Skip examples and anecdotes* off
 *
 * Each part is one request; the `avoid` list (cards of earlier parts) is left out, since it depends on the replies.
 * The script drops repeated fronts itself.
 */
class ExtractRequestDump {
    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun dump() {
        val out = System.getenv("MNEMO_EXTRACT_DUMP")?.takeIf { it.isNotBlank() }
        assumeTrue("MNEMO_EXTRACT_DUMP is not set", out != null)
        val sources = File(System.getenv("MNEMO_EXTRACT_SOURCES")?.takeIf { it.isNotBlank() } ?: "../../scripts/qa/extract")
        val files = sources.listFiles { file -> file.extension in setOf("md", "txt") }.orEmpty().sortedBy { it.name }
        check(files.isNotEmpty()) { "No .md or .txt texts in ${sources.absolutePath}" }
        val options = options()
        val dir = File(out!!).apply { mkdirs() }

        val manifest = buildJsonArray {
            for (file in files) {
                val text = file.readText()
                val title = text.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: file.nameWithoutExtension
                // A file read into the box has its title as the root of every part's path.
                val parts = splitSource(text, listOf(title))
                for ((index, part) in parts.withIndex()) {
                    val prompt = cardsPrompt(GenerationRequest(source = part, options = options, part = index, parts = parts.size, title = title))
                    val request = ChatRequest(model = "MODEL", messages = prompt.messages(), responseFormat = prompt.responseFormat)
                    val name = "${file.nameWithoutExtension}.part${index + 1}.json"
                    File(dir, name).writeText(json.encodeToString(ChatRequest.serializer(), request))
                    add(
                        buildJsonObject {
                            put("source", file.name)
                            put("title", title)
                            put("part", index + 1)
                            put("parts", parts.size)
                            put("words", SourceText.countWords(part.text))
                            put("path", part.path.joinToString(" › "))
                            put("continued", part.continued)
                            put("request", name)
                        },
                    )
                }
            }
        }
        File(dir, "manifest.json").writeText(manifest.toString())
        println("Wrote ${manifest.size} requests for ${files.size} texts to ${dir.absolutePath}")
    }

    private fun options(): ExtractOptions {
        val density = System.getenv("MNEMO_EXTRACT_DENSITY")?.takeIf { it.isNotBlank() }?.let { ExtractDensity.valueOf(it) } ?: ExtractDensity.Balanced
        val archetypes = System.getenv("MNEMO_EXTRACT_ARCHETYPES")?.takeIf { it.isNotBlank() }
            ?.split(',')?.map { CardArchetype.valueOf(it.trim()) }?.toSet()
            ?: ExtractOptions().archetypes
        val skipIllustrations = System.getenv("MNEMO_EXTRACT_SKIP_ILLUSTRATIONS") != "0"
        return ExtractOptions(density = density, archetypes = archetypes, skipIllustrations = skipIllustrations)
    }
}
