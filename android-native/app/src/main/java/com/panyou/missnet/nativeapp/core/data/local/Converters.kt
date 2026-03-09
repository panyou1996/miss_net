package com.panyou.missnet.nativeapp.core.data.local

import androidx.room.TypeConverter
import com.panyou.missnet.nativeapp.core.util.appJson
import kotlinx.serialization.encodeToString

class DatabaseConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = appJson.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { appJson.decodeFromString<List<String>>(value) }.getOrElse { emptyList() }
    }
}
