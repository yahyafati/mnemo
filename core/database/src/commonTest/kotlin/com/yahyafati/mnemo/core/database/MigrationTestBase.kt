package com.yahyafati.mnemo.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import java.io.File

/**
 * What a migration test needs, on both targets: Room's `MigrationTestHelper` reads the exported
 * schemas (`core/database/schemas/`) and works on [databaseFile] (deleted after each test).
 */
expect abstract class MigrationTestBase() : PlatformTest {
    /** The file the test database lives in. */
    val databaseFile: File

    /** Creates [databaseFile] with the exported schema of [version], as that version's app did. */
    fun createDatabase(version: Int): SQLiteConnection

    /** Runs [migrations] on [databaseFile] up to [version] and validates it against that schema. */
    fun migrate(version: Int, vararg migrations: Migration): SQLiteConnection

    /** Opens [databaseFile] the way the app does, with every migration. */
    fun open(): MnemoDatabase
}
