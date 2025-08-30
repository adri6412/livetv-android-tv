package com.livetv.androidtv.data.repository

import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.dao.PlaylistDao
import com.livetv.androidtv.data.entity.Playlist
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val playlistDao: PlaylistDao
) {
    
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    
    fun getAllPlaylistsLiveData(): LiveData<List<Playlist>> = playlistDao.getAllPlaylistsLiveData()
    
    suspend fun getActivePlaylist(): Playlist? = playlistDao.getActivePlaylist()
    
    suspend fun getPlaylistById(playlistId: String): Playlist? = playlistDao.getPlaylistById(playlistId)
    
    suspend fun getPlaylistByUrl(url: String): Playlist? = playlistDao.getPlaylistByUrl(url)
    
    suspend fun getPlaylistByFilePath(filePath: String): Playlist? = playlistDao.getPlaylistByFilePath(filePath)
    
    suspend fun insertPlaylist(playlist: Playlist) = playlistDao.insertPlaylist(playlist)
    
    suspend fun insertPlaylists(playlists: List<Playlist>) = playlistDao.insertPlaylists(playlists)
    
    suspend fun updatePlaylist(playlist: Playlist) = playlistDao.updatePlaylist(playlist)
    
    suspend fun activatePlaylist(playlistId: String) {
        playlistDao.deactivateAllPlaylists()
        playlistDao.activatePlaylist(playlistId)
    }
    
    suspend fun updatePlaylistStats(playlistId: String, count: Int, timestamp: Long = System.currentTimeMillis()) = 
        playlistDao.updatePlaylistStats(playlistId, count, timestamp)
    
    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.deletePlaylist(playlist)
    
    suspend fun deletePlaylistById(playlistId: String) = playlistDao.deletePlaylistById(playlistId)
    
    suspend fun deleteAllPlaylists() = playlistDao.deleteAllPlaylists()
    
    suspend fun getPlaylistCount(): Int = playlistDao.getPlaylistCount()
    
    suspend fun getPlaylistsNeedingUpdate(): List<Playlist> = playlistDao.getPlaylistsNeedingUpdate()
}