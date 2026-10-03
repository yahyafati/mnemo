package com.yahyafati.mnemo.core.sync.webdav

/** One `response` of a WebDAV `207 Multi-Status` answer: where the resource is and whether it is a folder. */
internal class WebDavEntry(val href: String, val isCollection: Boolean)

/**
 * Reads the `href`s of a `PROPFIND` answer (docs/sync/ROADMAP.md S7). The store needs nothing else, so this is a few
 * strict patterns instead of an XML parser: servers disagree on namespace prefixes (`d:`, `D:`, `ns0:`, none), which the
 * patterns allow, and a parser would bring doctype and entity handling that has nothing to do here. Only the five
 * predefined entities and numeric references are decoded, and a document that declares entities of its own is refused.
 */
internal object WebDavMultistatus {
    private const val NAME = "(?:[A-Za-z_][\\w.-]*:)?"
    private val RESPONSE = Regex("<${NAME}response(?=[\\s>/])[^>]*>(.*?)</${NAME}response\\s*>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val HREF = Regex("<${NAME}href(?=[\\s>/])[^>]*>(.*?)</${NAME}href\\s*>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val COLLECTION = Regex("<${NAME}collection(?=[\\s>/])")
    private val NUMERIC = Regex("&#(x[0-9A-Fa-f]+|[0-9]+);")

    /** The entries of [body]; an empty list for a document with no `response`; `null` if it isn't something to trust. */
    fun parse(body: String): List<WebDavEntry>? {
        if ("<!ENTITY" in body || "<!DOCTYPE" in body) return null
        if (!body.contains("multistatus")) return null
        return RESPONSE.findAll(body).mapNotNull { match ->
            val inner = match.groupValues[1]
            val href = HREF.find(inner)?.groupValues?.get(1)?.let(::text)?.trim().orEmpty()
            if (href.isEmpty()) null else WebDavEntry(href, COLLECTION.containsMatchIn(inner))
        }.toList()
    }

    /** Element text: a CDATA section as it is, otherwise with entities decoded. */
    private fun text(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")) return trimmed.removePrefix("<![CDATA[").removeSuffix("]]>")
        return decode(raw)
    }

    private fun decode(raw: String): String {
        if ('&' !in raw) return raw
        val numeric = NUMERIC.replace(raw) { match ->
            val digits = match.groupValues[1]
            val code = if (digits.startsWith("x")) digits.drop(1).toIntOrNull(16) else digits.toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        // `&amp;` last, so "&amp;lt;" stays "&lt;".
        return numeric.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    }
}
