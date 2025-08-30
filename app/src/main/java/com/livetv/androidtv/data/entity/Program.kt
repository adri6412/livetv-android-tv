package com.livetv.androidtv.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
@Entity(tableName = "programs")
data class Program(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val category: String,
    val isCurrent: Boolean = false,
    val episodeNumber: String? = null,
    val seasonNumber: String? = null,
    val rating: String? = null,
    val director: String? = null,
    val actors: List<String> = emptyList(),
    val isLive: Boolean = false,
    val isRepeat: Boolean = false,
    val hasSubtitles: Boolean = false,
    val hasAudioDescription: Boolean = false,
    val iconUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable {
    
    /**
     * Restituisce la durata del programma in minuti
     */
    fun getDurationMinutes(): Int {
        return ((endTime - startTime) / (1000 * 60)).toInt()
    }
    
    /**
     * Restituisce la durata formattata (es. "1h 30m")
     */
    fun getDurationFormatted(): String {
        val duration = getDurationMinutes()
        val hours = duration / 60
        val minutes = duration % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Restituisce l'orario di inizio formattato
     */
    fun getStartTimeFormatted(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(startTime))
    }
    
    /**
     * Restituisce l'orario di fine formattato
     */
    fun getEndTimeFormatted(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(endTime))
    }
    
    /**
     * Restituisce l'intervallo orario formattato
     */
    fun getTimeRangeFormatted(): String {
        return "${getStartTimeFormatted()} - ${getEndTimeFormatted()}"
    }
    
    /**
     * Restituisce la data formattata
     */
    fun getDateFormatted(): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        return dateFormat.format(Date(startTime))
    }
    
    /**
     * Verifica se il programma è in corso
     */
    fun isCurrentlyOn(): Boolean {
        val now = System.currentTimeMillis()
        return now >= startTime && now < endTime
    }
    
    /**
     * Verifica se il programma è già terminato
     */
    fun isFinished(): Boolean {
        return System.currentTimeMillis() > endTime
    }
    
    /**
     * Verifica se il programma deve ancora iniziare
     */
    fun isUpcoming(): Boolean {
        return System.currentTimeMillis() < startTime
    }
    
    /**
     * Restituisce il tempo rimanente in minuti
     */
    fun getRemainingMinutes(): Int {
        val now = System.currentTimeMillis()
        return if (now < endTime) {
            ((endTime - now) / (1000 * 60)).toInt()
        } else {
            0
        }
    }
    
    /**
     * Restituisce il tempo rimanente formattato
     */
    fun getRemainingFormatted(): String {
        val remaining = getRemainingMinutes()
        val hours = remaining / 60
        val minutes = remaining % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m rimanenti"
            hours > 0 -> "${hours}h rimanenti"
            else -> "${minutes}m rimanenti"
        }
    }
    
    companion object {
        /**
         * Crea un programma da dati XMLTV
         */
        fun createFromXMLTV(
            channelId: String,
            title: String,
            description: String,
            startTime: Long,
            endTime: Long,
            category: String = "",
            attributes: Map<String, String> = emptyMap()
        ): Program {
            return Program(
                id = "${channelId}_${startTime}",
                channelId = channelId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                category = category,
                episodeNumber = attributes["episode-num"],
                seasonNumber = attributes["season-num"],
                rating = attributes["rating"],
                director = attributes["director"],
                actors = attributes["actor"]?.split(", ") ?: emptyList(),
                isLive = attributes["live"] == "true",
                isRepeat = attributes["repeat"] == "true",
                hasSubtitles = attributes["subtitles"] == "true",
                hasAudioDescription = attributes["audio-desc"] == "true",
                iconUrl = attributes["icon"]
            )
        }
    }
}
