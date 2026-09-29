package com.yahyafati.mnemo.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yahyafati.mnemo.core.database.converter.Converters
import com.yahyafati.mnemo.core.database.dao.AiProviderDao
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.MediaDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
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
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MnemoDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    abstract fun noteDao(): NoteDao

    abstract fun cardDao(): CardDao

    abstract fun reviewLogDao(): ReviewLogDao

    abstract fun mediaDao(): MediaDao

    abstract fun aiProviderDao(): AiProviderDao

    companion object {
        const val NAME = "mnemo.db"

        /** Builds the database. [name] null gives an in-memory database (tests). */
        fun build(context: Context, name: String? = NAME): MnemoDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, MnemoDatabase::class.java)
            } else {
                Room.databaseBuilder(context, MnemoDatabase::class.java, name)
            }
            return builder
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(SeedBuiltInNoteTypes)
                .build()
        }
    }
}

/** Inserts the built-in note types (Basic, Basic + Reversed, Cloze) when the database is created. */
internal object SeedBuiltInNoteTypes : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        NoteType.BuiltIns.forEach { type ->
            db.execSQL(
                "INSERT INTO note_types (id, name, kind, fields, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, 0, 0, NULL)",
                arrayOf(type.id, type.name, type.kind.name, Json.encodeToString(type.fields)),
            )
        }
    }
}
