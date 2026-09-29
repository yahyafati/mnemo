package com.yahyafati.mnemo.core.model

/**
 * Where Mnemo's public source, issue tracker and privacy policy live. One place, so Settings ›
 * About, the AI report action and the store listing (docs/release) agree. If the repository
 * moves, change [SOURCE] and the rest follows.
 */
object ProjectLinks {
    const val SOURCE = "https://github.com/yahyafati/mnemo"
    const val ISSUES = "$SOURCE/issues"

    /** The privacy policy as rendered on GitHub. R4 may replace it with a Pages URL. */
    const val PRIVACY_POLICY = "$SOURCE/blob/main/docs/release/privacy-policy.md"

    const val LICENSE_NAME = "GPL-3.0-or-later"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
}
