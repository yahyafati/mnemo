package com.yahyafati.mnemo.core.ui.ai

import com.yahyafati.mnemo.core.model.ProjectLinks
import java.net.URLEncoder

/** Which AI feature produced the [AiReport.content] being reported. */
enum class AiReportKind(val label: String) {
    SmartExtractCard("Smart Extract card"),
    Explain("Explain"),
    Example("Example"),
    Rewrite("Rewrite"),
    CoAuthorReply("Co-Author reply"),
    CoAuthorSuggestion("Co-Author suggested card"),
    CoAuthorRewrite("Co-Author rewritten card"),
}

/**
 * A piece of AI output the user wants to report (Play's AI-generated content policy asks for an
 * in-app way to flag offensive or harmful output). Only this text, the feature and the model name
 * are ever put in the draft: never the source text or the rest of the deck.
 */
data class AiReport(val kind: AiReportKind, val content: String, val modelId: String? = null) {
    companion object {
        /** A card's fields (question, answer, …) as one text, without the empty ones. */
        fun ofFields(kind: AiReportKind, fields: List<String>, modelId: String?) =
            AiReport(kind, fields.filter { it.isNotBlank() }.joinToString("\n\n"), modelId)
    }
}

/**
 * Builds the GitHub "new issue" link for an [AiReport]. It only opens a form in the browser with
 * the text filled in; nothing is sent until the user submits it there.
 */
object AiReportIssue {
    /** Long output is cut so the link stays within what browsers and GitHub accept. */
    const val MAX_CONTENT_CHARS = 3_000

    fun title(report: AiReport): String = "AI output report: ${report.kind.label}"

    /** The text shown to the user before the link opens: the part of the output that is sent. */
    fun sentContent(report: AiReport): String {
        val text = report.content.trim()
        return if (text.length <= MAX_CONTENT_CHARS) text else text.take(MAX_CONTENT_CHARS).trimEnd() + "…"
    }

    fun body(report: AiReport, appVersion: String): String = buildString {
        appendLine("**What is wrong with this AI output?** (inaccurate, offensive, unsafe, other)")
        appendLine()
        appendLine("<!-- Describe the problem here. -->")
        appendLine()
        appendLine("---")
        appendLine("Feature: ${report.kind.label}")
        report.modelId?.takeIf { it.isNotBlank() }?.let { appendLine("Model: $it") }
        if (appVersion.isNotBlank()) appendLine("Mnemo: $appVersion")
        appendLine()
        appendLine("AI output:")
        appendLine()
        sentContent(report).lines().forEach { appendLine("> $it") }
    }

    fun url(report: AiReport, appVersion: String): String =
        "${ProjectLinks.ISSUES}/new?title=${encode(title(report))}&body=${encode(body(report, appVersion))}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
