package com.yahyafati.mnemo.core.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import java.io.File

actual abstract class MigrationTestBase actual constructor() : PlatformTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    actual val databaseFile: File = context.getDatabasePath("migration-test").also {
        it.parentFile?.mkdirs()
        it.delete()
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        databaseFile,
        AndroidSQLiteDriver(),
        MnemoDatabase::class,
        { MnemoDatabaseConstructor.initialize() },
    )

    actual fun createDatabase(version: Int): SQLiteConnection = helper.createDatabase(version)

    actual fun migrate(version: Int, vararg migrations: Migration): SQLiteConnection =
        helper.runMigrationsAndValidate(version, migrations.toList())

    actual fun open(): MnemoDatabase =
        MnemoDatabase.configure(Room.databaseBuilder<MnemoDatabase>(context, databaseFile.absolutePath), AndroidSQLiteDriver())
}
