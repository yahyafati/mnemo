package com.yahyafati.mnemo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.android.build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
actual abstract class PlatformTest actual constructor()

actual fun inMemoryDatabase(): MnemoDatabase = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)

/**
 * `AndroidSQLiteDriver` can't run EXPLAIN (its statements only step queries it recognizes as
 * SELECT or PRAGMA), so the plan comes from a database with the same schema opened through the
 * framework's open helper instead of the driver.
 */
actual suspend fun MnemoDatabase.queryPlan(sql: String): List<String> = withContext(Dispatchers.IO) {
    val planDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MnemoDatabase::class.java).build()
    try {
        planDb.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
        }
    } finally {
        planDb.close()
    }
}
