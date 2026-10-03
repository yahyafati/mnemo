package com.yahyafati.mnemo.core.database.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yahyafati.mnemo.core.database.entity.SyncChangeEntity
import com.yahyafati.mnemo.core.database.entity.SyncStateEntity

/**
 * A table whose rows are synced. [keyColumns] are its primary key; [columns] are the columns whose
 * changes are recorded: every column except the key, `createdAt` and `updatedAt`. `SyncTriggersTest`
 * compares the lists with the real tables, so a column added to an entity without being listed here
 * fails a test instead of silently never syncing.
 */
class SyncedTable(
    val name: String,
    val keyColumns: List<String>,
    val columns: List<String>,
)

/**
 * The tables that sync (ADR 0013). AI providers, models, routes and usage never do (keys stay on the
 * device), and neither do the sync tables themselves.
 */
val SYNCED_TABLES: List<SyncedTable> = listOf(
    SyncedTable("decks", listOf("id"), listOf("parentId", "name", "description", "category", "starred", "deletedAt", "examDate")),
    SyncedTable("note_types", listOf("id"), listOf("name", "kind", "fields", "deletedAt")),
    SyncedTable(
        "notes",
        listOf("id"),
        listOf("deckId", "noteTypeId", "fields", "tags", "source", "deletedAt", "guid", "hint"),
    ),
    SyncedTable(
        "cards",
        listOf("id"),
        listOf(
            "noteId", "deckId", "templateOrd", "state", "due", "stability", "difficulty", "step", "lastReview", "reps",
            "lapses", "flagged", "starred", "suspended", "buriedUntil", "deletedAt",
        ),
    ),
    SyncedTable(
        "review_logs",
        listOf("id"),
        listOf(
            "cardId", "rating", "stateBefore", "reviewedAt", "elapsedDays", "scheduledDays", "durationMs", "stabilityAfter",
            "difficultyAfter", "deletedAt", "stateAfter", "stepAfter", "dueAfter", "repsAfter", "lapsesAfter",
        ),
    ),
    SyncedTable("media", listOf("id"), listOf("name", "mimeType", "size", "deletedAt")),
    SyncedTable("ai_answers", listOf("noteId", "kind"), listOf("text", "providerName", "modelId", "fieldsHash", "deletedAt")),
)

/**
 * The SQL that fills the outbox ([SyncChangeEntity]). Room doesn't manage triggers, so the same list
 * is run by the 5 → 6 migration and by the database's creation callback, and a test checks that a new
 * and a migrated database end up with the same ones.
 *
 * Both triggers are skipped unless sync is on and no remote change is being applied
 * (`sync_state.enabled = 1 AND applying = 0`), and test that first so a device that never syncs pays one
 * single-row lookup per write. The update trigger records only the columns whose value changed.
 */
internal object SyncTriggers {
    private const val STATE_TABLE = "sync_state"
    private const val CHANGES_TABLE = "sync_changes"
    private const val ACTIVE = "(SELECT enabled = 1 AND applying = 0 FROM $STATE_TABLE WHERE id = ${SyncStateEntity.SINGLE_ROW})"

    fun statements(): List<String> = SYNCED_TABLES.flatMap { listOf(insertTrigger(it), updateTrigger(it)) }

    fun create(connection: SQLiteConnection) {
        statements().forEach(connection::execSQL)
    }

    private fun insertTrigger(table: SyncedTable): String =
        """
        CREATE TRIGGER IF NOT EXISTS sync_ins_${table.name} AFTER INSERT ON ${table.name}
        WHEN $ACTIVE
        BEGIN
            INSERT INTO $CHANGES_TABLE (tbl, rowId, fields, at)
            VALUES ('${table.name}', ${table.rowId("NEW")}, '${SyncChangeEntity.ALL_FIELDS}', NEW.updatedAt);
        END
        """.trimIndent()

    private fun updateTrigger(table: SyncedTable): String {
        val changed = table.columns.joinToString(" OR ") { "NEW.$it IS NOT OLD.$it" }
        // ',a,b' without its first comma: the names of the columns that changed.
        val names = table.columns.joinToString(" || ") { "CASE WHEN NEW.$it IS NOT OLD.$it THEN ',$it' ELSE '' END" }
        return """
        CREATE TRIGGER IF NOT EXISTS sync_upd_${table.name} AFTER UPDATE ON ${table.name}
        WHEN $ACTIVE AND ($changed)
        BEGIN
            INSERT INTO $CHANGES_TABLE (tbl, rowId, fields, at)
            VALUES ('${table.name}', ${table.rowId("NEW")}, substr($names, 2), NEW.updatedAt);
        END
        """.trimIndent()
    }

    private fun SyncedTable.rowId(row: String): String = keyColumns.joinToString(" || '/' || ") { "$row.$it" }

}

/**
 * Makes sure the single `sync_state` row exists, with a new random device id if it has to create it.
 * Run by the migration and every time the database opens, so a database that came from somewhere
 * else (an older backup, say) can never be without an identity.
 */
internal fun ensureSyncState(connection: SQLiteConnection) {
    connection.prepare(
        "INSERT OR IGNORE INTO sync_state (id, deviceId, clock, enabled, applying) VALUES (${SyncStateEntity.SINGLE_ROW}, ?, 0, 0, 0)",
    ).use { statement ->
        statement.bindText(1, java.util.UUID.randomUUID().toString())
        statement.step()
    }
}
