package com.yahyafati.mnemo.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yahyafati.mnemo.core.database.migration.ALL_MIGRATIONS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrations against the exported schemas in `core/database/schemas/`. With only version 1 so
 * far, this checks that the committed schema matches what Room generates. Each new version adds
 * a `migrate<N>To<N+1>` case that creates the old schema, inserts rows, and runs
 * `helper.runMigrationsAndValidate`.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemoDatabase::class.java,
    )

    @Test
    fun exportedSchemaMatchesEntities() {
        helper.createDatabase(TEST_DB, 1).close()

        // Room validates the on-disk schema against the entities when it opens the database.
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
            .apply { openHelper.writableDatabase.close() }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
