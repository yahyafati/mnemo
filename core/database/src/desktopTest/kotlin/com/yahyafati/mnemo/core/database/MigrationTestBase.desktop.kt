package com.yahyafati.mnemo.core.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest

actual abstract class MigrationTestBase actual constructor() : PlatformTest() {
    private val directory: File = Files.createTempDirectory("mnemo-migration").toFile()

    actual val databaseFile: File = File(directory, "migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        // Gradle runs tests in the module's directory.
        Path.of("schemas"),
        databaseFile.toPath(),
        BundledSQLiteDriver(),
        MnemoDatabase::class,
        { MnemoDatabaseConstructor.initialize() },
    )

    @AfterTest
    fun deleteDatabase() {
        directory.deleteRecursively()
    }

    actual fun createDatabase(version: Int): SQLiteConnection = helper.createDatabase(version)

    actual fun migrate(version: Int, vararg migrations: Migration): SQLiteConnection =
        helper.runMigrationsAndValidate(version, migrations.toList())

    actual fun open(): MnemoDatabase =
        MnemoDatabase.configure(Room.databaseBuilder<MnemoDatabase>(databaseFile.absolutePath), BundledSQLiteDriver())
}
