package com.hawatri.pinit.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hawatri.pinit.ui.FormatRange

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromFormatRangeList(value: List<FormatRange>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFormatRangeList(value: String): List<FormatRange> {
        val type = object : TypeToken<List<FormatRange>>() {}.type
        return gson.fromJson(value, type)
    }
}