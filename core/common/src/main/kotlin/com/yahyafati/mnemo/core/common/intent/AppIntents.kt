package com.yahyafati.mnemo.core.common.intent

/**
 * Extras for intents that open the app somewhere in particular: the study reminder and the home
 * screen widget open the Study tab. Shared here because the senders (`:core:data`, the widget)
 * can't see `:app`, which reads them.
 */
object AppIntents {
    /** Which screen to open: one of the `OPEN_*` values. */
    const val EXTRA_OPEN = "com.yahyafati.mnemo.extra.OPEN"

    /** The Study tab (the Daily Mix). */
    const val OPEN_STUDY = "study"
}
