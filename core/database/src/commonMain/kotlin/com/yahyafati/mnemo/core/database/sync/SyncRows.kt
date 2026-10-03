package com.yahyafati.mnemo.core.database.sync

import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import com.yahyafati.mnemo.core.database.MnemoDatabase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Rows of the synced tables as JSON objects, for the merge engine (docs/sync/ROADMAP.md S3). Every column of
 * these tables is an integer, a real, text or null, so a row converts to JSON and back without a mapping per
 * table (a boolean is 0 or 1, a list of strings is the JSON text Room already stores). Which columns exist
 * comes from [SYNCED_TABLES], and a column the caller names that isn't in the table is refused, so a change file
 * can't make the engine write an arbitrary column.
 *
 * Writes go through the same connection as the DAOs, so inside a `TransactionRunner` block they are part of it.
 * The sync triggers fire for them like for any write, which is how a local merge result reaches the outbox, and
 * why applying remote changes first sets `sync_state.applying`.
 */
class SyncRows(private val database: MnemoDatabase) {
    /** The row with key [rowId] (parts joined with `/` for a composite key), every column, or null. */
    suspend fun get(table: String, rowId: String): JsonObject? {
        val info = info(table)
        val key = keyValues(info, rowId)
        val sql = "SELECT * FROM ${info.name} WHERE ${info.whereKey()}"
        return database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                key.forEachIndexed { i, part -> statement.bindText(i + 1, part) }
                if (statement.step()) statement.toJson() else null
            }
        }
    }

    /** Inserts a row. [row] has every column but the key's, which come from [rowId]. */
    suspend fun insert(table: String, rowId: String, row: Map<String, JsonElement>) {
        val info = info(table)
        val key = keyValues(info, rowId)
        val columns = columnsOf(info, row.keys)
        val sql = "INSERT INTO ${info.name} (${(info.keyColumns + columns).joinToString { quote(it) }}) " +
            "VALUES (${(0 until info.keyColumns.size + columns.size).joinToString { "?" }})"
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                key.forEachIndexed { i, part -> statement.bindText(i + 1, part) }
                columns.forEachIndexed { i, column -> statement.bind(key.size + i + 1, row.getValue(column)) }
                statement.step()
            }
        }
    }

    /** Sets [values] on the row with key [rowId]. */
    suspend fun update(table: String, rowId: String, values: Map<String, JsonElement>) {
        if (values.isEmpty()) return
        val info = info(table)
        val key = keyValues(info, rowId)
        val columns = columnsOf(info, values.keys)
        val sql = "UPDATE ${info.name} SET ${columns.joinToString { "${quote(it)} = ?" }} WHERE ${info.whereKey()}"
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                columns.forEachIndexed { i, column -> statement.bind(i + 1, values.getValue(column)) }
                key.forEachIndexed { i, part -> statement.bindText(columns.size + i + 1, part) }
                statement.step()
            }
        }
    }

    private fun info(table: String): SyncedTable =
        SYNCED_TABLES.firstOrNull { it.name == table } ?: throw IllegalArgumentException("$table doesn't sync")

    private fun keyValues(info: SyncedTable, rowId: String): List<String> {
        val parts = rowId.split('/', limit = info.keyColumns.size)
        require(parts.size == info.keyColumns.size) { "$rowId isn't a key of ${info.name}" }
        return parts
    }

    private fun columnsOf(info: SyncedTable, names: Collection<String>): List<String> {
        val known = info.columns + TIMESTAMPS
        names.forEach { require(it in known) { "${info.name} has no column $it" } }
        return names.toList()
    }

    private fun SyncedTable.whereKey(): String = keyColumns.joinToString(" AND ") { "${quote(it)} = ?" }

    private fun quote(column: String) = "`$column`"

    private fun SQLiteStatement.bind(index: Int, value: JsonElement) {
        if (value is JsonNull) {
            bindNull(index)
            return
        }
        val primitive = value as? JsonPrimitive ?: throw IllegalArgumentException("A column holds a JSON primitive, not $value")
        when {
            primitive.isString -> bindText(index, primitive.content)
            primitive.longOrNull != null -> bindLong(index, primitive.longOrNull!!)
            primitive.doubleOrNull != null -> bindDouble(index, primitive.doubleOrNull!!)
            else -> throw IllegalArgumentException("Not a column value: $value")
        }
    }

    private fun SQLiteStatement.toJson(): JsonObject = JsonObject(
        buildMap {
            for (i in 0 until getColumnCount()) {
                put(
                    getColumnName(i),
                    when (getColumnType(i)) {
                        SQLITE_DATA_NULL -> JsonNull
                        SQLITE_DATA_INTEGER -> JsonPrimitive(getLong(i))
                        SQLITE_DATA_FLOAT -> JsonPrimitive(getDouble(i))
                        else -> JsonPrimitive(getText(i))
                    },
                )
            }
        },
    )

    private companion object {
        val TIMESTAMPS = listOf("createdAt", "updatedAt")
    }
}
