package com.yahyafati.mnemo.core.testing

import android.content.Context
import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.android.build
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
actual abstract class PlatformRunner actual constructor()

actual fun inMemoryDatabase(): MnemoDatabase =
    MnemoDatabase.build(ApplicationProvider.getApplicationContext<Context>(), name = null)

actual fun fileDatabase(file: File): MnemoDatabase {
    file.absoluteFile.parentFile?.mkdirs()
    return MnemoDatabase.configure(
        Room.databaseBuilder<MnemoDatabase>(ApplicationProvider.getApplicationContext<Context>(), file.absolutePath),
        AndroidSQLiteDriver(),
    )
}

actual fun testSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
