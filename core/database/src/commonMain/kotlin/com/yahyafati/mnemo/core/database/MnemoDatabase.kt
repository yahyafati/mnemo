package com.yahyafati.mnemo.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.yahyafati.mnemo.core.database.converter.Converters
import com.yahyafati.mnemo.core.database.dao.AiProviderDao
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.MediaDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.database.dao.StatsDao
import com.yahyafati.mnemo.core.database.entity.AiModelEntity
import com.yahyafati.mnemo.core.database.entity.AiProviderEntity
import com.yahyafati.mnemo.core.database.entity.AiTaskRouteEntity
import com.yahyafati.mnemo.core.database.entity.AiUsageEntity
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.MediaEntity
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import com.yahyafati.mnemo.core.database.entity.NoteTypeEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.database.migration.ALL_MIGRATIONS
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * The single source of truth. The schema is exported to `core/database/schemas/` and committed;
 * every version bump needs a migration in [ALL_MIGRATIONS] and a `MigrationTest` case.
 * Destructive migration is never enabled.
 */
@Database(
    entities = [
        DeckEntity::class,
        NoteTypeEntity::class,
        NoteEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
        MediaEntity::class,
        AiProviderEntity::class,
        AiModelEntity::class,
        AiTaskRouteEntity::class,
        AiUsageEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(MnemoDatabaseConstructor::class)
abstract class MnemoDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    abstract fun noteDao(): NoteDao

    abstract fun cardDao(): CardDao

    abstract fun reviewLogDao(): ReviewLogDao

    abstract fun mediaDao(): MediaDao

    abstract fun aiProviderDao(): AiProviderDao

    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "mnemo.db"

        /**
         * Finishes a [builder] made by the platform (a file path or a `Context` differ, see
         * `MnemoDatabase.build` in `androidMain` and `desktopMain`).
         */
        fun configure(builder: RoomDatabase.Builder<MnemoDatabase>, driver: SQLiteDriver): MnemoDatabase =
            builder
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(SeedBuiltInNoteTypes)
                .build()
    }
}

// Room generates the actual.
@Suppress("KotlinNoActualForExpect")
expect object MnemoDatabaseConstructor : RoomDatabaseConstructor<MnemoDatabase> {
    override fun initialize(): MnemoDatabase
}

/** Inserts the built-in note types when the database is created. */
internal object SeedBuiltInNoteTypes : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        insertBuiltInNoteTypes(connection, NoteType.BuiltIns)
    }
}

/** Inserts [types] into `note_types`, skipping any already there. Also used by migrations. */
internal fun insertBuiltInNoteTypes(connection: SQLiteConnection, types: List<NoteType>) {
    connection.prepare(
        "INSERT OR IGNORE INTO note_types (id, name, kind, fields, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, 0, 0, NULL)",
    ).use { statement ->
        types.forEach { type ->
            statement.reset()
            statement.bindText(1, type.id)
            statement.bindText(2, type.name)
            statement.bindText(3, type.kind.name)
            statement.bindText(4, Json.encodeToString(type.fields))
            statement.step()
        }
    }
}
