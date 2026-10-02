package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** What [EpubReader] reads at most. Beyond the file and entry caps it keeps what it has and says so ([BookSource.truncated]). */
data class EpubLimits(
    val maxFileBytes: Long = 100L * 1024 * 1024,
    val maxEntries: Int = 5_000,
    /** Uncompressed bytes read from one XML or XHTML entry: the zip-bomb guard (an entry's declared size is not trusted). */
    val maxEntryBytes: Int = 8 * 1024 * 1024,
    val maxChapters: Int = 400,
    val maxTotalChars: Int = 4_000_000,
    val maxChapterChars: Int = 200_000,
)

/**
 * An EPUB read into chapters (docs/epub/ROADMAP.md B1, ADR 0011). The chapters are the table of
 * contents' (EPUB 3 `nav`, EPUB 2 `toc.ncx`), else the reading order's files. Text only: images,
 * MathML, page numbers, footnotes and ruby readings are left out. A book with DRM fails with
 * [SourceProblem.Drm]; nothing here tries to read protected content.
 *
 * The book is copied to [cacheDir] (a zip needs random access, and the file API only gives a stream),
 * read with [ZipFile] and deleted again. No entry is ever written to disk, so a path like `../x` in the
 * zip is only a name. Blocking; call it off the main thread.
 */
class EpubReader(private val cacheDir: () -> File, private val limits: EpubLimits = EpubLimits()) {
    fun read(input: InputStream, fileName: String? = null): BookResult {
        val file = try {
            spool(input)
        } catch (e: BookProblem) {
            return e.failure()
        } catch (e: IOException) {
            return BookResult.Failure(SourceProblem.FileUnavailable)
        }
        return try {
            ZipFile(file).use { zip -> BookParser(zip, limits, fileName).parse() }
        } catch (e: BookProblem) {
            e.failure()
        } catch (e: ZipException) {
            BookResult.Failure(SourceProblem.Unsupported)
        } catch (e: IOException) {
            BookResult.Failure(SourceProblem.FileUnavailable)
        } catch (e: IllegalArgumentException) {
            // A zip entry name that isn't valid UTF-8.
            BookResult.Failure(SourceProblem.Unsupported)
        } finally {
            file.delete()
        }
    }

    private fun spool(input: InputStream): File {
        val dir = cacheDir().also { it.mkdirs() }
        val file = File.createTempFile("book", ".epub", dir)
        try {
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    // Fail while copying, not after the whole file is on disk.
                    if (total > limits.maxFileBytes) throw BookProblem(SourceProblem.TooLarge)
                    out.write(buffer, 0, read)
                }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return file
    }
}

/** A reason to stop reading; never thrown out of [EpubReader]. */
private class BookProblem(val problem: SourceProblem) : Exception(problem.name) {
    fun failure() = BookResult.Failure(problem)
}

/** A chapter before the clean-up passes. */
private class RawChapter(var title: String, var text: String, var kind: ChapterKind)

/** A place in the reading order: the [doc]'th spine document, [offset] characters in. */
private data class Pos(val doc: Int, val offset: Int) : Comparable<Pos> {
    override fun compareTo(other: Pos) = compareValuesBy(this, other, { it.doc }, { it.offset })
}

private class TocEntry(val title: String, val path: String?, val fragment: String?, val children: List<TocEntry>)

private class ManifestItem(val id: String, val path: String, val mediaType: String, val properties: Set<String>)

/** One spine document's text (cleaned later, per chapter) and where its `id`s are in it. */
private class Doc(
    val text: String,
    val anchors: Map<String, Int>,
    val heading: String?,
    val titleTag: String?,
    /** The `epub:type`s of the body and its direct children. */
    val types: Set<String>,
)

private class BookParser(private val zip: ZipFile, private val limits: EpubLimits, private val fileName: String?) {
    private val entries = HashMap<String, ZipEntry>()
    private val lowerEntries = HashMap<String, ZipEntry>()
    private var truncated = false
    private var totalChars = 0
    private val docs = HashMap<Int, Doc>()
    private var spine: List<String> = emptyList()
    private val spineIndex = HashMap<String, Int>()
    private val spineIndexLower = HashMap<String, Int>()

    init {
        if (zip.size() > limits.maxEntries) throw BookProblem(SourceProblem.TooLarge)
        for (entry in zip.entries().asSequence()) {
            if (entry.isDirectory) continue
            entries.putIfAbsent(entry.name, entry)
            lowerEntries.putIfAbsent(entry.name.lowercase(), entry)
        }
    }

    fun parse(): BookResult {
        val opfPath = findOpf() ?: throw BookProblem(SourceProblem.Unsupported)
        val opfDir = dirOf(opfPath)
        val opf = xml(opfPath)
        val manifest = manifestOf(opf, opfDir)
        val byId = manifest.associateBy { it.id }
        val navItem = manifest.firstOrNull { "nav" in it.properties }

        spine = opf.elements("itemref")
            .filter { it.attr("linear").lowercase() != "no" }
            .mapNotNull { byId[it.attr("idref")] }
            .filter { isDocument(it) && it !== navItem }
            .map { it.path }
            .distinct()
        spine.forEachIndexed { index, path ->
            spineIndex.putIfAbsent(path, index)
            spineIndexLower.putIfAbsent(path.lowercase(), index)
        }

        checkDrm(manifest)

        val landmarks = HashMap<String, MutableSet<String>>()
        val toc = tocOf(opf, opfDir, byId, navItem, landmarks)
        var starts = chapterEntries(toc)
        if (starts.isEmpty()) {
            starts = spine.indices.map { Located(null, Pos(it, 0), null, null) }
        }

        val raw = assemble(starts, landmarks)
        if (raw.isEmpty()) throw BookProblem(SourceProblem.NoText)

        val chapters = mutableListOf<BookChapter>()
        for ((index, chapter) in merge(raw).withIndex()) {
            if (index >= limits.maxChapters) {
                truncated = true
                break
            }
            val tooLong = chapter.text.length > limits.maxChapterChars
            if (tooLong) truncated = true
            chapters += BookChapter(
                id = chapters.size,
                title = chapter.title,
                text = if (tooLong) chapter.text.take(limits.maxChapterChars).trimEnd() else chapter.text,
                kind = chapter.kind,
                truncated = tooLong,
            )
        }
        return BookResult.Success(
            BookSource(
                // Blank titles stay blank: the screens say "Chapter 3" in the user's language.
                title = titleOf(opf) ?: fileName?.substringBeforeLast('.')?.trim().orEmpty(),
                author = authorsOf(opf),
                language = opf.elements("language").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
                chapters = chapters,
                truncated = truncated,
            ),
        )
    }

    // ---- zip access ----

    private fun find(path: String): ZipEntry? = entries[path] ?: lowerEntries[path.lowercase()]

    /** At most [max] bytes of the entry; more than that is cut off (and [truncated] is set when [markTruncated]). */
    private fun bytes(entry: ZipEntry, max: Int, markTruncated: Boolean): ByteArray {
        zip.getInputStream(entry).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var remaining = max
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                remaining -= read
            }
            if (input.read() >= 0 && markTruncated) truncated = true
            return out.toByteArray()
        }
    }

    private fun xml(path: String): Document {
        val entry = find(path) ?: throw BookProblem(SourceProblem.Unsupported)
        val bytes = bytes(entry, limits.maxEntryBytes, markTruncated = false)
        return Jsoup.parse(ByteArrayInputStream(bytes), null, "", Parser.xmlParser())
    }

    private fun findOpf(): String? {
        val container = find("META-INF/container.xml")
        if (container != null) {
            val rootfile = xml("META-INF/container.xml").elements("rootfile").firstOrNull { it.hasAttr("full-path") }
            val path = rootfile?.attr("full-path")?.let { resolvePath("", it) }
            if (path != null && find(path) != null) return find(path)!!.name
        }
        // Some books leave the container out or point at nothing: take the only package file.
        return entries.keys.singleOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    // ---- package ----

    private fun manifestOf(opf: Document, opfDir: String): List<ManifestItem> = opf.elements("item").mapNotNull { item ->
        val path = resolvePath(opfDir, item.attr("href")) ?: return@mapNotNull null
        ManifestItem(
            id = item.attr("id"),
            path = find(path)?.name ?: path,
            mediaType = item.attr("media-type").lowercase(),
            properties = item.attr("properties").lowercase().split(WHITESPACE).filter { it.isNotEmpty() }.toSet(),
        )
    }

    private fun isDocument(item: ManifestItem) =
        item.mediaType == "application/xhtml+xml" || item.mediaType == "text/html" ||
            (item.mediaType.isEmpty() && item.path.substringAfterLast('.').lowercase() in DOCUMENT_EXTENSIONS)

    private fun titleOf(opf: Document): String? {
        val titles = opf.elements("title").filter { it.parent()?.localName() == "metadata" }
        val mainIds = opf.elements("meta")
            .filter { it.attr("property") == "title-type" && it.text().trim() == "main" }
            .map { it.attr("refines").removePrefix("#") }
        return (titles.firstOrNull { it.id().isNotEmpty() && it.id() in mainIds } ?: titles.firstOrNull { it.text().isNotBlank() })
            ?.text()?.collapse()?.takeIf { it.isNotEmpty() }
    }

    private fun authorsOf(opf: Document): String? = opf.elements("creator")
        .map { it.text().collapse() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_AUTHORS)
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }

    // ---- DRM ----

    /**
     * Fails for a book whose text is protected. `encryption.xml` alone is not DRM: it also lists
     * obfuscated fonts, which is common in plain books.
     */
    private fun checkDrm(manifest: List<ManifestItem>) {
        if (PROTECTION_FILES.any { find(it) != null }) throw BookProblem(SourceProblem.Drm)
        if (find("META-INF/encryption.xml") == null) return
        val documentPaths = (manifest.filter { isDocument(it) }.map { it.path } + spine).map { it.lowercase() }.toSet()
        for (data in xml("META-INF/encryption.xml").elements("EncryptedData")) {
            val algorithm = data.elements("EncryptionMethod").firstOrNull()?.attr("Algorithm").orEmpty()
            if (algorithm in FONT_OBFUSCATION) continue
            val path = data.elements("CipherReference").firstOrNull()?.attr("URI")?.let { resolvePath("", it) } ?: continue
            if (path.lowercase() in documentPaths || path.substringAfterLast('.').lowercase() in DOCUMENT_EXTENSIONS) {
                throw BookProblem(SourceProblem.Drm)
            }
        }
    }

    // ---- table of contents ----

    /** Where a table-of-contents entry starts, after it is found in the reading order. */
    private class Located(
        val title: String?,
        val pos: Pos,
        val path: String?,
        val fragment: String?,
        /** A part's title page: it ends the chapter before it, and is not a chapter. */
        val boundary: Boolean = false,
    )

    private fun tocOf(
        opf: Document,
        opfDir: String,
        byId: Map<String, ManifestItem>,
        navItem: ManifestItem?,
        landmarks: MutableMap<String, MutableSet<String>>,
    ): List<TocEntry> {
        // EPUB 2's `guide` names the cover, title page and so on.
        for (reference in opf.elements("reference")) {
            val path = resolvePath(opfDir, reference.attr("href")) ?: continue
            landmarks.getOrPut(path.lowercase()) { mutableSetOf() } += reference.attr("type").lowercase()
        }
        if (navItem != null) {
            val entries = navToc(navItem.path, landmarks)
            if (entries.isNotEmpty()) return entries
        }
        val ncx = opf.elements("spine").firstOrNull()?.attr("toc")?.let { byId[it] }
            ?: byId.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        return ncx?.let { ncxToc(it.path) }.orEmpty()
    }

    private fun navToc(path: String, landmarks: MutableMap<String, MutableSet<String>>): List<TocEntry> {
        val entry = find(path) ?: return emptyList()
        val dir = dirOf(path)
        val document = parseXhtml(bytes(entry, limits.maxEntryBytes, markTruncated = false))
        for (nav in document.select("nav").filter { "landmarks" in it.epubTypes() }) {
            for (link in nav.select("a[href]")) {
                val target = resolvePath(dir, link.attr("href")) ?: continue
                landmarks.getOrPut(target.lowercase()) { mutableSetOf() } += link.epubTypes()
            }
        }
        val nav = document.select("nav").firstOrNull { "toc" in it.epubTypes() } ?: document.selectFirst("nav") ?: return emptyList()
        return nav.children().firstOrNull { it.normalName() == "ol" }?.let { navList(it, dir) }.orEmpty()
    }

    private fun navList(list: Element, dir: String): List<TocEntry> = list.children().filter { it.normalName() == "li" }.map { item ->
        val label = item.children().firstOrNull { it.normalName() == "a" || it.normalName() == "span" }
        val href = label?.takeIf { it.normalName() == "a" }?.attr("href")
        TocEntry(
            title = label?.text()?.collapse().orEmpty(),
            path = href?.let { resolvePath(dir, it) },
            fragment = href?.let { fragmentOf(it) },
            children = item.children().firstOrNull { it.normalName() == "ol" }?.let { navList(it, dir) }.orEmpty(),
        )
    }

    private fun ncxToc(path: String): List<TocEntry> {
        if (find(path) == null) return emptyList()
        val dir = dirOf(path)
        val points = xml(path).elements("navMap").firstOrNull() ?: return emptyList()
        return ncxPoints(points, dir)
    }

    private fun ncxPoints(parent: Element, dir: String): List<TocEntry> = parent.children().filter { it.localName() == "navpoint" }.map { point ->
        val src = point.children().firstOrNull { it.localName() == "content" }?.attr("src")
        TocEntry(
            title = point.children().firstOrNull { it.localName() == "navlabel" }?.text()?.collapse().orEmpty(),
            path = src?.let { resolvePath(dir, it) },
            fragment = src?.let { fragmentOf(it) },
            children = ncxPoints(point, dir),
        )
    }

    /**
     * The entries that become chapters, in reading order. A "Part" page with chapters under it is
     * not a chapter itself, its chapters are; any other entry is a chapter, and the entries under it
     * (sections) are part of it.
     */
    private fun chapterEntries(toc: List<TocEntry>): List<Located> {
        val out = mutableListOf<Located>()
        fun visit(list: List<TocEntry>) {
            for (entry in list) {
                val container = entry.children.isNotEmpty() && (entry.path == null || (PART_TITLE.containsMatchIn(entry.title) && leadIsShort(entry)))
                if (container) {
                    locate(entry, boundary = true)?.let { out += it }
                    visit(entry.children)
                } else {
                    locate(entry)?.let { out += it }
                }
            }
        }
        visit(toc)
        // The reading order wins over a table of contents that lists things out of order.
        return out.sortedBy { it.pos }
    }

    private fun locate(entry: TocEntry, boundary: Boolean = false): Located? {
        val path = entry.path ?: return null
        val index = spineIndex[path] ?: spineIndexLower[path.lowercase()] ?: return null
        val offset = entry.fragment?.takeIf { it.isNotEmpty() }?.let { doc(index).anchors[it] } ?: 0
        return Located(entry.title, Pos(index, offset), path, entry.fragment?.takeIf { it.isNotEmpty() }, boundary)
    }

    private fun firstLocation(entry: TocEntry): Located? = locate(entry) ?: entry.children.firstNotNullOfOrNull { firstLocation(it) }

    /** The page of a part (up to its first chapter) has next to no text: a title page. */
    private fun leadIsShort(entry: TocEntry): Boolean {
        val start = locate(entry)?.pos ?: return true
        val end = firstLocation(entry.children.first())?.pos ?: return true
        if (end <= start) return true
        return isShort(clean(slice(start, end)))
    }

    // ---- text ----

    private fun doc(index: Int): Doc = docs.getOrPut(index) { parseDoc(index) }

    private fun parseDoc(index: Int): Doc {
        val empty = Doc("", emptyMap(), null, null, emptySet())
        val remaining = limits.maxTotalChars - totalChars
        if (remaining <= 0) {
            truncated = true
            return empty
        }
        val entry = find(spine[index]) ?: return empty
        val document = parseXhtml(bytes(entry, limits.maxEntryBytes, markTruncated = true))
        val body = document.body()
        val types = (listOf(body) + body.children()).flatMapTo(mutableSetOf()) { it.epubTypes() }
        val heading = document.selectFirst("h1, h2, h3")?.text()?.collapse()?.takeIf { it.isNotEmpty() }
        val titleTag = document.title().collapse().takeIf { it.isNotEmpty() }
        body.select(NOISE).remove()
        body.allElements.filter { element -> element !== body && (element.epubTypes().any { it in DROPPED_TYPES } || element.attr("role").lowercase() in DROPPED_ROLES) }
            .forEach { it.remove() }
        val anchors = HashMap<String, Int>()
        val raw = ReadableText.of(body) { element, offset ->
            if (element.id().isNotEmpty()) anchors.putIfAbsent(element.id(), offset)
            if (element.normalName() == "a" && element.hasAttr("name")) anchors.putIfAbsent(element.attr("name"), offset)
        }
        val text = if (raw.length > remaining) raw.take(remaining).also { truncated = true } else raw
        totalChars += text.length
        return Doc(text, anchors, heading, titleTag, types)
    }

    /** The raw text from [start] up to (not including) [end]. */
    private fun slice(start: Pos, end: Pos): String {
        if (start.doc == end.doc) return doc(start.doc).text.range(start.offset, end.offset)
        val out = StringBuilder(doc(start.doc).text.range(start.offset, Int.MAX_VALUE))
        for (index in start.doc + 1 until minOf(end.doc, spine.size)) out.append("\n\n").append(doc(index).text)
        if (end.doc < spine.size) out.append("\n\n").append(doc(end.doc).text.range(0, end.offset))
        return out.toString()
    }

    private fun clean(raw: String) = TextCleanup.normalize(raw)

    private fun assemble(located: List<Located>, landmarks: Map<String, Set<String>>): List<RawChapter> {
        val end = Pos(spine.size, 0)
        val out = mutableListOf<RawChapter>()
        for ((index, item) in located.withIndex()) {
            if (item.boundary) continue
            val text = clean(slice(item.pos, located.getOrNull(index + 1)?.pos ?: end))
            if (text.isEmpty()) continue
            val first = doc(item.pos.doc)
            val title = (item.title?.takeIf { it.isNotBlank() } ?: first.heading ?: first.titleTag ?: "").collapse()
            // Types name a whole file, so they only describe an entry that starts at its top.
            val types = if (item.fragment == null) first.types + landmarks[(item.path ?: spine[item.pos.doc]).lowercase()].orEmpty() else emptySet()
            out += RawChapter(title, text, classify(title, types))
        }
        return out
    }

    /** Title pages of a part ("Part I") fold into the chapter after them, as its first lines. */
    private fun merge(chapters: List<RawChapter>): List<RawChapter> {
        val out = mutableListOf<RawChapter>()
        var carry: RawChapter? = null
        for ((index, original) in chapters.withIndex()) {
            var chapter = original
            carry?.let { stub ->
                if (chapter.kind == ChapterKind.Content) {
                    val lead = if (stub.text.startsWith(stub.title, ignoreCase = true)) stub.text else stub.title + "\n\n" + stub.text
                    chapter = RawChapter(chapter.title, lead + "\n\n" + chapter.text, chapter.kind)
                } else {
                    out += stub
                }
            }
            carry = null
            if (chapter.kind == ChapterKind.Content && isStub(chapter.text) && index < chapters.lastIndex) carry = chapter else out += chapter
        }
        carry?.let { out += it }
        return out
    }

    private fun isShort(text: String) = SourceText.countWords(text) < SHORT_WORDS && text.length < SHORT_CHARS

    private fun isStub(text: String) = SourceText.countWords(text) <= STUB_WORDS && text.length <= STUB_CHARS

    private fun Element.epubTypes(): Set<String> = attr("epub:type").lowercase().split(WHITESPACE).filter { it.isNotEmpty() }.toSet()

    /** Elements by local name, whatever their namespace prefix (`dc:title`, `opf:meta`). */
    private fun Element.elements(name: String): List<Element> = select("*").filter { it !== this && it.localName() == name.lowercase() }

    private fun Element.localName() = tagName().substringAfterLast(':').lowercase()

    private fun String.range(from: Int, to: Int): String {
        val start = from.coerceIn(0, length)
        return substring(start, to.coerceIn(start, length))
    }

    private fun String.collapse() = replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        const val MAX_AUTHORS = 3

        /** A page with less text than this is a title page, not a chapter (a part opener). */
        const val SHORT_WORDS = 100
        const val SHORT_CHARS = 600

        /** A chapter this small is a heading with next to nothing: it folds into the next one. */
        const val STUB_WORDS = 30
        const val STUB_CHARS = 200

        val DOCUMENT_EXTENSIONS = setOf("xhtml", "html", "htm", "xml")
        val PROTECTION_FILES = listOf("META-INF/rights.xml", "META-INF/sinf.xml")
        val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")
        val PART_TITLE = Regex("""^\s*(part|book|volume|vol\.|unit|act|division)\b""", RegexOption.IGNORE_CASE)

        const val NOISE = "script, style, svg, rt, rp, nav, [hidden], [aria-hidden=true]"
        val DROPPED_TYPES = setOf("pagebreak", "noteref", "footnote", "rearnote", "endnote")
        val DROPPED_ROLES = setOf("doc-pagebreak", "doc-noteref", "doc-footnote", "doc-endnote")
    }
}

/** `<span/>` is an empty element in XHTML, but the HTML parser keeps a non-void tag open: it would swallow the rest of the page. */
private val EMPTY_ELEMENT = Regex("""<([A-Za-z][\w:.-]*)((?:\s[^<>]*?)?)\s*/>""")
private val VOID_ELEMENTS = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
private val DECLARED_ENCODING = Regex("""^\s*<\?xml[^>]*encoding\s*=\s*["']([\w.:-]+)["']""", RegexOption.IGNORE_CASE)

/**
 * A content document (or `nav`) as HTML. Book files are XHTML, so empty elements are written out
 * first; encoding comes from the byte order mark or the XML declaration, else UTF-8.
 */
private fun parseXhtml(bytes: ByteArray): Document {
    val html = EMPTY_ELEMENT.replace(decode(bytes)) { match ->
        val name = match.groupValues[1]
        if (name.lowercase() in VOID_ELEMENTS) match.value else "<$name${match.groupValues[2]}></$name>"
    }
    return Jsoup.parse(html)
}

private fun decode(bytes: ByteArray): String {
    fun starts(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { (bytes[it].toInt() and 0xFF) == prefix[it] }
    return when {
        starts(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        starts(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        starts(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        else -> {
            val declared = DECLARED_ENCODING.find(String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1))?.groupValues?.get(1)
            val charset = try {
                declared?.let { java.nio.charset.Charset.forName(it) }
            } catch (e: IllegalArgumentException) {
                null
            }
            String(bytes, charset ?: Charsets.UTF_8)
        }
    }
}

private val SCHEME = Regex("""^(https?|mailto|ftp|data|javascript|file|urn):""", RegexOption.IGNORE_CASE)

/**
 * The zip path [href] names, from the file in [baseDir] ("" or "dir/"): without the fragment, percent-decoded,
 * `.` and `..` resolved. Null for an address, a fragment alone, or a path that climbs out of the zip.
 */
internal fun resolvePath(baseDir: String, href: String): String? {
    val noFragment = href.substringBefore('#').substringBefore('?')
    if (noFragment.isEmpty() || SCHEME.containsMatchIn(noFragment)) return null
    val decoded = percentDecode(noFragment)
    val joined = if (decoded.startsWith("/")) decoded.drop(1) else baseDir + decoded
    val stack = ArrayDeque<String>()
    for (segment in joined.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (stack.isEmpty()) return null else stack.removeLast()
            else -> stack.addLast(segment)
        }
    }
    return stack.joinToString("/").takeIf { it.isNotEmpty() }
}

/** The part of [href] after `#`, percent-decoded. */
private fun fragmentOf(href: String): String? = href.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { percentDecode(it) }

private fun percentDecode(value: String): String = try {
    // `+` is a plus in a path, not a space.
    URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
} catch (e: IllegalArgumentException) {
    value
}

private fun dirOf(path: String) = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

private val FRONT_TYPES = setOf(
    "cover", "titlepage", "title-page", "halftitlepage", "copyright-page", "dedication", "epigraph", "toc", "frontmatter",
    "imprint", "contributors", "loi", "lot",
)
private val BACK_TYPES = setOf("index", "colophon", "acknowledgments", "acknowledgements", "backmatter", "bibliography", "endnotes", "rearnotes", "other-credits")
private val CONTENT_TYPES = setOf("bodymatter", "chapter", "part", "division", "text")
private val FRONT_TITLES = setOf(
    "cover", "cover page", "title", "title page", "half title", "half-title", "copyright", "copyright page", "dedication", "contents",
    "table of contents", "epigraph", "imprint", "also by", "books by", "praise", "about this book",
)
private val FRONT_PREFIXES = listOf("copyright ", "©", "praise for", "also by", "books by", "other books by")
private val BACK_TITLES = setOf(
    "index", "notes", "endnotes", "bibliography", "colophon", "credits", "permissions", "acknowledgments", "acknowledgements",
    "acknowledgment", "acknowledgement", "about the author", "about the authors", "about the publisher", "further reading",
)
private val BACK_PREFIXES = listOf("acknowledg", "about the author", "index of")

/** Front or back matter by the book's own `epub:type`s, else by the title; the picker leaves those unchecked. */
internal fun classify(title: String, types: Set<String>): ChapterKind {
    if (types.any { it in CONTENT_TYPES }) return ChapterKind.Content
    if (types.any { it in FRONT_TYPES }) return ChapterKind.FrontMatter
    if (types.any { it in BACK_TYPES }) return ChapterKind.BackMatter
    val key = title.lowercase().trim().trimEnd('.', ':', '!').trim()
    return when {
        key in FRONT_TITLES || FRONT_PREFIXES.any { key.startsWith(it) } -> ChapterKind.FrontMatter
        key in BACK_TITLES || BACK_PREFIXES.any { key.startsWith(it) } || "project gutenberg" in key -> ChapterKind.BackMatter
        else -> ChapterKind.Content
    }
}
