package com.livetv.androidtv.data.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.entity.EPGProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EPGDao {
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(channelId: String, currentTime: Long = System.currentTimeMillis()): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, currentTime: Long = System.currentTimeMillis()): EPGProgram?
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(channelId: String, currentTime: Long = System.currentTimeMillis()): EPGProgram?
    
    @Query("SELECT * FROM epg_programs WHERE startTime >= :startTime AND startTime < :endTime ORDER BY startTime ASC")
    fun getProgramsInTimeRange(startTime: Long, endTime: Long): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime >= :startTime AND startTime < :endTime ORDER BY startTime ASC")
    fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY startTime ASC")
    fun searchPrograms(query: String): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE category = :category ORDER BY startTime ASC")
    fun getProgramsByCategory(category: String): Flow<List<EPGProgram>>
    
    @Query("SELECT DISTINCT category FROM epg_programs WHERE category IS NOT NULL ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
    
    @Query("SELECT * FROM epg_programs WHERE hbbtvUrl IS NOT NULL AND hbbtvUrl != '' ORDER BY startTime ASC")
    fun getHbbTVPrograms(): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE isLive = 1 AND startTime <= :currentTime AND endTime > :currentTime ORDER BY startTime ASC")
    fun getCurrentLivePrograms(currentTime: Long = System.currentTimeMillis()): Flow<List<EPGProgram>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: EPGProgram)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EPGProgram>)
    
    @Update
    suspend fun updateProgram(program: EPGProgram)
    
    @Delete
    suspend fun deleteProgram(program: EPGProgram)
    
    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: String)
    
    @Query("DELETE FROM epg_programs WHERE endTime < :cutoffTime")
    suspend fun deleteOldPrograms(cutoffTime: Long)
    
    @Query("DELETE FROM epg_programs")
    suspend fun deleteAllPrograms()
    
    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int
    
    @Query("SELECT COUNT(*) FROM epg_programs WHERE channelId = :channelId")
    suspend fun getProgramCountForChannel(channelId: String): Int
    
    @Query("SELECT * FROM epg_programs ORDER BY channelId, startTime ASC")
    suspend fun getAllPrograms(): List<EPGProgram>
    
    // Metodi per EPG grid
    @Query("""
        SELECT DISTINCT channelId 
        FROM epg_programs 
        WHERE startTime >= :startTime AND startTime < :endTime 
        ORDER BY channelId
    """)
    suspend fun getChannelsWithProgramsInTimeRange(startTime: Long, endTime: Long): List<String>
    
    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelId IN (:channelIds) AND startTime >= :startTime AND startTime < :endTime 
        ORDER BY channelId, startTime ASC
    """)
    suspend fun getProgramsForChannelsInTimeRange(
        channelIds: List<String>, 
        startTime: Long, 
        endTime: Long
    ): List<EPGProgram>
    

}