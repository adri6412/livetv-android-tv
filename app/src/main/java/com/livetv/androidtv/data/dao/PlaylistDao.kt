package com.livetv.androidtv.data.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.entity.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsLiveData(): LiveData<List<Playlist>>
    
    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlaylist(): Playlist?
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): Playlist?
    
    @Query("SELECT * FROM playlists WHERE url = :url LIMIT 1")
    suspend fun getPlaylistByUrl(url: String): Playlist?
    
    @Query("SELECT * FROM playlists WHERE filePath = :filePath LIMIT 1")
    suspend fun getPlaylistByFilePath(filePath: String): Playlist?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)
    
    @Update
    suspend fun updatePlaylist(playlist: Playlist)
    
    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAllPlaylists()
    
    @Query("UPDATE playlists SET isActive = 1 WHERE id = :playlistId")
    suspend fun activatePlaylist(playlistId: String)
    
    @Query("UPDATE playlists SET channelCount = :count, lastUpdated = :timestamp WHERE id = :playlistId")
    suspend fun updatePlaylistStats(playlistId: String, count: Int, timestamp: Long)
    
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)
    
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
    
    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int
    
    @Query("SELECT * FROM playlists WHERE autoUpdate = 1 AND (lastUpdated + updateInterval) < :currentTime")
    suspend fun getPlaylistsNeedingUpdate(currentTime: Long = System.currentTimeMillis()): List<Playlist>
}