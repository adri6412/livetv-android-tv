package com.livetv.androidtv.ui.settings

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.livetv.androidtv.data.LiveTVDatabase
import com.livetv.androidtv.data.entity.Playlist
import com.livetv.androidtv.data.repository.ChannelRepository
import com.livetv.androidtv.data.repository.PlaylistRepository
import com.livetv.androidtv.data.repository.ProgramRepository
import com.livetv.androidtv.utils.M3UParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.TrafficStats
import java.net.URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = LiveTVDatabase.getDatabase(application)
    private val playlistRepository = PlaylistRepository(database.playlistDao())
    private val channelRepository = ChannelRepository(database.channelDao())
    private val programRepository = ProgramRepository(database.programDao())
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    
    private val _activePlaylist = MutableLiveData<Playlist?>()
    val activePlaylist: LiveData<Playlist?> = _activePlaylist
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    
    private val _channelCount = MutableLiveData<Int>()
    val channelCount: LiveData<Int> = _channelCount
    
    // Proprietà per logcat
    private val _logcatServerIp = MutableLiveData<String>()
    val logcatServerIp: LiveData<String> = _logcatServerIp
    
    private val _logcatServerPort = MutableLiveData<Int>()
    val logcatServerPort: LiveData<Int> = _logcatServerPort
    
    private val _isLogcatRunning = MutableLiveData<Boolean>()
    val isLogcatRunning: LiveData<Boolean> = _isLogcatRunning
    
    private var localPlaylistUri: Uri? = null
    
    fun loadSettings() {
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.getActivePlaylist()
                _activePlaylist.value = playlist
                
                if (playlist != null) {
                    val count = channelRepository.getChannelCount()
                    _channelCount.value = count
                }
                
                // Carica anche le impostazioni logcat
                loadLogcatSettings()
                
            } catch (e: Exception) {
                _message.value = "Errore nel caricamento impostazioni: ${e.message}"
            }
        }
    }
    
    fun loadLogcatSettings() {
        val ip = sharedPreferences.getString("logcat_server_ip", "192.168.1.100") ?: "192.168.1.100"
        val port = sharedPreferences.getInt("logcat_server_port", 8080)
        
        _logcatServerIp.value = ip
        _logcatServerPort.value = port
        _isLogcatRunning.value = false
    }
    
    fun saveLogcatSettings(ip: String, port: Int) {
        viewModelScope.launch {
            try {
                sharedPreferences.edit()
                    .putString("logcat_server_ip", ip)
                    .putInt("logcat_server_port", port)
                    .apply()
                
                _logcatServerIp.value = ip
                _logcatServerPort.value = port
                _message.value = "Impostazioni logcat salvate"
                
            } catch (e: Exception) {
                _message.value = "Errore nel salvataggio: ${e.message}"
            }
        }
    }
    
    fun startLogcatService() {
        viewModelScope.launch {
            try {
                val ip = _logcatServerIp.value ?: "192.168.1.100"
                val port = _logcatServerPort.value ?: 8080
                
                val intent = android.content.Intent(getApplication(), 
                    Class.forName("com.livetv.androidtv.service.LogcatService"))
                intent.action = "com.livetv.androidtv.START_LOGCAT"
                intent.putExtra("server_ip", ip)
                intent.putExtra("server_port", port)
                
                getApplication<Application>().startService(intent)
                _isLogcatRunning.value = true
                _message.value = "Servizio logcat avviato"
                
            } catch (e: Exception) {
                _message.value = "Errore nell'avvio: ${e.message}"
            }
        }
    }
    
    fun stopLogcatService() {
        viewModelScope.launch {
            try {
                val intent = android.content.Intent(getApplication(), 
                    Class.forName("com.livetv.androidtv.service.LogcatService"))
                intent.action = "com.livetv.androidtv.STOP_LOGCAT"
                
                getApplication<Application>().stopService(intent)
                _isLogcatRunning.value = false
                _message.value = "Servizio logcat fermato"
                
            } catch (e: Exception) {
                _message.value = "Errore nell'arresto: ${e.message}"
            }
        }
    }
    
    fun testRemotePlaylist(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = "Connessione in corso..."
            
            try {
                // Validazione URL sul thread principale
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    _message.value = "URL non valido. Deve iniziare con http:// o https://"
                    _channelCount.value = 0
                    return@launch
                }
                
                // Esegui operazioni di rete su thread IO
                val result = withContext(Dispatchers.IO) {
                    // Tag del traffico di rete per StrictMode
                    TrafficStats.setThreadStatsTag(0x1001)
                    
                    try {
                        val connection = URL(url).openConnection()
                        connection.connectTimeout = 15000
                        connection.readTimeout = 30000
                        
                        // Aggiungi User-Agent per compatibilità con più server
                        connection.setRequestProperty("User-Agent", "LiveTV Android/1.0")
                        connection.setRequestProperty("Accept", "*/*")
                        
                        val inputStream = connection.getInputStream()
                        val content = inputStream.bufferedReader().use { it.readText() }
                        inputStream.close()
                        
                        content
                    } finally {
                        // Rimuovi il tag del traffico
                        TrafficStats.clearThreadStatsTag()
                    }
                }
                
                _message.value = "Parsing playlist..."
                
                // Verifica che sia un file M3U valido
                if (!result.trim().startsWith("#EXTM3U")) {
                    _message.value = "Il file scaricato non è una playlist M3U valida"
                    _channelCount.value = 0
                    return@launch
                }
                
                // Parsing su thread IO
                val channels = withContext(Dispatchers.IO) {
                    M3UParser.parseM3UFromString(result)
                }
                
                _channelCount.value = channels.size
                _message.value = if (channels.isNotEmpty()) {
                    "✅ Test completato: ${channels.size} canali trovati"
                } else {
                    "⚠️ Playlist valida ma nessun canale trovato"
                }
                
            } catch (e: java.net.UnknownHostException) {
                _message.value = "❌ Errore: Host non trovato. Verifica l'URL e la connessione internet"
                _channelCount.value = 0
            } catch (e: java.net.SocketTimeoutException) {
                _message.value = "❌ Errore: Timeout connessione. Il server non risponde"
                _channelCount.value = 0
            } catch (e: java.net.ConnectException) {
                _message.value = "❌ Errore: Impossibile connettersi al server"
                _channelCount.value = 0
            } catch (e: java.io.FileNotFoundException) {
                _message.value = "❌ Errore: File non trovato (404). Verifica l'URL"
                _channelCount.value = 0
            } catch (e: javax.net.ssl.SSLException) {
                _message.value = "❌ Errore SSL: Problema con il certificato HTTPS"
                _channelCount.value = 0
            } catch (e: Exception) {
                _message.value = "❌ Errore nel test: ${e.javaClass.simpleName} - ${e.message}"
                _channelCount.value = 0
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun testLocalPlaylist() {
        localPlaylistUri?.let { uri ->
            viewModelScope.launch {
                _isLoading.value = true
                _message.value = "Test file locale in corso..."
                
                try {
                    val result = withContext(Dispatchers.IO) {
                        val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val channels = M3UParser.parseM3U(inputStream)
                            inputStream.close()
                            channels
                        } else {
                            null
                        }
                    }
                    
                    if (result != null) {
                        _channelCount.value = result.size
                        _message.value = if (result.isNotEmpty()) {
                            "✅ Test completato: ${result.size} canali trovati"
                        } else {
                            "⚠️ Nessun canale trovato nella playlist"
                        }
                    } else {
                        _message.value = "❌ Impossibile leggere il file"
                        _channelCount.value = 0
                    }
                    
                } catch (e: Exception) {
                    _message.value = "❌ Errore nel test: ${e.javaClass.simpleName} - ${e.message}"
                    _channelCount.value = 0
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
    
    fun savePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = "Salvataggio in corso..."
            
            try {
                // Salva la playlist
                _message.value = "Salvataggio playlist nel database..."
                playlistRepository.insertPlaylist(playlist)
                playlistRepository.activatePlaylist(playlist.id)
                
                _message.value = "Caricamento canali..."
                // Carica i canali
                loadChannelsFromPlaylist(playlist)
                
                _activePlaylist.value = playlist
                _message.value = "✅ Impostazioni salvate con successo"
                
            } catch (e: Exception) {
                _message.value = "❌ Errore nel salvataggio: ${e.javaClass.simpleName} - ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveLocalPlaylist(epgUrl: String?) {
        localPlaylistUri?.let { uri ->
            viewModelScope.launch {
                _isLoading.value = true
                _message.value = null
                
                try {
                    // Crea playlist locale
                    val playlist = Playlist.createLocal(
                        filePath = uri.toString(),
                        name = "Playlist locale"
                    ).copy(epgUrl = epgUrl)
                    
                    // Salva la playlist
                    playlistRepository.insertPlaylist(playlist)
                    playlistRepository.activatePlaylist(playlist.id)
                    
                    // Carica i canali
                    loadChannelsFromLocalPlaylist(uri)
                    
                    _activePlaylist.value = playlist
                    _message.value = "Impostazioni salvate con successo"
                    
                } catch (e: Exception) {
                    _message.value = "Errore nel salvataggio: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
    
    private suspend fun loadChannelsFromPlaylist(playlist: Playlist) {
        try {
            if (playlist.isRemote()) {
                // Operazioni di rete su thread IO
                val content = withContext(Dispatchers.IO) {
                    // Tag del traffico di rete per StrictMode
                    TrafficStats.setThreadStatsTag(0x1002)
                    
                    try {
                        val connection = URL(playlist.url!!).openConnection()
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
                    throw Exception("Il file scaricato non è una playlist M3U valida")
                }
                
                // Parsing su thread IO
                val channels = withContext(Dispatchers.IO) {
                    M3UParser.parseM3UFromString(content)
                }
                
                if (channels.isNotEmpty()) {
                    channelRepository.deactivateAllChannels()
                    channelRepository.insertChannels(channels)
                    channelRepository.deleteInactiveChannels()
                    
                    _channelCount.value = channels.size
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    private suspend fun loadChannelsFromLocalPlaylist(uri: Uri) {
        try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val channels = M3UParser.parseM3U(inputStream)
                inputStream.close()
                
                if (channels.isNotEmpty()) {
                    channelRepository.deactivateAllChannels()
                    channelRepository.insertChannels(channels)
                    channelRepository.deleteInactiveChannels()
                    
                    _channelCount.value = channels.size
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    fun setLocalPlaylistUri(uri: Uri) {
        localPlaylistUri = uri
    }
    
    fun hasLocalPlaylist(): Boolean = localPlaylistUri != null
    
    fun saveHbbTVSetting(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean("hbbtv_enabled", enabled)
            .apply()
    }
    
    fun clearCache() {
        viewModelScope.launch {
            try {
                channelRepository.deleteAllChannels()
                programRepository.deleteAllPrograms()
                playlistRepository.deleteAllPlaylists()
                
                _activePlaylist.value = null
                _channelCount.value = 0
                
            } catch (e: Exception) {
                _message.value = "Errore nella cancellazione cache: ${e.message}"
            }
        }
    }
}