package com.livetv.androidtv.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value != null) {
            Gson().toJson(value)
        } else {
            Gson().toJson(emptyList<String>())
        }
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            Gson().fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String {
        return if (value != null) {
            Gson().toJson(value)
        } else {
            Gson().toJson(emptyMap<String, String>())
        }
    }
    
    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        return try {
            Gson().fromJson(value, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}