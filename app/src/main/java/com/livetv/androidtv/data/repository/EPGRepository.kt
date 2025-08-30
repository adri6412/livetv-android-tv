package com.livetv.androidtv.data.repository

import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.dao.EPGDao
import com.livetv.androidtv.data.entity.EPGProgram
import kotlinx.coroutines.flow.Flow

class EPGRepository(
    private val epgDao: EPGDao
) {
    
    fun getProgramsForChannel(channelId: String): Flow<List<EPGProgram>> = 
        epgDao.getProgramsForChannel(channelId)
    
    suspend fun getCurrentProgram(channelId: String, currentTime: Long = System.currentTimeMillis()): EPGProgram? = 
        epgDao.getCurrentProgram(channelId, currentTime)
    
    suspend fun getNextProgram(channelId: String, currentTime: Long = System.currentTimeMillis()): EPGProgram? = 
        epgDao.getNextProgram(channelId, currentTime)
    
    suspend fun getAllPrograms(): List<EPGProgram> = 
        epgDao.getAllPrograms()
    
    suspend fun getProgramCount(): Int = 
        epgDao.getProgramCount()
    
    fun getProgramsInTimeRange(startTime: Long, endTime: Long): Flow<List<EPGProgram>> = 
        epgDao.getProgramsInTimeRange(startTime, endTime)
    
    fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): Flow<List<EPGProgram>> = 
        epgDao.getProgramsForChannelInTimeRange(channelId, startTime, endTime)
    
    fun searchPrograms(query: String): Flow<List<EPGProgram>> = epgDao.searchPrograms(query)
    
    fun getProgramsByCategory(category: String): Flow<List<EPGProgram>> = epgDao.getProgramsByCategory(category)
    
    fun getAllCategories(): Flow<List<String>> = epgDao.getAllCategories()
    
    fun getHbbTVPrograms(): Flow<List<EPGProgram>> = epgDao.getHbbTVPrograms()
    
    fun getCurrentLivePrograms(): Flow<List<EPGProgram>> = epgDao.getCurrentLivePrograms()
    
    suspend fun insertProgram(program: EPGProgram) = epgDao.insertProgram(program)
    
    suspend fun insertPrograms(programs: List<EPGProgram>) = epgDao.insertPrograms(programs)
    
    suspend fun updateProgram(program: EPGProgram) = epgDao.updateProgram(program)
    
    suspend fun deleteProgram(program: EPGProgram) = epgDao.deleteProgram(program)
    
    suspend fun deleteProgramsForChannel(channelId: String) = epgDao.deleteProgramsForChannel(channelId)
    
    suspend fun deleteOldPrograms(cutoffTime: Long = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)) = 
        epgDao.deleteOldPrograms(cutoffTime)
    
    suspend fun deleteAllPrograms() = epgDao.deleteAllPrograms()
    
    suspend fun getProgramCountForChannel(channelId: String): Int = epgDao.getProgramCountForChannel(channelId)
    
    suspend fun getChannelsWithProgramsInTimeRange(startTime: Long, endTime: Long): List<String> = 
        epgDao.getChannelsWithProgramsInTimeRange(startTime, endTime)
    
    suspend fun getProgramsForChannelsInTimeRange(channelIds: List<String>, startTime: Long, endTime: Long): List<EPGProgram> = 
        epgDao.getProgramsForChannelsInTimeRange(channelIds, startTime, endTime)
}