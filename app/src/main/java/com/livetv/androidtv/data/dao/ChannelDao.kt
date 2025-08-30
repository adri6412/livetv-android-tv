package com.livetv.androidtv.data.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.entity.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    
    @Query("SELECT * FROM channels WHERE isActive = 1 ORDER BY number ASC, name ASC")
    fun getAllChannels(): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE isActive = 1 ORDER BY number ASC, name ASC")
    fun getAllChannelsLiveData(): LiveData<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND isActive = 1 ORDER BY number ASC, name ASC")
    fun getFavoriteChannels(): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE `group` = :group AND isActive = 1 ORDER BY number ASC, name ASC")
    fun getChannelsByGroup(group: String): Flow<List<Channel>>
    
    @Query("SELECT DISTINCT `group` FROM channels WHERE `group` IS NOT NULL AND isActive = 1 ORDER BY `group` ASC")
    fun getAllGroups(): Flow<List<String>>
    
    @Query("SELECT * FROM channels WHERE id = :channelId")
    suspend fun getChannelById(channelId: String): Channel?
    
    @Query("SELECT * FROM channels WHERE number = :number AND isActive = 1")
    suspend fun getChannelByNumber(number: Int): Channel?
    
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND isActive = 1 ORDER BY number ASC, name ASC")
    fun searchChannels(query: String): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE hbbtvUrl IS NOT NULL AND hbbtvUrl != '' AND isActive = 1 ORDER BY number ASC, name ASC")
    fun getHbbTVChannels(): Flow<List<Channel>>
    
    @Query("SELECT COUNT(*) FROM channels WHERE isActive = 1")
    suspend fun getChannelCount(): Int
    
    @Query("SELECT COUNT(*) FROM channels WHERE isFavorite = 1 AND isActive = 1")
    suspend fun getFavoriteChannelCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: Channel)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)
    
    @Update
    suspend fun updateChannel(channel: Channel)
    
    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: String, isFavorite: Boolean)
    
    @Query("UPDATE channels SET isActive = 0")
    suspend fun deactivateAllChannels()
    
    @Delete
    suspend fun deleteChannel(channel: Channel)
    
    @Query("DELETE FROM channels WHERE id = :channelId")
    suspend fun deleteChannelById(channelId: String)
    
    @Query("DELETE FROM channels WHERE isActive = 0")
    suspend fun deleteInactiveChannels()
    
    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()
    
    // Metodi per la navigazione tra canali
    @Query("""
        SELECT * FROM channels 
        WHERE number > :currentNumber AND isActive = 1 
        ORDER BY number ASC 
        LIMIT 1
    """)
    suspend fun getNextChannel(currentNumber: Int): Channel?
    
    @Query("""
        SELECT * FROM channels 
        WHERE number < :currentNumber AND isActive = 1 
        ORDER BY number DESC 
        LIMIT 1
    """)
    suspend fun getPreviousChannel(currentNumber: Int): Channel?
    
    @Query("""
        SELECT * FROM channels 
        WHERE isActive = 1 
        ORDER BY number ASC 
        LIMIT 1
    """)
    suspend fun getFirstChannel(): Channel?
    
    @Query("""
        SELECT * FROM channels 
        WHERE isActive = 1 
        ORDER BY number DESC 
        LIMIT 1
    """)
    suspend fun getLastChannel(): Channel?
    
    // Statistiche
    @Query("""
        SELECT `group`, COUNT(*) as count 
        FROM channels 
        WHERE `group` IS NOT NULL AND isActive = 1 
        GROUP BY `group` 
        ORDER BY count DESC
    """)
    suspend fun getChannelCountByGroup(): List<GroupCount>
    
    // Metodo sincrono per ottenere tutti i canali (per il popup)
    @Query("SELECT * FROM channels WHERE isActive = 1 ORDER BY number ASC, name ASC")
    suspend fun getAllChannelsSync(): List<Channel>
}

data class GroupCount(val group: String, val count: Int)