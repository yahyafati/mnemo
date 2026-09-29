package com.yahyafati.mnemo.core.anki

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** The packages in `src/test/resources`, written by real Anki (see `fixtures/make_fixtures.py`). */
internal object Fixtures {
    val driver = BundledSQLiteDriver()

    const val MODERN = "modern.apkg"
    const val LEGACY = "legacy.apkg"
    const val COLLECTION = "collection.colpkg"
    const val NO_SCHEDULING = "no-scheduling.apkg"

    fun file(name: String): File = File(checkNotNull(Fixtures::class.java.getResource("/$name")) { name }.toURI())

    fun open(name: String, tmp: TemporaryFolder): AnkiPackage = ApkgReader(driver).open(file(name), tmp.newFolder())

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Media name → `media:<sha256>` for everything in [pkg], as the importer stores it. */
    fun mediaRefs(pkg: AnkiPackage): Map<String, String> =
        pkg.media.associate { it.name to "media:" + sha256(pkg.openMedia(it).use { s -> s.readBytes() }) }
}
