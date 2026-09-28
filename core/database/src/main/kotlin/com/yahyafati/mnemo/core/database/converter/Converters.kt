package com.yahyafati.mnemo.core.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/** Note fields and tags are stored as JSON arrays. */
internal class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = Json.decodeFromString(value)
}
