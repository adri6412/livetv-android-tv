package com.livetv.androidtv.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey
    val id: String,
    val number: Int,
    val name: String,
    val group: String?,
    val logoUrl: String?,
    val streamUrl: String,
    val epgId: String?,
    val epgDisplayName: String?, // Nuovo campo per il nome da usare per il matching EPG
    val isFavorite: Boolean = false,
    val isHD: Boolean = false,
    val audioTracks: List<String> = emptyList(),
    val subtitleTracks: List<String> = emptyList(),
    val hbbtvUrl: String? = null,
    val quality: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val frameRate: Double? = null,
    val isActive: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun getDisplayName(): String {
        return if (number > 0) "$number - $name" else name
    }
    
    fun getQualityInfo(): String {
        val parts = mutableListOf<String>()
        
        if (isHD) parts.add("HD")
        quality?.let { parts.add(it) }
        resolution?.let { parts.add(it) }
        
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else "SD"
    }
    
    fun hasHbbTV(): Boolean = !hbbtvUrl.isNullOrEmpty()
    
    fun hasEPG(): Boolean = !epgId.isNullOrEmpty()
    
    // Metodo per ottenere il nome da usare per il matching EPG
    fun getEpgMatchingName(): String {
        return epgDisplayName ?: name
    }
    
    companion object {
        fun createFromM3U(
            id: String,
            name: String,
            url: String,
            attributes: Map<String, String> = emptyMap()
        ): Channel {
            val number = attributes["tvg-chno"]?.toIntOrNull() ?: 0
            val group = attributes["group-title"]
            val logo = attributes["tvg-logo"]
            val epgId = attributes["tvg-id"]
            val hbbtvUrl = attributes["hbbtv-url"]
            
            // Estrai il nome del canale per il matching EPG (rimuovi numeri e trattini)
            val epgDisplayName = name.replace(Regex("^[0-9]+\\s*-\\s*"), "").trim()
            
            return Channel(
                id = id,
                number = number,
                name = name,
                group = group,
                logoUrl = logo,
                streamUrl = url,
                epgId = epgId,
                epgDisplayName = epgDisplayName,
                hbbtvUrl = hbbtvUrl,
                isHD = name.contains("HD", ignoreCase = true) || 
                       attributes["resolution"]?.contains("1080") == true ||
                       attributes["resolution"]?.contains("720") == true
            )
        }
    }
}