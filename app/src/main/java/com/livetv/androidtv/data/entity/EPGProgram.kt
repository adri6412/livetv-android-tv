package com.livetv.androidtv.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["startTime"]),
        Index(value = ["endTime"])
    ]
)
data class EPGProgram(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startTime: Long,
    val endTime: Long,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val year: Int? = null,
    val rating: String? = null,
    val director: String? = null,
    val actors: List<String>? = null,
    val genres: List<String>? = null,
    val posterUrl: String? = null,
    val isLive: Boolean = false,
    val isRepeat: Boolean = false,
    val hasSubtitles: Boolean = false,
    val audioDescription: Boolean = false,
    val hbbtvUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun getDuration(): Long = endTime - startTime
    
    fun getDurationMinutes(): Int = (getDuration() / (1000 * 60)).toInt()
    
    fun isCurrentlyAiring(): Boolean {
        val now = System.currentTimeMillis()
        return now >= startTime && now < endTime
    }
    
    fun hasEnded(): Boolean = System.currentTimeMillis() >= endTime
    
    fun hasStarted(): Boolean = System.currentTimeMillis() >= startTime
    
    fun getProgress(): Float {
        val now = System.currentTimeMillis()
        if (now < startTime) return 0f
        if (now >= endTime) return 1f
        
        return (now - startTime).toFloat() / getDuration().toFloat()
    }
    
    fun getStartTimeFormatted(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTime))
    }
    
    fun getEndTimeFormatted(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endTime))
    }
    
    fun getDateFormatted(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(startTime))
    }
    
    fun getTimeRangeFormatted(): String {
        return "${getStartTimeFormatted()} - ${getEndTimeFormatted()}"
    }
    
    fun getFullDescription(): String {
        val parts = mutableListOf<String>()
        
        description?.let { parts.add(it) }
        
        if (episodeNumber != null && seasonNumber != null) {
            parts.add("Stagione $seasonNumber, Episodio $episodeNumber")
        } else if (episodeNumber != null) {
            parts.add("Episodio $episodeNumber")
        }
        
        year?.let { parts.add("Anno: $it") }
        director?.let { parts.add("Regia: $it") }
        
        if (!actors.isNullOrEmpty()) {
            parts.add("Cast: ${actors.take(3).joinToString(", ")}")
        }
        
        return parts.joinToString("\n\n")
    }
    
    fun getGenresString(): String {
        return genres?.joinToString(", ") ?: ""
    }
    
    fun hasHbbTV(): Boolean = !hbbtvUrl.isNullOrEmpty()
    
    companion object {
        fun createFromXMLTV(
            channelId: String,
            title: String,
            startTime: Long,
            endTime: Long,
            attributes: Map<String, String> = emptyMap()
        ): EPGProgram {
            return EPGProgram(
                id = "${channelId}_${startTime}",
                channelId = channelId,
                title = title,
                description = attributes["desc"],
                category = attributes["category"],
                startTime = startTime,
                endTime = endTime,
                episodeNumber = attributes["episode-num"]?.toIntOrNull(),
                seasonNumber = attributes["season-num"]?.toIntOrNull(),
                year = attributes["date"]?.substring(0, 4)?.toIntOrNull(),
                rating = attributes["rating"],
                director = attributes["director"],
                actors = attributes["actor"]?.split(",")?.map { it.trim() },
                genres = attributes["genre"]?.split(",")?.map { it.trim() },
                posterUrl = attributes["icon"],
                isLive = attributes["live"] == "true",
                isRepeat = attributes["repeat"] == "true",
                hasSubtitles = attributes["subtitles"] == "true",
                audioDescription = attributes["audio-desc"] == "true",
                hbbtvUrl = attributes["hbbtv-url"]
            )
        }
    }
}