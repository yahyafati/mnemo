package com.yahyafati.mnemo.core.testing

import androidx.sqlite.SQLiteDriver
import com.yahyafati.mnemo.core.database.MnemoDatabase
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What makes a test class run on both targets: Robolectric on Android (the framework's SQLite and
 * the Android libraries need a `Context`), plain JUnit on desktop.
 */
expect abstract class PlatformRunner()

/** Base class of the tests that run on both targets (Android host tests and desktop tests). */
abstract class PlatformTest : PlatformRunner() {
    /** A scratch directory that is gone after each test. */
    @get:Rule
    val tmp = TemporaryFolder()
}

/** A fresh in-memory database, built the way the platform builds the real one. */
expect fun inMemoryDatabase(): MnemoDatabase

/** The database in [file], built the way the platform builds the real one. Close it before deleting the file. */
expect fun fileDatabase(file: File): MnemoDatabase

/** The SQLite driver the platform reads Anki packages with: the framework's on Android, the bundled one on desktop. */
expect fun testSqliteDriver(): SQLiteDriver
