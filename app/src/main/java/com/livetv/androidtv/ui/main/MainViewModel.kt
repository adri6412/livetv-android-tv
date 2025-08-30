package com.livetv.androidtv.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetv.androidtv.data.LiveTVDatabase
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.ui.main.ChannelWithProgram
import com.livetv.androidtv.data.repository.ChannelRepository
import com.livetv.androidtv.data.repository.EPGRepository
import com.livetv.androidtv.data.repository.PlaylistRepository
import com.livetv.androidtv.utils.M3UParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import android.net.TrafficStats
import java.net.URL
import com.livetv.androidtv.utils.PreferencesManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = LiveTVDatabase.getDatabase(application)
    private val channelRepository = ChannelRepository(database.channelDao())
    private val epgRepository = EPGRepository(database.epgDao())
    private val playlistRepository = PlaylistRepository(database.playlistDao())
    private val preferencesManager = PreferencesManager(application)
    
    private val _channels = MutableLiveData<List<Channel>>()
    val channels: LiveData<List<Channel>> = _channels
    
    private val _currentChannel = MutableLiveData<Channel?>()
    val currentChannel: LiveData<Channel?> = _currentChannel
    
    private val _currentProgram = MutableLiveData<EPGProgram?>()
    val currentProgram: LiveData<EPGProgram?> = _currentProgram
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    
    // LiveData per il popup del canale
    private val _showChannelPopup = MutableLiveData<ChannelWithProgram?>()
    val showChannelPopup: LiveData<ChannelWithProgram?> = _showChannelPopup
    
    private var allChannels = listOf<Channel>()
    private var _allPrograms = MutableLiveData<List<EPGProgram>>()
    val allPrograms: LiveData<List<EPGProgram>> = _allPrograms
    private var showFavoritesOnly = false
    private var currentChannelIndex = 0
    
    // LiveData per notificare quando scrollare alla posizione
    private val _scrollToPosition = MutableLiveData<Int>()
    val scrollToPosition: LiveData<Int> = _scrollToPosition
    
    // Receiver per i messaggi EPG
    private val epgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.livetv.androidtv.EPG_LOADED" -> {
                    val programsCount = intent.getIntExtra("programs_count", 0)
                    // Mostra messaggio di successo
                    // Log per debug (senza mostrare messaggi a schermo)
                    
                    // Ricarica i programmi EPG e aggiorna la UI
                    viewModelScope.launch {
                        try {
                            val programs = epgRepository.getAllPrograms()
                            _allPrograms.value = programs
                            // EPG aggiornato
                            
                            // Aggiorna la UI con i nuovi programmi
                            if (allChannels.isNotEmpty()) {
                                updateDisplayedChannels()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "Errore nel ricaricamento programmi EPG", e)
                        }
                    }
                }
                "com.livetv.androidtv.EPG_ERROR" -> {
                    val errorMessage = intent.getStringExtra("error_message") ?: "Errore sconosciuto"
                    _message.value = "Errore EPG: $errorMessage"
                }
                "com.livetv.androidtv.EPG_STATUS" -> {
                    val statusMessage = intent.getStringExtra("status") ?: ""
                    // Mostra messaggio di status per debug
                    _message.value = "Status EPG: $statusMessage"
                }
            }
        }
    }
    
    init {
        android.util.Log.d("MainViewModel", "Inizializzazione MainViewModel...")
        observeChannels()
        observeEPGPrograms()
        registerEPGReceiver()
        loadChannelsOnStartup()
        loadEPGOnStartup()
        
        // Controllo immediato dello stato EPG
        viewModelScope.launch {
            delay(5000) // Aspetta 5 secondi
            checkEPGStatus()
        }
    }
    
    private fun registerEPGReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.livetv.androidtv.EPG_LOADED")
                addAction("com.livetv.androidtv.EPG_ERROR")
                addAction("com.livetv.androidtv.EPG_STATUS")
            }
            getApplication<Application>().registerReceiver(epgReceiver, filter)
        } catch (e: Exception) {
            // Ignora errori di registrazione
        }
    }
    
    private fun unregisterEPGReceiver() {
        try {
            getApplication<Application>().unregisterReceiver(epgReceiver)
        } catch (e: Exception) {
            // Ignora errori di deregistrazione
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        unregisterEPGReceiver()
    }
    
    private fun observeChannels() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "Inizio osservazione canali...")
                channelRepository.getAllChannels().collect { channelList ->
                    android.util.Log.d("MainViewModel", "Canali ricevuti dal repository: ${channelList.size}")
                    allChannels = channelList
                    
                    if (channelList.isNotEmpty()) {
                        android.util.Log.d("MainViewModel", "Primi 3 canali: ${channelList.take(3).map { "${it.number}: ${it.name}" }}")
                    }
                    
                    updateDisplayedChannels()
                    
                    // Ripristina l'ultimo canale o seleziona il primo
                    if (_currentChannel.value == null && channelList.isNotEmpty()) {
                        restoreLastChannelOrSelectDefault(channelList)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nell'osservazione canali", e)
            }
        }
    }
    
    private fun observeEPGPrograms() {
        viewModelScope.launch {
            try {
                // Carica tutti i programmi EPG dal database
                val programs = epgRepository.getAllPrograms()
                _allPrograms.value = programs
                
                // Aggiorna la lista per mostrare i programmi solo se ci sono canali
                if (allChannels.isNotEmpty()) {
                    updateDisplayedChannels()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel caricamento programmi EPG", e)
                // Ignora errori, i programmi potrebbero non essere ancora caricati
            }
        }
    }

    private fun restoreLastChannelOrSelectDefault(channelList: List<Channel>) {
        val lastChannelId = preferencesManager.getLastChannelId()

        if (lastChannelId != -1L) {
            // Cerca l'ultimo canale visualizzato
            val lastChannel = channelList.find { it.id == lastChannelId.toString() }
            if (lastChannel != null) {
                val index = channelList.indexOf(lastChannel)
                _currentChannel.value = lastChannel
                currentChannelIndex = index
                _scrollToPosition.value = index
                return
            }
        }

        // Se è il primo avvio o l'ultimo canale non esiste più, seleziona il canale 1 (indice 0)
        if (preferencesManager.isFirstLaunch()) {
            _currentChannel.value = channelList[0]
            currentChannelIndex = 0
            _scrollToPosition.value = 0
            preferencesManager.setFirstLaunchCompleted()
        } else {
            // Seleziona il primo canale disponibile
            _currentChannel.value = channelList[0]
            currentChannelIndex = 0
            _scrollToPosition.value = 0
        }
    }
    
    fun loadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val activePlaylist = playlistRepository.getActivePlaylist()
                if (activePlaylist == null) {
                    _error.value = "Nessuna playlist configurata. Vai nelle impostazioni per aggiungerne una."
                    // IMPORTANTE: Emetti una lista vuota quando non c'è playlist
                    _channels.value = emptyList()
                    return@launch
                }
                
                // Se la playlist è remota, scarica e parsa
                if (activePlaylist.isRemote()) {
                    loadRemotePlaylist(activePlaylist.url!!)
                } else if (activePlaylist.isLocal()) {
                    loadLocalPlaylist(activePlaylist.filePath!!)
                }
                
            } catch (e: Exception) {
                _error.value = "Errore nel caricamento dei canali: ${e.message}"
                // IMPORTANTE: Emetti una lista vuota anche in caso di errore
                _channels.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun loadRemotePlaylist(url: String) {
        try {
            // Operazioni di rete su thread IO
            val content = withContext(Dispatchers.IO) {
                // Tag del traffico di rete per StrictMode
                TrafficStats.setThreadStatsTag(0x1003)
                
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    
                    // Aggiungi User-Agent per compatibilità con più server
                    connection.setRequestProperty("User-Agent", "LiveTV Android/1.0")
                    connection.setRequestProperty("Accept", "*/*")
                    
                    val inputStream = connection.getInputStream()
                    val result = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()
                    
                    result
                } finally {
                    // Rimuovi il tag del traffico
                    TrafficStats.clearThreadStatsTag()
                }
            }
            
            // Verifica che sia un file M3U valido
            if (!content.trim().startsWith("#EXTM3U")) {
                _error.value = "Il file scaricato non è una playlist M3U valida"
                return
            }
            
                            // Parsing su thread IO
                val channels = withContext(Dispatchers.IO) {
                    M3UParser.parseM3UFromString(content)
                }
                
                if (channels.isNotEmpty()) {
                    // Deattiva tutti i canali esistenti e inserisci i nuovi
                    channelRepository.deactivateAllChannels()
                    channelRepository.insertChannels(channels)
                    channelRepository.deleteInactiveChannels()
                    
                    _error.value = null
                } else {
                    _error.value = "Nessun canale trovato nella playlist"
                }
            
        } catch (e: java.net.UnknownHostException) {
            _error.value = "Host non trovato. Verifica l'URL e la connessione internet"
        } catch (e: java.net.SocketTimeoutException) {
            _error.value = "Timeout connessione. Il server non risponde"
        } catch (e: java.net.ConnectException) {
            _error.value = "Impossibile connettersi al server"
        } catch (e: java.io.FileNotFoundException) {
            _error.value = "File non trovato (404). Verifica l'URL"
        } catch (e: javax.net.ssl.SSLException) {
            _error.value = "Errore SSL: Problema con il certificato HTTPS"
        } catch (e: Exception) {
            _error.value = "Errore nel download della playlist: ${e.javaClass.simpleName} - ${e.message}"
        }
    }
    
    private suspend fun loadLocalPlaylist(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                _error.value = "File playlist non trovato: $filePath"
                return
            }
            
            val inputStream = file.inputStream()
            val channels = M3UParser.parseM3U(inputStream)
            inputStream.close()
            
            if (channels.isNotEmpty()) {
                // Deattiva tutti i canali esistenti e inserisci i nuovi
                channelRepository.deactivateAllChannels()
                channelRepository.insertChannels(channels)
                channelRepository.deleteInactiveChannels()
                
                _error.value = null
            } else {
                _error.value = "Nessun canale trovato nella playlist"
            }
            
        } catch (e: Exception) {
            _error.value = "Errore nella lettura del file playlist: ${e.message}"
        }
    }
    
    fun refreshChannels() {
        loadChannels()
        // Aggiorna anche l'EPG se disponibile
        loadEPGFromURL()
    }
    
    /**
     * Aggiorna solo i dati EPG
     */
    fun refreshEPG() {
        loadEPGFromURL()
    }
    
    fun toggleFavoritesFilter() {
        showFavoritesOnly = !showFavoritesOnly
        updateDisplayedChannels()
    }
    
    private fun updateDisplayedChannels() {
        try {
            val currentTime = System.currentTimeMillis()
            
            android.util.Log.d("MainViewModel", "updateDisplayedChannels: allChannels=${allChannels.size}, allPrograms=${_allPrograms.value?.size ?: 0}")
            
            // Verifica che i dati siano disponibili
            if (allChannels.isEmpty()) {
                android.util.Log.d("MainViewModel", "Nessun canale disponibile per l'aggiornamento")
                return
            }
            
            // Crea la lista di canali con programmi correnti
            val channelsWithPrograms = allChannels.map { channel ->
                val currentProgram = _allPrograms.value?.find { program ->
                    // I programmi sono memorizzati con epgId, cerca prima con epgId poi con id
                    val channelIdentifier = channel.epgId ?: channel.id
                    program.channelId == channelIdentifier && 
                    program.startTime <= currentTime && 
                    program.endTime > currentTime
                }
                
                // Log per debug del mapping rimosso per pulizia output
                
                ChannelWithProgram(channel, currentProgram)
            }

            // Filtra per preferiti se necessario
            val filteredChannels = if (showFavoritesOnly) {
                channelsWithPrograms.filter { it.channel.isFavorite }
            } else {
                channelsWithPrograms
            }

            android.util.Log.d("MainViewModel", "Canali con programmi creati: ${channelsWithPrograms.size}, filtrati: ${filteredChannels.size}")
            
            _channels.value = filteredChannels.map { it.channel }
            
            // Aggiorna l'indice del canale corrente in modo sicuro
            _currentChannel.value?.let { current ->
                val newIndex = filteredChannels.indexOfFirst { it.channel.id == current.id }
                if (newIndex != -1) {
                    currentChannelIndex = newIndex
                } else if (filteredChannels.isNotEmpty()) {
                    currentChannelIndex = 0
                    _currentChannel.value = filteredChannels[0].channel
                }
            }
            
            android.util.Log.d("MainViewModel", "Canali aggiornati: ${filteredChannels.size}")
            
            // Aggiorna il programma corrente per il canale selezionato
            _currentChannel.value?.let { channel ->
                updateCurrentProgram(channel.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Errore nell'aggiornamento canali", e)
        }
    }
    
    fun updateDisplayedChannelsWithPrograms() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Verifica che i dati siano disponibili
            if (allChannels.isEmpty()) {
                android.util.Log.d("MainViewModel", "Nessun canale disponibile per l'aggiornamento programmi")
                return
            }
            
            // Crea la lista di canali con programmi correnti
            val channelsWithPrograms = allChannels.map { channel ->
                val currentProgram = _allPrograms.value?.find { program ->
                    // I programmi sono memorizzati con epgId, cerca prima con epgId poi con id
                    val channelIdentifier = channel.epgId ?: channel.id
                    program.channelId == channelIdentifier && 
                    program.startTime <= currentTime && 
                    program.endTime > currentTime
                }
                ChannelWithProgram(channel, currentProgram)
            }

            // Filtra per preferiti se necessario
            val filteredChannels = if (showFavoritesOnly) {
                channelsWithPrograms.filter { it.channel.isFavorite }
            } else {
                channelsWithPrograms
            }

            // Aggiorna la lista con i programmi
            _channels.value = filteredChannels.map { it.channel }
            
            // Aggiorna l'indice del canale corrente in modo sicuro
            _currentChannel.value?.let { current ->
                val newIndex = filteredChannels.indexOfFirst { it.channel.id == current.id }
                if (newIndex != -1) {
                    currentChannelIndex = newIndex
                } else if (filteredChannels.isNotEmpty()) {
                    currentChannelIndex = 0
                    _currentChannel.value = filteredChannels[0].channel
                }
            }
            
            android.util.Log.d("MainViewModel", "Canali con programmi aggiornati: ${filteredChannels.size}")
            
            // Aggiorna il programma corrente per il canale selezionato
            _currentChannel.value?.let { channel ->
                updateCurrentProgram(channel.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Errore nell'aggiornamento canali con programmi", e)
        }
    }
    
    suspend fun selectNextChannel(showPopup: Boolean = false) {
        try {
            val currentChannels = _channels.value ?: return
            if (currentChannels.isEmpty()) return
            
            // Verifica che l'indice sia valido
            if (currentChannelIndex < 0 || currentChannelIndex >= currentChannels.size) {
                currentChannelIndex = 0
            }
            
            currentChannelIndex = (currentChannelIndex + 1) % currentChannels.size
            _currentChannel.value = currentChannels[currentChannelIndex]
            _scrollToPosition.value = currentChannelIndex
            
            // Aggiorna il programma corrente per il nuovo canale
            updateCurrentProgram(_currentChannel.value?.id)
            
            // Salva la posizione del canale corrente
            saveCurrentChannelPosition()
            
            // Mostra il popup del canale solo se richiesto
            if (showPopup) {
                _currentChannel.value?.let { showChannelPopup(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Errore nella selezione canale successivo", e)
        }
    }
    
    suspend fun selectPreviousChannel(showPopup: Boolean = false) {
        try {
            val currentChannels = _channels.value ?: return
            android.util.Log.d("MainViewModel", "Canali disponibili: ${currentChannels.size}")
            if (currentChannels.isEmpty()) return
            
            // Verifica che l'indice sia valido
            if (currentChannelIndex < 0 || currentChannelIndex >= currentChannels.size) {
                currentChannelIndex = 0
            }
            
            currentChannelIndex = if (currentChannelIndex > 0) {
                currentChannelIndex - 1
            } else {
                currentChannels.size - 1
            }
            _currentChannel.value = currentChannels[currentChannelIndex]
            _scrollToPosition.value = currentChannelIndex
            
            // Aggiorna il programma corrente per il nuovo canale
            updateCurrentProgram(_currentChannel.value?.id)
            
            // Salva la posizione del canale corrente
            saveCurrentChannelPosition()
            
            // Mostra il popup del canale solo se richiesto
            if (showPopup) {
                _currentChannel.value?.let { showChannelPopup(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Errore nella selezione canale precedente", e)
        }
    }
    
    fun selectChannel(channel: Channel, showPopup: Boolean = false) {
        val currentChannels = _channels.value ?: return
        val index = currentChannels.indexOfFirst { it.id == channel.id }
        if (index != -1) {
            currentChannelIndex = index
            _currentChannel.value = channel
            _scrollToPosition.value = index
            
            // Aggiorna il programma corrente per il canale selezionato
            updateCurrentProgram(channel.id)
            
            // Salva la posizione del canale corrente
            saveCurrentChannelPosition()
            
            // Mostra il popup del canale solo se richiesto (di default no, per la lista principale)
            if (showPopup) {
                showChannelPopup(channel)
            }
        }
    }
    
    private fun saveCurrentChannelPosition() {
        _currentChannel.value?.let { channel ->
            preferencesManager.saveLastChannel(channel.id.toLongOrNull() ?: -1L, currentChannelIndex)
        }
    }
    
    // Metodo per mostrare il popup del canale
    fun showChannelPopup(channel: Channel) {
        viewModelScope.launch {
            try {
                // Trova il programma corrente per il canale
                val currentTime = System.currentTimeMillis()
                val channelIdentifier = channel.epgId ?: channel.id
                val currentProgram = _allPrograms.value?.find { program ->
                    program.channelId == channelIdentifier && 
                    program.startTime <= currentTime && 
                    program.endTime > currentTime
                }
                
                val channelWithProgram = ChannelWithProgram(channel, currentProgram)
                _showChannelPopup.value = channelWithProgram
                
                // Nascondi il popup dopo 3 secondi
                delay(3000)
                _showChannelPopup.value = null
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel mostrare popup canale", e)
            }
        }
    }
    
    private fun updateCurrentProgram(channelId: String?) {
        if (channelId == null) return
        
        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Trova il canale per ottenere l'epgId
                val channel = allChannels.find { it.id == channelId }
                val channelIdentifier = channel?.epgId ?: channelId
                
                val program = epgRepository.getCurrentProgram(channelIdentifier, currentTime)
                _currentProgram.value = program
                // Programma corrente recuperato
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel recupero programma corrente", e)
                _currentProgram.value = null
            }
        }
    }
    
    private fun loadChannelsOnStartup() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "Avvio caricamento automatico canali...")
                
                // Aspetta un momento per assicurarsi che tutto sia inizializzato
                delay(1000)
                
                // Controlla se ci sono canali nel database
                val channelCount = allChannels.size
                android.util.Log.d("MainViewModel", "Controllo canali: trovati $channelCount canali in memoria")
                
                if (channelCount == 0) {
                    android.util.Log.d("MainViewModel", "Nessun canale in memoria, carico canali automaticamente")
                    // Carica i canali automaticamente se non ci sono
                    loadChannels()
                } else {
                    android.util.Log.d("MainViewModel", "Canali già presenti in memoria: $channelCount canali")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel controllo canali all'avvio", e)
            }
        }
    }
    
    private fun loadEPGOnStartup() {
        viewModelScope.launch {
            try {
                // Aspetta un momento per assicurarsi che i canali siano caricati
                delay(3000)
                
                // Controlla se ci sono programmi EPG nel database
                val programCount = epgRepository.getProgramCount()
                
                if (programCount == 0) {
                    // Carica l'EPG automaticamente se non ci sono programmi
                    loadEPGFromURL()
                    
                    // Aspetta un po' e ricontrolla
                    delay(10000) // 10 secondi
                    val newProgramCount = epgRepository.getProgramCount()
                    
                    if (newProgramCount > 0) {
                        updateDisplayedChannels()
                    }
                } else {
                    // Aggiorna la UI con i programmi esistenti
                    updateDisplayedChannels()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel controllo EPG all'avvio", e)
            }
        }
    }
    
    fun toggleChannelFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.updateFavoriteStatus(channel.id, !channel.isFavorite)
        }
    }
    
    /**
     * Carica i dati EPG dall'URL configurato nella playlist
     */
    fun loadEPGFromURL() {
        // Forza il ricaricamento pulendo prima il database
        forceReloadEPG()
    }
    
    /**
     * Forza il ricaricamento completo dell'EPG
     */
    fun forceReloadEPG() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "=== AVVIO DOWNLOAD EPG MANUALE ===")
                _isLoading.value = true
                _error.value = null
                
                // Ottieni la playlist attiva
                val activePlaylist = playlistRepository.getActivePlaylist()
                android.util.Log.d("MainViewModel", "Playlist attiva: ${activePlaylist?.name}")
                android.util.Log.d("MainViewModel", "URL EPG configurato: ${activePlaylist?.epgUrl}")
                
                if (activePlaylist?.epgUrl.isNullOrEmpty()) {
                    _error.value = "Nessun URL EPG configurato nella playlist attiva"
                    android.util.Log.w("MainViewModel", "Nessun URL EPG configurato")
                    return@launch
                }
                
                // Avvia il servizio EPG per scaricare i dati
                val intent = Intent(getApplication(), com.livetv.androidtv.service.EPGService::class.java).apply {
                    action = com.livetv.androidtv.service.EPGService.ACTION_LOAD_EPG
                    putExtra(com.livetv.androidtv.service.EPGService.EXTRA_EPG_URL, activePlaylist?.epgUrl ?: "")
                }
                
                android.util.Log.d("MainViewModel", "Avvio EPGService con URL: ${activePlaylist?.epgUrl}")
                android.util.Log.d("MainViewModel", "Intent creato: $intent")
                
                getApplication<Application>().startService(intent)
                android.util.Log.d("MainViewModel", "EPGService avviato con successo")
                
                // Log per debug (senza mostrare messaggi a schermo)
                
                _error.value = null
                
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel caricamento EPG", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Metodo per testare se ci sono programmi EPG nel database
    fun checkEPGStatus() {
        viewModelScope.launch {
            try {
                val programCount = epgRepository.getProgramCount()
                android.util.Log.d("MainViewModel", "=== CHECK EPG STATUS ===")
                android.util.Log.d("MainViewModel", "Programmi nel database: $programCount")
                android.util.Log.d("MainViewModel", "Canali in memoria: ${allChannels.size}")
                
                if (programCount > 0) {
                    val programs = epgRepository.getAllPrograms()
                    android.util.Log.d("MainViewModel", "Primi 5 programmi:")
                    programs.take(5).forEach { program ->
                        android.util.Log.d("MainViewModel", "  ${program.channelId}: ${program.title}")
                    }
                    
                    // Verifica mapping canali-programmi
                    android.util.Log.d("MainViewModel", "=== VERIFICA MAPPING ===")
                    allChannels.take(5).forEach { channel ->
                        android.util.Log.d("MainViewModel", "Canale: ${channel.name}")
                        android.util.Log.d("MainViewModel", "  ID: ${channel.id}")
                        android.util.Log.d("MainViewModel", "  EPG ID: ${channel.epgId}")
                        android.util.Log.d("MainViewModel", "  Ha EPG: ${channel.hasEPG()}")
                        
                        val matchingPrograms = programs.filter { it.channelId == channel.id }
                        android.util.Log.d("MainViewModel", "  Programmi con ID: ${matchingPrograms.size}")
                        
                        // Prova anche con epgId
                        val matchingProgramsByEpgId = if (channel.epgId != null) {
                            programs.filter { it.channelId == channel.epgId }
                        } else {
                            emptyList()
                        }
                        android.util.Log.d("MainViewModel", "  Programmi con EPG ID: ${matchingProgramsByEpgId.size}")
                        
                        if (matchingPrograms.isNotEmpty()) {
                            android.util.Log.d("MainViewModel", "  Primo programma (ID): ${matchingPrograms.first().title}")
                        }
                        if (matchingProgramsByEpgId.isNotEmpty()) {
                            android.util.Log.d("MainViewModel", "  Primo programma (EPG ID): ${matchingProgramsByEpgId.first().title}")
                        }
                    }
                    
                    // Controlla quanti canali hanno epgId impostato
                    val channelsWithEpgId = allChannels.count { it.epgId != null }
                    android.util.Log.d("MainViewModel", "=== STATISTICHE EPG ID ===")
                    android.util.Log.d("MainViewModel", "Canali totali: ${allChannels.size}")
                    android.util.Log.d("MainViewModel", "Canali con epgId: $channelsWithEpgId")
                    android.util.Log.d("MainViewModel", "Canali senza epgId: ${allChannels.size - channelsWithEpgId}")
                    
                    if (channelsWithEpgId == 0) {
                        android.util.Log.w("MainViewModel", "ATTENZIONE: Nessun canale ha epgId impostato!")
                        android.util.Log.w("MainViewModel", "Questo spiega perché i programmi EPG non vengono mappati")
                    }
                    
                    // Verifica se ci sono programmi con ID che corrispondono ai canali del channels.xml
                    android.util.Log.d("MainViewModel", "=== VERIFICA ID CHANNELS.XML ===")
                    val sampleChannelIds = listOf(
                        "e4157d818e826015e270b27d4aa6f2c2", // Italia1 HD
                        "19803a85e48fff3f0076f54e43393c75", // LA7 HD
                        "c1c43c05aaf3935f93ad5f05d2cdd5a7"  // Italia1 HD
                    )
                    
                    sampleChannelIds.forEach { xmlChannelId ->
                        val programsWithXmlId = programs.filter { it.channelId == xmlChannelId }
                        android.util.Log.d("MainViewModel", "ID channels.xml '$xmlChannelId': ${programsWithXmlId.size} programmi")
                        if (programsWithXmlId.isNotEmpty()) {
                            android.util.Log.d("MainViewModel", "  Primo: ${programsWithXmlId.first().title}")
                        }
                    }
                }
                
                // Log per debug (senza mostrare messaggi a schermo)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Errore nel controllo EPG", e)
            }
        }
    }
}