package com.android.bakchodai.data.local

import androidx.room.TypeConverter
import com.android.bakchodai.data.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TypeConverters {
    private val gson = Gson()

    // For participants map
    @TypeConverter
    fun fromParticipantsMap(value: Map<String, Boolean>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toParticipantsMap(value: String): Map<String, Boolean> {
        val mapType = object : TypeToken<Map<String, Boolean>>() {}.type
        return gson.fromJson(value, mapType)
    }

    // For messages map
    @TypeConverter
    fun fromMessagesMap(value: Map<String, Message>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toMessagesMap(value: String): Map<String, Message> {
        val mapType = object : TypeToken<Map<String, Message>>() {}.type
        return gson.fromJson(value, mapType)
    }
}