package com.yahyafati.mnemo.core.ingest

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small EPUBs in memory, so each test shows the book it reads. Names are used as given (`../x` too). */
internal class EpubBuilder {
    private val files = LinkedHashMap<String, ByteArray>()

    fun file(name: String, content: String) = apply { files[name] = content.toByteArray() }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

internal class Ch(val file: String, val title: String, val body: String, val bodyAttrs: String = "")

internal fun words(count: Int, prefix: String = "w") = (1..count).joinToString(" ") { "$prefix$it" }

internal fun para(text: String) = "<p>$text</p>"

internal fun xhtml(body: String, bodyAttrs: String = "", title: String = "t") =
    """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>$title</title></head><body $bodyAttrs>$body</body></html>"""

internal const val DEFAULT_METADATA = "<dc:title>Test Book</dc:title><dc:creator>Ada Author</dc:creator><dc:language>en</dc:language>"

internal fun container(opfPath: String = "OEBPS/content.opf") =
    """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles></container>"""

internal fun opf(metadata: String, manifest: String, spine: String, version: String = "3.0", spineAttrs: String = "", extra: String = "") =
    """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="$version" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata><manifest>$manifest</manifest><spine $spineAttrs>$spine</spine>$extra</package>"""

internal fun navDocument(list: String) =
    xhtml("""<nav epub:type="toc"><ol>$list</ol></nav>""")

internal fun navItem(href: String, title: String, children: String = "") =
    "<li><a href=\"$href\">$title</a>${if (children.isEmpty()) "" else "<ol>$children</ol>"}</li>"

/** An EPUB 3 in `OEBPS/`: one file per chapter, all in the reading order, and a `nav` (the default lists each chapter). */
internal fun epub3(
    chapters: List<Ch>,
    nav: String = chapters.joinToString("") { navItem(it.file, it.title) },
    metadata: String = DEFAULT_METADATA,
    extra: EpubBuilder.() -> Unit = {},
): EpubBuilder {
    val builder = EpubBuilder()
        .file("META-INF/container.xml", container())
        .file("OEBPS/nav.xhtml", navDocument(nav))
    chapters.forEach { builder.file("OEBPS/${it.file}", xhtml(it.body, it.bodyAttrs, it.title)) }
    val manifest = """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" +
        chapters.mapIndexed { index, ch -> """<item id="c$index" href="${ch.file}" media-type="application/xhtml+xml"/>""" }.joinToString("")
    val spine = chapters.indices.joinToString("") { """<itemref idref="c$it"/>""" }
    builder.file("OEBPS/content.opf", opf(metadata, manifest, spine))
    builder.extra()
    return builder
}
