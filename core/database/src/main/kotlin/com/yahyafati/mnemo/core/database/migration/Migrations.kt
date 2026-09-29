package com.yahyafati.mnemo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, oldest first. Version 1 is the first shipped schema. Add each new one
 * here and a case to `MigrationTest`; the SQL must match what Room generates for the entities
 * (compare with `core/database/schemas/<version>.json`).
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    Migration1To2,
)

/** Phase 2: content-addressed media, and Anki guids on notes for duplicate-free imports. */
internal object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                "`size` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `guid` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_guid` ON `notes` (`guid`)")
    }
}
