package com.yahyafati.mnemo.core.model

/**
 * Where Mnemo's public source, issue tracker and privacy policy live. One place, so Settings ›
 * About, the AI report action and the store listing (docs/release) agree. If the repository
 * moves, change [SOURCE] and the rest follows.
 */
object ProjectLinks {
    const val SOURCE = "https://github.com/yahyafati/mnemo"
    const val ISSUES = "$SOURCE/issues"

    /** Where a new version is published (sideload roadmap S5). Opened in the browser: the app itself never asks GitHub. */
    const val RELEASES = "$SOURCE/releases"

    /** The privacy policy as rendered on GitHub. R4 (docs/release/play-console.md §2) replaces it with the Pages URL once that loads. */
    const val PRIVACY_POLICY = "$SOURCE/blob/main/docs/release/privacy-policy.md"

    const val LICENSE_NAME = "GPL-3.0-or-later"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
}
