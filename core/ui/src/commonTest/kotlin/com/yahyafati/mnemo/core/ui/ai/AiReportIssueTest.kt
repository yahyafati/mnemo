package com.yahyafati.mnemo.core.ui.ai

import java.net.URI
import java.net.URLDecoder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiReportIssueTest {
    private val report = AiReport(AiReportKind.SmartExtractCard, "Front: 2 + 2?\nBack: 5", modelId = "gpt-4o")

    private fun query(url: String): Map<String, String> = URI(url).rawQuery.split('&').associate {
        val (key, value) = it.split('=', limit = 2)
        key to URLDecoder.decode(value, "UTF-8")
    }

    @Test
    fun urlOpensAnIssueDraftOnTheProjectTracker() {
        val url = AiReportIssue.url(report, "1.0.0")
        assertTrue(url.startsWith("https://github.com/yahyafati/mnemo/issues/new?"))
        assertEquals("AI output report: Smart Extract card", query(url).getValue("title"))
    }

    @Test
    fun bodyHasTheOutputTheFeatureTheModelAndTheVersionOnly() {
        val body = query(AiReportIssue.url(report, "1.0.0")).getValue("body")
        assertTrue("> Front: 2 + 2?" in body)
        assertTrue("> Back: 5" in body)
        assertTrue("Feature: Smart Extract card" in body)
        assertTrue("Model: gpt-4o" in body)
        assertTrue("Mnemo: 1.0.0" in body)
    }

    @Test
    fun specialCharactersSurviveEncoding() {
        val tricky = AiReport(AiReportKind.Explain, "a&b=c #1 + 50% \\frac{1}{2} é")
        val body = query(AiReportIssue.url(tricky, "1.0.0")).getValue("body")
        assertTrue("> a&b=c #1 + 50% \\frac{1}{2} é" in body)
    }

    @Test
    fun longOutputIsCutAndTheDialogShowsTheSameCut() {
        val long = AiReport(AiReportKind.CoAuthorReply, "x".repeat(AiReportIssue.MAX_CONTENT_CHARS * 2))
        val sent = AiReportIssue.sentContent(long)
        assertEquals(AiReportIssue.MAX_CONTENT_CHARS + 1, sent.length)
        assertTrue(sent.endsWith("…"))
        assertTrue(sent in AiReportIssue.body(long, "").replace("> ", ""))
    }

    @Test
    fun missingModelAndVersionAreLeftOut() {
        val body = AiReportIssue.body(AiReport(AiReportKind.Example, "text"), appVersion = "")
        assertFalse("Model:" in body)
        assertFalse("Mnemo:" in body)
    }
}
