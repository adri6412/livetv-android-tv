package com.livetv.androidtv.data.dao

import androidx.room.*
import com.livetv.androidtv.data.entity.Program
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId ORDER BY startTime ASC")
    fun getProgramsForChannel(channelId: String): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime >= :startTime ORDER BY startTime ASC LIMIT :limit")
    fun getProgramsForChannelFromTime(channelId: String, startTime: Long, limit: Int = 10): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    fun getCurrentProgram(channelId: String, currentTime: Long): Flow<Program?>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    fun getNextProgram(channelId: String, currentTime: Long): Flow<Program?>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime >= :startTime AND startTime < :endTime ORDER BY startTime ASC")
    fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE startTime >= :startTime AND startTime < :endTime ORDER BY startTime ASC")
    fun getAllProgramsInTimeRange(startTime: Long, endTime: Long): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE title LIKE '%' || :searchQuery || '%' OR description LIKE '%' || :searchQuery || '%' ORDER BY startTime ASC")
    fun searchPrograms(searchQuery: String): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE category = :category ORDER BY startTime ASC")
    fun getProgramsByCategory(category: String): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE isLive = 1 ORDER BY startTime ASC")
    fun getLivePrograms(): Flow<List<Program>>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId ORDER BY startTime ASC")
    suspend fun getProgramsForChannelSync(channelId: String): List<Program>
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    suspend fun getCurrentProgramSync(channelId: String, currentTime: Long): Program?
    
    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgramSync(channelId: String, currentTime: Long): Program?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: Program)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<Program>)
    
    @Update
    suspend fun updateProgram(program: Program)
    
    @Delete
    suspend fun deleteProgram(program: Program)
    
    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: String)
    
    @Query("DELETE FROM programs WHERE startTime < :cutoffTime")
    suspend fun deleteOldPrograms(cutoffTime: Long)
    
    @Query("DELETE FROM programs")
    suspend fun deleteAllPrograms()
    
    @Query("SELECT COUNT(*) FROM programs WHERE channelId = :channelId")
    suspend fun getProgramCountForChannel(channelId: String): Int
    
    @Query("SELECT COUNT(*) FROM programs")
    suspend fun getTotalProgramCount(): Int
    
    @Query("SELECT DISTINCT category FROM programs WHERE category IS NOT NULL AND category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
    
    @Query("SELECT DISTINCT channelId FROM programs")
    fun getAllChannelIds(): Flow<List<String>>
}
