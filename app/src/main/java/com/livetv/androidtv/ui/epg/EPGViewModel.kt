package com.livetv.androidtv.ui.epg

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetv.androidtv.data.LiveTVDatabase
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.data.repository.ChannelRepository
import com.livetv.androidtv.data.repository.EPGRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EPGViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = LiveTVDatabase.getDatabase(application)
    private val channelRepository = ChannelRepository(database.channelDao())
    private val epgRepository = EPGRepository(database.epgDao())
    
    private val _epgData = MutableLiveData<List<EPGProgram>>()
    val epgData: LiveData<List<EPGProgram>> = _epgData
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _currentChannel = MutableLiveData<Channel?>()
    val currentChannel: LiveData<Channel?> = _currentChannel
    
    private var currentChannelIndex = 0
    private var channels = listOf<Channel>()
    
    init {
        // Carica i canali in background
        viewModelScope.launch {
            loadChannels()
        }
    }
    
    fun loadEPGData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Carica i canali se non sono già caricati
                if (channels.isEmpty()) {
                    loadChannels()
                }
                
                // Carica tutti i programmi EPG disponibili
                loadAllEPGPrograms()
                
            } catch (e: Exception) {
                _error.value = "Errore nel caricamento EPG: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshEPG() {
        loadEPGData()
    }
    
    fun navigateChannel(direction: Int) {
        if (channels.isEmpty()) return
        
        currentChannelIndex = (currentChannelIndex + direction).let { newIndex ->
            when {
                newIndex < 0 -> channels.size - 1
                newIndex >= channels.size -> 0
                else -> newIndex
            }
        }
        
        _currentChannel.value = channels[currentChannelIndex]
        
        viewModelScope.launch {
            loadProgramsForCurrentChannel()
        }
    }
    
    private suspend fun loadChannels() {
        try {
            channels = withContext(Dispatchers.IO) {
                channelRepository.getAllChannels().first()
            }
            
            if (channels.isNotEmpty()) {
                currentChannelIndex = 0
                _currentChannel.value = channels[0]
            }
            
        } catch (e: Exception) {
            _error.value = "Errore nel caricamento canali: ${e.message}"
        }
    }
    
    private suspend fun loadProgramsForCurrentChannel() {
        try {
            val channel = _currentChannel.value ?: return
            
            // Usa epgId se disponibile, altrimenti id
            val channelIdentifier = channel.epgId ?: channel.id
            
            val programs = withContext(Dispatchers.IO) {
                epgRepository.getProgramsForChannel(channelIdentifier).first()
            }
            
            // Ordina i programmi per orario di inizio
            val sortedPrograms = programs.sortedBy { it.startTime }
            
            _epgData.value = sortedPrograms
            
            // Log per debug
            android.util.Log.d("EPGViewModel", "Caricati ${sortedPrograms.size} programmi per canale ${channel.name} (ID: $channelIdentifier)")
            
        } catch (e: Exception) {
            _error.value = "Errore nel caricamento programmi: ${e.message}"
            android.util.Log.e("EPGViewModel", "Errore nel caricamento programmi", e)
        }
    }
    
    private suspend fun loadAllEPGPrograms() {
        try {
            val allPrograms = withContext(Dispatchers.IO) {
                epgRepository.getAllPrograms()
            }
            
            // Ordina i programmi per canale e orario di inizio
            val sortedPrograms = allPrograms.sortedWith(
                compareBy<EPGProgram> { it.channelId }
                    .thenBy { it.startTime }
            )
            
            _epgData.value = sortedPrograms
            
            // Log per debug
            android.util.Log.d("EPGViewModel", "Caricati ${sortedPrograms.size} programmi EPG totali")
            
        } catch (e: Exception) {
            _error.value = "Errore nel caricamento programmi EPG: ${e.message}"
            android.util.Log.e("EPGViewModel", "Errore nel caricamento programmi EPG", e)
        }
    }
}
