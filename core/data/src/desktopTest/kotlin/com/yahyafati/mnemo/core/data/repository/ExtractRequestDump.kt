package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.prompt.SourceSummaryPrompt
import com.yahyafati.mnemo.core.ingest.SummaryInput
import com.yahyafati.mnemo.core.model.ContextSummary
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.Overlap
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
 * - `MNEMO_EXTRACT_SUMMARY`: `auto` (default: a text of three or more parts), `off` or `always` (C7: the QA texts are
 *   short, so `always` shows what the summary does for a text of one or two parts)
 * - `MNEMO_EXTRACT_OVERLAP`: WithinSections (default), Always or Off
 *
 * Each part is one request; the `avoid` list (cards of earlier parts) is left out, since it depends on the replies.
 * The script drops repeated fronts itself. A text that gets a summary also gets `<name>.summary.json`, the request
 * that writes it, and each of its parts carries [SUMMARY_PLACEHOLDER] where the summary goes: the script sends the
 * summary request first and puts the reply (cleaned as the app does) in its place.
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
        val files = sources.listFiles { file -> file.extension in setOf("md", "txt") && !file.name.equals("README.md", ignoreCase = true) }.orEmpty().sortedBy { it.name }
        check(files.isNotEmpty()) { "No .md or .txt texts in ${sources.absolutePath}" }
        val options = options()
        val summaryMode = System.getenv("MNEMO_EXTRACT_SUMMARY")?.takeIf { it.isNotBlank() }?.lowercase() ?: "auto"
        require(summaryMode in setOf("auto", "off", "always")) { "MNEMO_EXTRACT_SUMMARY must be auto, off or always" }
        val dir = File(out!!).apply { mkdirs() }

        val manifest = buildJsonArray {
            for (file in files) {
                val text = file.readText()
                val title = text.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: file.nameWithoutExtension
                // A file read into the box has its title as the root of every part's path.
                val parts = splitSource(text, listOf(title), options.overlap)
                val summarise = when (summaryMode) {
                    "always" -> true
                    "off" -> false
                    else -> options.makesSummary(parts.size)
                }
                var summaryName: String? = null
                if (summarise) {
                    val input = SummaryInput.of(text)
                    val request = SourceSummaryPrompt(input.text, title, input.skimmed, options.language)
                    summaryName = "${file.nameWithoutExtension}.summary.json"
                    File(dir, summaryName).writeText(
                        json.encodeToString(ChatRequest.serializer(), ChatRequest(model = "MODEL", messages = request.messages())),
                    )
                }
                for ((index, part) in parts.withIndex()) {
                    val prompt = cardsPrompt(
                        GenerationRequest(
                            source = part,
                            options = options,
                            part = index,
                            parts = parts.size,
                            title = title,
                            context = if (summarise) SUMMARY_PLACEHOLDER else "",
                        ),
                    )
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
                            put("overlap_words", SourceText.countWords(part.overlap))
                            put("request", name)
                            summaryName?.let { put("summary_request", it) }
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
        val overlap = System.getenv("MNEMO_EXTRACT_OVERLAP")?.takeIf { it.isNotBlank() }?.let { Overlap.valueOf(it) } ?: Overlap.WithinSections
        // `contextSummary` stays Auto: this class decides itself, so `always` and `off` don't depend on the options.
        return ExtractOptions(
            density = density,
            archetypes = archetypes,
            skipIllustrations = skipIllustrations,
            overlap = overlap,
            contextSummary = ContextSummary.Auto,
        )
    }

    private companion object {
        /** Where the script puts the summary; no tag in it, so [PromptFence] leaves it alone. */
        const val SUMMARY_PLACEHOLDER = "{{MNEMO_SUMMARY}}"
    }
}
