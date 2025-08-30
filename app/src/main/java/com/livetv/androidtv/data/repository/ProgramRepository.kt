package com.livetv.androidtv.data.repository

import com.livetv.androidtv.data.dao.ProgramDao
import com.livetv.androidtv.data.entity.Program
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProgramRepository(private val programDao: ProgramDao) {
    
    /**
     * Ottiene tutti i programmi per un canale specifico
     */
    fun getProgramsForChannel(channelId: String): Flow<List<Program>> {
        return programDao.getProgramsForChannel(channelId)
    }
    
    /**
     * Ottiene tutti i programmi per un canale specifico (sincrono)
     */
    suspend fun getProgramsForChannelSync(channelId: String): List<Program> {
        return withContext(Dispatchers.IO) {
            programDao.getProgramsForChannelSync(channelId)
        }
    }
    
    /**
     * Ottiene i programmi per un canale da un determinato orario
     */
    fun getProgramsForChannelFromTime(channelId: String, startTime: Long, limit: Int = 10): Flow<List<Program>> {
        return programDao.getProgramsForChannelFromTime(channelId, startTime, limit)
    }
    
    /**
     * Ottiene il programma corrente per un canale
     */
    fun getCurrentProgram(channelId: String): Flow<Program?> {
        val currentTime = System.currentTimeMillis()
        return programDao.getCurrentProgram(channelId, currentTime)
    }
    
    /**
     * Ottiene il prossimo programma per un canale
     */
    fun getNextProgram(channelId: String): Flow<Program?> {
        val currentTime = System.currentTimeMillis()
        return programDao.getNextProgram(channelId, currentTime)
    }
    
    /**
     * Ottiene i programmi per un canale in un intervallo di tempo
     */
    fun getProgramsForChannelInTimeRange(channelId: String, startTime: Long, endTime: Long): Flow<List<Program>> {
        return programDao.getProgramsForChannelInTimeRange(channelId, startTime, endTime)
    }
    
    /**
     * Ottiene tutti i programmi in un intervallo di tempo
     */
    fun getAllProgramsInTimeRange(startTime: Long, endTime: Long): Flow<List<Program>> {
        return programDao.getAllProgramsInTimeRange(startTime, endTime)
    }
    
    /**
     * Cerca programmi per titolo o descrizione
     */
    fun searchPrograms(searchQuery: String): Flow<List<Program>> {
        return programDao.searchPrograms(searchQuery)
    }
    
    /**
     * Ottiene programmi per categoria
     */
    fun getProgramsByCategory(category: String): Flow<List<Program>> {
        return programDao.getProgramsByCategory(category)
    }
    
    /**
     * Ottiene tutti i programmi live
     */
    fun getLivePrograms(): Flow<List<Program>> {
        return programDao.getLivePrograms()
    }
    
    /**
     * Ottiene il programma corrente per un canale (sincrono)
     */
    suspend fun getCurrentProgramSync(channelId: String): Program? {
        return withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            programDao.getCurrentProgramSync(channelId, currentTime)
        }
    }
    
    /**
     * Ottiene il prossimo programma per un canale (sincrono)
     */
    suspend fun getNextProgramSync(channelId: String): Program? {
        return withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            programDao.getNextProgramSync(channelId, currentTime)
        }
    }
    
    /**
     * Inserisce un singolo programma
     */
    suspend fun insertProgram(program: Program) {
        withContext(Dispatchers.IO) {
            programDao.insertProgram(program)
        }
    }
    
    /**
     * Inserisce una lista di programmi
     */
    suspend fun insertPrograms(programs: List<Program>) {
        withContext(Dispatchers.IO) {
            programDao.insertPrograms(programs)
        }
    }
    
    /**
     * Aggiorna un programma
     */
    suspend fun updateProgram(program: Program) {
        withContext(Dispatchers.IO) {
            programDao.updateProgram(program)
        }
    }
    
    /**
     * Elimina un programma
     */
    suspend fun deleteProgram(program: Program) {
        withContext(Dispatchers.IO) {
            programDao.deleteProgram(program)
        }
    }
    
    /**
     * Elimina tutti i programmi per un canale
     */
    suspend fun deleteProgramsForChannel(channelId: String) {
        withContext(Dispatchers.IO) {
            programDao.deleteProgramsForChannel(channelId)
        }
    }
    
    /**
     * Elimina i programmi vecchi
     */
    suspend fun deleteOldPrograms(cutoffTime: Long) {
        withContext(Dispatchers.IO) {
            programDao.deleteOldPrograms(cutoffTime)
        }
    }
    
    /**
     * Elimina tutti i programmi
     */
    suspend fun deleteAllPrograms() {
        withContext(Dispatchers.IO) {
            programDao.deleteAllPrograms()
        }
    }
    
    /**
     * Ottiene il conteggio dei programmi per un canale
     */
    suspend fun getProgramCountForChannel(channelId: String): Int {
        return withContext(Dispatchers.IO) {
            programDao.getProgramCountForChannel(channelId)
        }
    }
    
    /**
     * Ottiene il conteggio totale dei programmi
     */
    suspend fun getTotalProgramCount(): Int {
        return withContext(Dispatchers.IO) {
            programDao.getTotalProgramCount()
        }
    }
    
    /**
     * Ottiene tutte le categorie disponibili
     */
    fun getAllCategories(): Flow<List<String>> {
        return programDao.getAllCategories()
    }
    
    /**
     * Ottiene tutti gli ID dei canali che hanno programmi
     */
    fun getAllChannelIds(): Flow<List<String>> {
        return programDao.getAllChannelIds()
    }
}
