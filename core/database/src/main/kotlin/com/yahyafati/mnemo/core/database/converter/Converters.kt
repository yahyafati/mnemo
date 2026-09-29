package com.yahyafati.mnemo.core.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/** Note fields and tags are stored as JSON arrays, provider headers as a JSON object. */
internal class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> = Json.decodeFromString(value)
}
