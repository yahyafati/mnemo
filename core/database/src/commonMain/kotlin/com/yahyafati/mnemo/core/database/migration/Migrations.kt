package com.yahyafati.mnemo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yahyafati.mnemo.core.database.insertBuiltInNoteTypes
import com.yahyafati.mnemo.core.model.NoteType

/**
 * Every schema migration, oldest first. Version 1 is the first shipped schema. Add each new one
 * here and a case to `MigrationTest`; the SQL must match what Room generates for the entities
 * (compare with `core/database/schemas/<version>.json`). They use Room's driver API
 * ([SQLiteConnection]), so the same code migrates the Android and the desktop database.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    Migration1To2,
    Migration2To3,
    Migration3To4,
    Migration4To5,
)

/** Phase 2: content-addressed media, and Anki guids on notes for duplicate-free imports. */
internal object Migration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `media` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                "`size` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `guid` TEXT")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_guid` ON `notes` (`guid`)")
    }
}

/**
 * Phase 3: AI providers, their models, per-task routes and token usage. API keys are not in the
 * database at all (ADR 0005).
 */
internal object Migration2To3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_providers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                "`presetId` TEXT, `headers` TEXT NOT NULL, `defaultModel` TEXT, `enabled` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `timeoutSeconds` INTEGER NOT NULL, `isLocal` INTEGER NOT NULL, " +
                "`disclosureAcceptedAt` INTEGER, `lastTestOk` INTEGER, `lastTestAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_models` (`providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, " +
                "`supportsJson` INTEGER NOT NULL, `supportsVision` INTEGER NOT NULL, `supportsStreaming` INTEGER NOT NULL, " +
                "`manual` INTEGER NOT NULL, `capabilitiesSetByUser` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`providerId`, `modelId`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_task_routes` (`task` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`task`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_task_routes_providerId` ON `ai_task_routes` (`providerId`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_usage` (`id` TEXT NOT NULL, `providerId` TEXT NOT NULL, `task` TEXT, " +
                "`modelId` TEXT NOT NULL, `requests` INTEGER NOT NULL, `promptTokens` INTEGER NOT NULL, " +
                "`completionTokens` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_providerId_task` ON `ai_usage` (`providerId`, `task`)")
    }
}

/**
 * Phase 6: note hints, deck exam dates, and the Type-in answer and Multiple choice note types
 * (ADR 0008).
 */
internal object Migration3To4 : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `hint` TEXT")
        connection.execSQL("ALTER TABLE `decks` ADD COLUMN `examDate` INTEGER")
        insertBuiltInNoteTypes(connection, listOf(NoteType.TypeIn, NoteType.MultipleChoice))
    }
}

/** Saved study-time AI answers ("Explain this", "Give me an example"), one per note and kind. */
internal object Migration4To5 : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_answers` (`noteId` TEXT NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`providerName` TEXT NOT NULL, `modelId` TEXT NOT NULL, `fieldsHash` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`noteId`, `kind`))",
        )
    }
}
