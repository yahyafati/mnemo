package com.yahyafati.mnemo.core.ai.prompt

/**
 * The tags Smart Extract's prompts use to fence text off as material (ADR 0015): `<source>`, `<before>` and
 * `<context>`. A tag of one of them inside fenced text (a page about HTML, or a trick) must not end its fence.
 */
internal object PromptFence {
    private val TAG = Regex("""<(/?)(source|before|context)\b""", RegexOption.IGNORE_CASE)

    fun escape(text: String): String = text.replace(TAG) { "&lt;${it.groupValues[1]}${it.groupValues[2].lowercase()}" }
}
