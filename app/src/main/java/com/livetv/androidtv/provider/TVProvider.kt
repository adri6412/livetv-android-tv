package com.livetv.androidtv.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.livetv.androidtv.data.LiveTVDatabase
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.repository.ChannelRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class LiveTVProvider : ContentProvider() {
    
    companion object {
        private const val TAG = "LiveTVProvider"
        private const val AUTHORITY = "com.livetv.androidtv.provider"
        
        // URI per i canali
        private const val CHANNELS = 1
        private const val CHANNEL_ID = 2
        
        // URI per i programmi
        private const val PROGRAMS = 3
        private const val PROGRAM_ID = 4
        
        // URI per i canali specifici
        private const val CHANNEL_BY_NUMBER = 5
        
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "channels", CHANNELS)
            addURI(AUTHORITY, "channels/#", CHANNEL_ID)
            addURI(AUTHORITY, "programs", PROGRAMS)
            addURI(AUTHORITY, "programs/#", PROGRAM_ID)
            addURI(AUTHORITY, "channels/number/*", CHANNEL_BY_NUMBER)
        }
        
        // Colonne per i canali
        val CHANNEL_COLUMNS = arrayOf(
            "_id",
            "display_name",
            "display_number",
            "input_id",
            "type",
            "service_type",
            "original_network_id",
            "transport_stream_id",
            "video_pid",
            "audio_pid",
            "pcr_pid"
        )
        
        // Colonne per i programmi
        val PROGRAM_COLUMNS = arrayOf(
            "_id",
            "channel_id",
            "title",
            "description",
            "start_time_utc_millis",
            "end_time_utc_millis",
            "category",
            "content_rating"
        )
    }
    
    private lateinit var channelRepository: ChannelRepository
    
    override fun onCreate(): Boolean {
        Log.d(TAG, "LiveTVProvider creato")
        
        try {
            val database = LiveTVDatabase.getDatabase(context!!)
            channelRepository = ChannelRepository(database.channelDao())
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'inizializzazione del provider", e)
            return false
        }
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "Query ricevuta: $uri")
        
        return when (uriMatcher.match(uri)) {
            CHANNELS -> queryChannels(projection, selection, selectionArgs, sortOrder)
            CHANNEL_ID -> queryChannelById(uri.lastPathSegment, projection)
            CHANNEL_BY_NUMBER -> queryChannelByNumber(uri.lastPathSegment, projection)
            PROGRAMS -> queryPrograms(projection, selection, selectionArgs, sortOrder)
            PROGRAM_ID -> queryProgramById(uri.lastPathSegment, projection)
            else -> {
                Log.w(TAG, "URI non riconosciuto: $uri")
                null
            }
        }
    }
    
    private fun queryChannels(
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return try {
            val columns = projection ?: CHANNEL_COLUMNS
            val cursor = MatrixCursor(columns)
            
            runBlocking {
                val channels = channelRepository.getAllChannels().first()
                
                channels.forEach { channel ->
                    val row = arrayOf(
                        channel.id.hashCode().toLong(), // _id
                        channel.name, // display_name
                        channel.number.toString(), // display_number
                        "livetv_input", // input_id
                        "SERVICE_TYPE_AUDIO_VIDEO", // type
                        "SERVICE_TYPE_AUDIO_VIDEO", // service_type
                        1, // original_network_id
                        1, // transport_stream_id
                        0, // video_pid
                        0, // audio_pid
                        0  // pcr_pid
                    )
                    cursor.addRow(row)
                }
            }
            
            cursor
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella query dei canali", e)
            null
        }
    }
    
    private fun queryChannelById(channelId: String?, projection: Array<out String>?): Cursor? {
        if (channelId == null) return null
        
        return try {
            val columns = projection ?: CHANNEL_COLUMNS
            val cursor = MatrixCursor(columns)
            
            runBlocking {
                val channels = channelRepository.getAllChannels().first()
                val channel = channels.find { it.id == channelId }
                
                if (channel != null) {
                    val row = arrayOf(
                        channel.id.hashCode().toLong(),
                        channel.name,
                        channel.number.toString(),
                        "livetv_input",
                        "SERVICE_TYPE_AUDIO_VIDEO",
                        "SERVICE_TYPE_AUDIO_VIDEO",
                        1, 1, 0, 0, 0
                    )
                    cursor.addRow(row)
                }
            }
            
            cursor
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella query del canale per ID", e)
            null
        }
    }
    
    private fun queryChannelByNumber(channelNumber: String?, projection: Array<out String>?): Cursor? {
        if (channelNumber == null) return null
        
        return try {
            val columns = projection ?: CHANNEL_COLUMNS
            val cursor = MatrixCursor(columns)
            
            runBlocking {
                val channels = channelRepository.getAllChannels().first()
                val channel = channels.find { it.number.toString() == channelNumber }
                
                if (channel != null) {
                    val row = arrayOf(
                        channel.id.hashCode().toLong(),
                        channel.name,
                        channel.number.toString(),
                        "livetv_input",
                        "SERVICE_TYPE_AUDIO_VIDEO",
                        "SERVICE_TYPE_AUDIO_VIDEO",
                        1, 1, 0, 0, 0
                    )
                    cursor.addRow(row)
                }
            }
            
            cursor
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella query del canale per numero", e)
            null
        }
    }
    
    private fun queryPrograms(
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Per ora restituiamo un cursor vuoto
        // I programmi verranno gestiti dal sistema EPG
        val columns = projection ?: PROGRAM_COLUMNS
        return MatrixCursor(columns)
    }
    
    private fun queryProgramById(programId: String?, projection: Array<out String>?): Cursor? {
        // Per ora restituiamo null
        return null
    }
    
    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CHANNELS -> "vnd.android.cursor.dir/channel"
            CHANNEL_ID -> "vnd.android.cursor.item/channel"
            PROGRAMS -> "vnd.android.cursor.dir/program"
            PROGRAM_ID -> "vnd.android.cursor.item/program"
            else -> null
        }
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Non supportiamo inserimenti
        return null
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Non supportiamo cancellazioni
        return 0
    }
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // Non supportiamo aggiornamenti
        return 0
    }
}
