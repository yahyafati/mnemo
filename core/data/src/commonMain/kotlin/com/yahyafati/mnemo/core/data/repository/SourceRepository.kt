package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.ingest.EpubReader
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.UUID

/** Smart Extract's sources (`:core:ingest`): PDFs, links and dictation become plain text on the device. */
interface SourceRepository {
    /** The text of one source. A book is not one text: an [SourceInput.Epub] fails as [SourceProblem.Unsupported]; use [readBook]. */
    suspend fun read(source: SourceInput): SourceResult

    /** An EPUB read into chapters. Read-only: nothing is saved. */
    suspend fun readBook(source: SourceInput.Epub): BookResult

    /**
     * Opens a picked PDF for reading in pieces (docs/pdf/ROADMAP.md, P1): checks its size, copies it into the cache
     * (so it can be read again, and by the page renderer later, without another permission) and counts its pages.
     * Copies left over from earlier are deleted when they are a day old. Close the handle when done.
     */
    suspend fun openPdf(uri: String): PdfOpenResult

    /**
     * The text of [pages] of an opened PDF ([PageRanges.Empty] is no pages, which fails as [SourceProblem.NoText]; null is the
     * first [PdfTextExtractor.MAX_PAGES]). More pages than that are cut, and the result says so.
     */
    suspend fun readPdf(handle: PdfHandle, pages: PageRanges?): SourceResult

    /**
     * The text layer of each of [pages] of an opened PDF, on its own and empty for a page with none (docs/pdf/ROADMAP.md,
     * P5): what Auto mode and the "no text" count tell the pages that have text from the ones that are only a picture.
     */
    suspend fun pageTexts(handle: PdfHandle, pages: List<Int>): PdfPageTextsResult

    /** Deletes the copy of an opened PDF and the pages rendered from it. Safe to call twice. */
    suspend fun closePdf(handle: PdfHandle)

    /**
     * A JPEG of [page] (1-based) of an opened PDF at [quality], in `cache/pdf/<id>/` (docs/pdf/ROADMAP.md, P3). Rendered
     * once and reused until the PDF is closed. A page with nothing on it fails as [SourceProblem.BlankPage]: a scan the
     * renderer couldn't decode looks the same.
     */
    suspend fun renderPdfPage(handle: PdfHandle, page: Int, quality: PdfQuality): PdfPageResult

    /** A small image of [page] for a page grid ([PdfQuality.THUMBNAIL_EDGE] px on the long side); cached like [renderPdfPage]. */
    suspend fun pdfThumbnail(handle: PdfHandle, page: Int): PdfPageResult

    /**
     * The part of [page] that [region] names, drawn from the page itself ([PdfQuality.FIGURE_EDGE] px on its long side) as a PNG
     * or a JPEG file in `cache/pdf/<id>/`, for a figure on a card (docs/pdf/ROADMAP.md, P7). The same page and region are drawn
     * once. A region with nothing on it fails as [SourceProblem.BlankPage].
     */
    suspend fun cropPdfPage(handle: PdfHandle, page: Int, region: PageRegion): PdfPageResult

    fun isDictationAvailable(): Boolean

    /** On-device dictation until the collector cancels. Needs the microphone permission. */
    fun dictate(languageTag: String? = null): Flow<DictationEvent>
}

internal class DefaultSourceRepository(
    private val documents: DocumentAccess,
    private val pdf: PdfTextExtractor,
    private val pageRenderer: PdfPageRenderer,
    private val web: WebPageExtractor,
    private val epub: EpubReader,
    private val speech: SpeechTranscriber,
    private val directories: AppDirectories,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : SourceRepository {
    override suspend fun read(source: SourceInput): SourceResult = withContext(ioDispatcher) {
        when (source) {
            is SourceInput.Link -> web.extract(source.url)
            is SourceInput.Pdf -> readPdfFile(source.uri)
            is SourceInput.TextFile -> readTextFile(source.uri)
            is SourceInput.Epub -> SourceResult.Failure(SourceProblem.Unsupported)
        }
    }

    override suspend fun readBook(source: SourceInput.Epub): BookResult = withContext(ioDispatcher) {
        val info = try {
            documents.info(source.uri)
        } catch (e: IOException) {
            return@withContext BookResult.Failure(SourceProblem.FileUnavailable)
        }
        if ((info.size ?: 0) > epub.maxFileBytes) return@withContext BookResult.Failure(SourceProblem.TooLarge)
        val input = try {
            documents.openInput(source.uri)
        } catch (e: IOException) {
            return@withContext BookResult.Failure(SourceProblem.FileUnavailable)
        }
        input.use { epub.read(it, info.name) }
    }

    override suspend fun openPdf(uri: String): PdfOpenResult = withContext(ioDispatcher) {
        val info = try {
            documents.info(uri)
        } catch (e: IOException) {
            return@withContext PdfOpenResult.Failure(SourceProblem.FileUnavailable)
        }
        if ((info.size ?: 0) > PdfTextExtractor.MAX_FILE_BYTES) return@withContext PdfOpenResult.Failure(SourceProblem.TooLarge)
        val folder = pdfFolder()
        removeStale(folder)
        val id = UUID.randomUUID().toString()
        val copy = File(folder, "$id.pdf")
        try {
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Can't create $folder")
            val copied = documents.openInput(uri).use { input -> copy.outputStream().use { input.copyUpTo(it, PdfTextExtractor.MAX_FILE_BYTES + 1) } }
            // A file that says nothing about its size (or lies) is told from one that just fits by the byte after the limit.
            if (copied > PdfTextExtractor.MAX_FILE_BYTES) {
                copy.delete()
                return@withContext PdfOpenResult.Failure(SourceProblem.TooLarge)
            }
            val name = info.name?.removeSuffix(".pdf")?.removeSuffix(".PDF")
            when (val inspected = copy.inputStream().use { pdf.inspect(it, name) }) {
                is PdfInfoResult.Success -> PdfOpenResult.Success(PdfHandle(id, inspected.info))
                is PdfInfoResult.Failure -> {
                    copy.delete()
                    PdfOpenResult.Failure(inspected.problem, inspected.detail)
                }
            }
        } catch (e: IOException) {
            copy.delete()
            PdfOpenResult.Failure(SourceProblem.FileUnavailable)
        }
    }

    override suspend fun readPdf(handle: PdfHandle, pages: PageRanges?): SourceResult = withContext(ioDispatcher) {
        val copy = pdfCopy(handle)
        if (!copy.isFile) return@withContext SourceResult.Failure(SourceProblem.FileUnavailable)
        try {
            copy.inputStream().use { pdf.extract(it, pages?.pages, handle.info.title) }
        } catch (e: IOException) {
            SourceResult.Failure(SourceProblem.FileUnavailable)
        }
    }

    override suspend fun pageTexts(handle: PdfHandle, pages: List<Int>): PdfPageTextsResult = withContext(ioDispatcher) {
        val copy = pdfCopy(handle)
        if (!copy.isFile) return@withContext PdfPageTextsResult.Failure(SourceProblem.FileUnavailable)
        try {
            copy.inputStream().use { pdf.pageTexts(it, pages) }
        } catch (e: IOException) {
            PdfPageTextsResult.Failure(SourceProblem.FileUnavailable)
        }
    }

    override suspend fun closePdf(handle: PdfHandle) = withContext(ioDispatcher) {
        pdfCopy(handle).delete()
        pdfPages(handle).deleteRecursively()
        Unit
    }

    override suspend fun renderPdfPage(handle: PdfHandle, page: Int, quality: PdfQuality): PdfPageResult =
        renderCached(handle, page, quality.longEdge, "p$page-${quality.name.lowercase()}.jpg")

    override suspend fun pdfThumbnail(handle: PdfHandle, page: Int): PdfPageResult =
        renderCached(handle, page, PdfQuality.THUMBNAIL_EDGE, "p$page-thumb.jpg")

    override suspend fun cropPdfPage(handle: PdfHandle, page: Int, region: PageRegion): PdfPageResult {
        // The encoding (PNG or JPEG) is the renderer's choice, so a figure drawn before is found under either name.
        val base = "fig-p$page-${region.key}"
        return renderCached(handle, page, listOf("$base.png", "$base.jpg")) { copy, folder ->
            val rendered = pageRenderer.renderRegion(copy, page, region, PdfQuality.FIGURE_EDGE)
            rendered to File(folder, "$base.${MediaRef.extensionFor(rendered.mimeType) ?: "jpg"}")
        }
    }

    private suspend fun renderCached(handle: PdfHandle, page: Int, longEdge: Int, name: String): PdfPageResult =
        renderCached(handle, page, listOf(name)) { copy, folder -> pageRenderer.render(copy, page, longEdge) to File(folder, name) }

    /** [names] are the files a drawing of this may already be in; [draw] makes it and says which file it goes to. */
    private suspend fun renderCached(
        handle: PdfHandle,
        page: Int,
        names: List<String>,
        draw: (copy: File, folder: File) -> Pair<RenderedPage, File>,
    ): PdfPageResult = withContext(ioDispatcher) {
        if (page !in 1..handle.info.pageCount) return@withContext PdfPageResult.Failure(SourceProblem.Unsupported)
        val copy = pdfCopy(handle)
        if (!copy.isFile) return@withContext PdfPageResult.Failure(SourceProblem.FileUnavailable)
        val folder = pdfPages(handle)
        names.map { File(folder, it) }.firstOrNull { it.isFile && it.length() > 0 }?.let { return@withContext PdfPageResult.Success(it) }
        try {
            val (rendered, target) = draw(copy, folder)
            // A white page is not saved: it is the answer for a scan the renderer can't decode, and the next try may differ.
            if (rendered.blank) return@withContext PdfPageResult.Failure(SourceProblem.BlankPage)
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Can't create $folder")
            // Written beside the target and renamed, so a reader never sees half a file and two renders of a page can't mix.
            val partial = File(folder, "${target.name}.${UUID.randomUUID()}.part")
            try {
                partial.writeBytes(rendered.bytes)
                if (!partial.renameTo(target)) throw IOException("Can't write $target")
            } finally {
                partial.delete()
            }
            PdfPageResult.Success(target)
        } catch (e: PdfRenderException) {
            PdfPageResult.Failure(e.problem, e.message)
        } catch (e: IOException) {
            PdfPageResult.Failure(SourceProblem.FileUnavailable)
        }
    }

    private fun pdfFolder() = File(directories.cache, PDF_FOLDER)

    /** The id is made by [openPdf], but a handle is a plain value: never let one name a file outside the folder. */
    private fun safeId(handle: PdfHandle) = handle.id.filter { it.isLetterOrDigit() || it == '-' }

    private fun pdfCopy(handle: PdfHandle) = File(pdfFolder(), "${safeId(handle)}.pdf")

    /** The pages rendered from a PDF (docs/pdf/ROADMAP.md, P3) sit in a folder named by its id. */
    private fun pdfPages(handle: PdfHandle) = File(pdfFolder(), safeId(handle))

    /** A copy is deleted when its PDF is closed; one a closed window or a killed process left behind goes after a day. */
    private fun removeStale(folder: File) {
        val cutoff = clock.now().minus(KEEP_PDF).toEpochMilli()
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun readPdfFile(uri: String): SourceResult {
        val info = documents.info(uri)
        if ((info.size ?: 0) > PdfTextExtractor.MAX_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val input = try {
            documents.openInput(uri)
        } catch (e: IOException) {
            return SourceResult.Failure(SourceProblem.FileUnavailable)
        }
        return input.use { pdf.extract(it, info.name?.removeSuffix(".pdf")?.removeSuffix(".PDF")) }
    }

    private fun readTextFile(uri: String): SourceResult {
        val info = documents.info(uri)
        if ((info.size ?: 0) > MAX_TEXT_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val bytes = try {
            // One byte more than the limit tells a file that is too large from one that just fits, whatever it says its size is.
            documents.openInput(uri).use { it.readUpTo(MAX_TEXT_FILE_BYTES.toInt() + 1) }
        } catch (e: IOException) {
            return SourceResult.Failure(SourceProblem.FileUnavailable)
        }
        if (bytes.size > MAX_TEXT_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val text = bytes.decodeToString().removePrefix("\uFEFF").trim()
        // A NUL is never in text: this is a binary file with a text file's name.
        if ('\u0000' in text) return SourceResult.Failure(SourceProblem.Unsupported)
        if (text.isEmpty()) return SourceResult.Failure(SourceProblem.NoText)
        return SourceResult.Success(
            SourceText(
                text = text.take(PdfTextExtractor.MAX_CHARS),
                title = info.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() },
                truncated = text.length > PdfTextExtractor.MAX_CHARS,
            ),
        )
    }

    override fun isDictationAvailable(): Boolean = speech.isAvailable()

    override fun dictate(languageTag: String?): Flow<DictationEvent> = speech.transcribe(languageTag)

    private companion object {
        /** A text file larger than this isn't a document Smart Extract can use (400,000 characters are read at most). */
        const val MAX_TEXT_FILE_BYTES = 8L * 1024 * 1024

        /** The folder of the cache that holds opened PDFs: `<id>.pdf`, and a folder `<id>` for what is made from it. */
        const val PDF_FOLDER = "pdf"
        val KEEP_PDF: Duration = Duration.ofDays(1)
    }
}

/** At most [limit] bytes of this stream; the rest stays unread. */
private fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}

/** Copies at most [limit] bytes of this stream to [out]; returns how many. */
private fun InputStream.copyUpTo(out: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return total
}
