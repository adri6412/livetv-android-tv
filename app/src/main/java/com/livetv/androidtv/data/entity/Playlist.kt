package com.livetv.androidtv.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String?,
    val filePath: String?,
    val isActive: Boolean = false,
    val channelCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val autoUpdate: Boolean = true,
    val updateInterval: Long = 24 * 60 * 60 * 1000L // 24 ore in millisecondi
) : Parcelable {
    
    fun isLocal(): Boolean = !filePath.isNullOrEmpty()
    
    fun isRemote(): Boolean = !url.isNullOrEmpty()
    
    fun getSource(): String {
        return when {
            isLocal() -> filePath!!
            isRemote() -> url!!
            else -> ""
        }
    }
    
    fun needsUpdate(): Boolean {
        if (!autoUpdate) return false
        val now = System.currentTimeMillis()
        return (now - lastUpdated) > updateInterval
    }
    
    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else {
            when {
                isLocal() -> filePath?.substringAfterLast("/") ?: "Playlist locale"
                isRemote() -> url?.let { 
                    try {
                        java.net.URL(it).host
                    } catch (e: Exception) {
                        "Playlist remota"
                    }
                } ?: "Playlist remota"
                else -> "Playlist sconosciuta"
            }
        }
    }
    
    companion object {
        fun createLocal(filePath: String, name: String = ""): Playlist {
            return Playlist(
                id = "local_${System.currentTimeMillis()}",
                name = name.ifEmpty { filePath.substringAfterLast("/") },
                url = null,
                filePath = filePath
            )
        }
        
        fun createRemote(url: String, name: String = ""): Playlist {
            return Playlist(
                id = "remote_${url.hashCode()}",
                name = name.ifEmpty { 
                    try {
                        java.net.URL(url).host
                    } catch (e: Exception) {
                        "Playlist remota"
                    }
                },
                url = url,
                filePath = null
            )
        }
    }
}