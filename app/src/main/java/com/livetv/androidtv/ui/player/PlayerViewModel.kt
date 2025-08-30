package com.livetv.androidtv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetv.androidtv.data.LiveTVDatabase
import android.util.Log
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.Program
import com.livetv.androidtv.data.repository.ChannelRepository
import com.livetv.androidtv.data.repository.ProgramRepository
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = LiveTVDatabase.getDatabase(application)
    private val channelRepository = ChannelRepository(database.channelDao())
    private val programRepository = ProgramRepository(database.programDao())
    
    private val _currentProgram = MutableLiveData<Program?>()
    val currentProgram: LiveData<Program?> = _currentProgram
    
    private val _nextProgram = MutableLiveData<Program?>()
    val nextProgram: LiveData<Program?> = _nextProgram
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    fun loadChannelPrograms(channelId: String) {
        viewModelScope.launch {
            try {
                Log.d("PlayerViewModel", "Caricamento programmi per canale ID: $channelId")
                
                // Carica programma corrente
                val current = programRepository.getCurrentProgramSync(channelId)
                Log.d("PlayerViewModel", "Programma corrente trovato: ${current?.title ?: "null"}")
                _currentProgram.value = current
                
                // Carica prossimo programma
                val next = programRepository.getNextProgramSync(channelId)
                Log.d("PlayerViewModel", "Prossimo programma trovato: ${next?.title ?: "null"}")
                _nextProgram.value = next
                
                _error.value = null
                
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Errore nel caricamento EPG per canale $channelId", e)
                _error.value = "Errore nel caricamento EPG: ${e.message}"
            }
        }
    }
    
    fun getAdjacentChannel(currentChannel: Channel, direction: Int, callback: (Channel?) -> Unit) {
        viewModelScope.launch {
            try {
                val adjacentChannel = if (direction > 0) {
                    channelRepository.getNextChannel(currentChannel.number)
                        ?: channelRepository.getFirstChannel()
                } else {
                    channelRepository.getPreviousChannel(currentChannel.number)
                        ?: channelRepository.getLastChannel()
                }
                
                callback(adjacentChannel)
                
            } catch (e: Exception) {
                _error.value = "Errore nel cambio canale: ${e.message}"
                callback(null)
            }
        }
    }
    
    fun getChannelByNumber(channelNumber: Int, callback: (Channel?) -> Unit) {
        viewModelScope.launch {
            try {
                val channel = channelRepository.getChannelByNumber(channelNumber)
                callback(channel)
                
            } catch (e: Exception) {
                _error.value = "Errore nella ricerca canale: ${e.message}"
                callback(null)
            }
        }
    }
    
    // Metodi per supportare il popup del canale
    suspend fun getCurrentProgram(channel: Channel): Program? {
        return try {
            val channelId = channel.epgId ?: channel.id
            Log.d("PlayerViewModel", "Ricerca programma per canale: ${channel.name}, ID: $channelId, EPG ID: ${channel.epgId}")
            val program = programRepository.getCurrentProgramSync(channelId)
            Log.d("PlayerViewModel", "Programma trovato: ${program?.title ?: "null"}")
            program
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Errore nel recupero programma per canale ${channel.name}", e)
            null
        }
    }
    
    suspend fun getChannelIndex(channel: Channel): Int {
        return try {
            val allChannels = channelRepository.getAllChannelsSync()
            allChannels.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    suspend fun getTotalChannels(): Int {
        return try {
            channelRepository.getAllChannelsSync().size
        } catch (e: Exception) {
            0
        }
    }
}