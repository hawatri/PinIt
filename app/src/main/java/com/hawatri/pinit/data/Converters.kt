package com.hawatri.pinit.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hawatri.pinit.ui.FormatRange

class Converters {
    private val gson = Gson()

    // Every converter accepts null on the way in because old cloud backups (or
    // any JSON older than the field) deserialise these as null even though
    // Note.kt declares them non-null with a default. Gson reflection bypasses
    // Kotlin's default-value initialisation when a key is missing.

    @TypeConverter
    fun fromFormatRangeList(value: List<FormatRange>?): String = gson.toJson(value ?: emptyList<FormatRange>())

    @TypeConverter
    fun toFormatRangeList(value: String?): List<FormatRange> = try {
        if (value.isNullOrBlank()) emptyList()
        else {
            val type = object : TypeToken<List<FormatRange>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        }
    } catch (e: Exception) { emptyList() }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toStringList(value: String?): List<String> = try {
        if (value.isNullOrBlank()) emptyList()
        else gson.fromJson(value, Array<String>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String = gson.toJson(value ?: emptyList<Long>())

    @TypeConverter
    fun toLongList(value: String?): List<Long> = try {
        if (value.isNullOrBlank()) emptyList()
        else gson.fromJson(value, Array<Long>::class.java)?.toList() ?: emptyList()
    } catch (e: Exception) { emptyList() }
}