package com.yahyafati.mnemo.core.testing

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.desktop.build
import java.io.File

actual abstract class PlatformRunner actual constructor()

actual fun inMemoryDatabase(): MnemoDatabase = MnemoDatabase.build(file = null)

actual fun fileDatabase(file: File): MnemoDatabase = MnemoDatabase.build(file)

actual fun testSqliteDriver(): SQLiteDriver = BundledSQLiteDriver()
