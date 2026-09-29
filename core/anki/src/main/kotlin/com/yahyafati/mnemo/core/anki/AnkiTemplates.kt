package com.yahyafati.mnemo.core.anki

/**
 * Reads which fields an Anki card template shows. Mnemo doesn't run Anki's HTML templates
 * (ADR 0003); it keeps the fields a template puts on each side, in order.
 */
internal object AnkiTemplates {
    private val REFERENCE = Regex("""\{\{([^{}]+)\}\}""")

    private data class Reference(val field: Int, val modifiers: List<String>)

    private fun references(template: String, fieldNames: List<String>): List<Reference> =
        REFERENCE.findAll(template).mapNotNull { match ->
            val inner = match.groupValues[1].trim()
            // Conditionals ({{#F}} {{^F}} {{/F}}) and comments only decide whether text shows.
            if (inner.isEmpty() || inner[0] in "#^/!=<>") return@mapNotNull null
            val name = inner.substringAfterLast(':').trim()
            val modifiers = inner.substringBeforeLast(':', "").split(':').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val index = fieldNames.indexOf(name).takeIf { it >= 0 }
                ?: fieldNames.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
                // {{FrontSide}}, {{Tags}}, {{Deck}} and other special fields aren't note fields.
                ?: return@mapNotNull null
            Reference(index, modifiers)
        }.toList()

    /** Fields shown on the question side, in order. `{{type:F}}` asks for F, so F is an answer. */
    fun frontFields(front: String, fieldNames: List<String>): List<Int> =
        references(front, fieldNames).filter { "type" !in it.modifiers }.map { it.field }.distinct()

    /** Fields the answer side adds to the question: what the user is checking against. */
    fun backFields(front: String, back: String, fieldNames: List<String>): List<Int> {
        val shown = frontFields(front, fieldNames).toSet()
        val typed = references(front, fieldNames).filter { "type" in it.modifiers }.map { it.field }
        return (typed + references(back, fieldNames).map { it.field }).distinct().filter { it !in shown }
    }

    /** Fields used as `{{cloze:F}}`, in order. */
    fun clozeFields(template: String, fieldNames: List<String>): List<Int> =
        references(template, fieldNames).filter { "cloze" in it.modifiers }.map { it.field }.distinct()
}
