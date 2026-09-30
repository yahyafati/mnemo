package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The text-file source (a text or Markdown file dropped on the desktop window). */
class SourceRepositoryTest {
    private val documents = FakeDocumentAccess()
    private val pdf = object : PdfTextExtractor {
        override fun extract(input: InputStream, fileName: String?): SourceResult = error("not a PDF")
    }
    private val repository = DefaultSourceRepository(
        documents = documents,
        pdf = pdf,
        web = WebPageExtractor(okhttp3.OkHttpClient(), pdf),
        speech = object : SpeechTranscriber {
            override fun isAvailable() = false

            override fun transcribe(languageTag: String?): Flow<DictationEvent> = emptyFlow()
        },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private suspend fun read(uri: String) = repository.read(SourceInput.TextFile(uri))

    @Test
    fun aTextFileIsReadAsTextWithItsNameAsTitle() = runTest {
        documents.put("/notes/Cell biology.md", "﻿# Cells\n\nMitochondria make ATP.\n")
        val source = assertIs<SourceResult.Success>(read("/notes/Cell biology.md")).source
        assertEquals("# Cells\n\nMitochondria make ATP.", source.text)
        assertEquals("Cell biology", source.title)
        assertEquals(false, source.truncated)
    }

    @Test
    fun aVeryLongFileIsCutAndSaidToBe() = runTest {
        documents.put("/notes/long.txt", "word ".repeat(PdfTextExtractor.MAX_CHARS / 5 + 10))
        val source = assertIs<SourceResult.Success>(read("/notes/long.txt")).source
        assertEquals(PdfTextExtractor.MAX_CHARS, source.text.length)
        assertTrue(source.truncated)
    }

    @Test
    fun whatIsNotTextFailsWithAReason() = runTest {
        documents.put("/notes/empty.txt", "  \n ")
        assertEquals(SourceProblem.NoText, assertIs<SourceResult.Failure>(read("/notes/empty.txt")).problem)

        documents.put("/notes/image.txt", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 1))
        assertEquals(SourceProblem.Unsupported, assertIs<SourceResult.Failure>(read("/notes/image.txt")).problem)

        documents.put("/notes/huge.txt", ByteArray(9 * 1024 * 1024) { 'a'.code.toByte() })
        assertEquals(SourceProblem.TooLarge, assertIs<SourceResult.Failure>(read("/notes/huge.txt")).problem)

        assertEquals(SourceProblem.FileUnavailable, assertIs<SourceResult.Failure>(read("/notes/gone.txt")).problem)
        assertNull(documents.info("/notes/gone.txt").size)
    }
}
