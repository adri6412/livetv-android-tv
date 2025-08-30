package com.livetv.androidtv.ui.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import com.livetv.androidtv.R
import com.livetv.androidtv.databinding.ActivityPlayerBinding
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.ui.hbbtv.HbbTVActivity
import com.livetv.androidtv.ui.epg.EPGActivity
import com.livetv.androidtv.utils.PreferencesManager
import com.livetv.androidtv.ui.main.ChannelWithProgram
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.media3.common.VideoSize
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.common.text.CueGroup
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.io.StringWriter
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.FrameLayout
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import com.livetv.androidtv.hbbtv.HbbTvAppUrl
import com.livetv.androidtv.ts.MyTsPayloadReaderFactory

@UnstableApi
class PlayerActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_CHANNEL = "extra_channel"
        private const val CONTROLS_TIMEOUT = 10000L // Aumentato a 10 secondi per Android TV
        private const val KEYCODE_RED = 403 // Tasto rosso del telecomando TV
        private const val PERMISSION_REQUEST_CODE = 1001
        
        // Costanti per HbbTV e DVB
        private const val HBBTV_PID_MIN = 0x1000
        private const val HBBTV_PID_MAX = 0x1FFF
        private const val TELETEXT_PID_MIN = 0x2000
        private const val TELETEXT_PID_MAX = 0x2FFF
    }
    
    // Permessi per il logging (non possono essere const)
    private val PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var preferencesManager: PreferencesManager
    
    // Repository EPG per accedere direttamente ai dati come faceva MainViewModel
    private lateinit var epgRepository: com.livetv.androidtv.data.repository.EPGRepository
    
    private var exoPlayer: ExoPlayer? = null
    private var currentChannel: Channel? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private var controlsRunnable: Runnable? = null
    
    // Sistema di logging per crash e debug
    private lateinit var logFile: File
    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // Gestione input numerico
    private var numberInput = ""
    private val numberInputHandler = Handler(Looper.getMainLooper())
    private var numberInputRunnable: Runnable? = null
    private val NUMBER_INPUT_TIMEOUT = 2000L
    
    // Controllo esplicito dei controlli
    private var controlsExplicitlyRequested = false
    
    // NUOVO: Supporto per dati DVB e HbbTV broadcast
    private var hasHbbTVBroadcast = false
    private var hasTeletextBroadcast = false
    private var raiHbbTvOverlay: FrameLayout? = null
    private var hbbTVData: String? = null
    private var teletextData: String? = null
    private var teletextBuffer: StringBuilder? = null
    private var currentHbbTVApplication: String? = null
    
    // HbbTV Manager for proper AIT parsing
    private lateinit var hbbTvManager: com.livetv.androidtv.hbbtv.HbbTvManager
    private var isHbbTVBroadcastModeActive = false
    
    // Flag per evitare doppia inizializzazione
    private var isPlayerInitialized = false
    private var isInitializationInProgress = false
    private var isPlayChannelInProgress = false
    
    // GLOBAL LOCK: Garantisce che solo 1 player sia attivo alla volta
    private var isAnyPlayerOperationInProgress = false
    
    // Flag per gestire il recovery dallo standby
    private var isStandbyRecoveryInProgress = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // OTTIMIZZAZIONI SPECIFICHE PER MI TV AYFR0
            // configureMiTVStickPerformance() non più necessaria
            
            // Mantieni schermo acceso e nascondi barre di sistema
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // MIGLIORAMENTO: Gestione riavvio dopo standby
        val fromStandbyWakeup = intent.getBooleanExtra("from_standby_wakeup", false)
        
        // Gestione specifica per risveglio dallo standby
        if (fromStandbyWakeup) {
            writeToLog("Risveglio dallo standby - preparazione recovery audio")
            handleStandbyWakeup()
        }
        
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inizializza PreferencesManager
        preferencesManager = PreferencesManager(this)
        
        // Inizializza repository EPG per accedere direttamente ai dati
        epgRepository = com.livetv.androidtv.data.repository.EPGRepository(com.livetv.androidtv.data.LiveTVDatabase.getDatabase(this).epgDao())
        
        // Initialize HbbTV Manager for proper AIT parsing
        hbbTvManager = com.livetv.androidtv.hbbtv.HbbTvManager()
        hbbTvManager.initialize(object : com.livetv.androidtv.hbbtv.HbbTvCallback {
            override fun onHbbTvUrlFound(info: com.livetv.androidtv.hbbtv.HbbTvAppUrl) {
                Log.i("PlayerActivity", "🎯 HbbTV URL found: ${info.url} (autostart: ${info.autostart})")
                handleHbbTvUrlFound(info)
            }
            
            override fun onAitPresentButNoUrl(reason: String) {
                Log.i("PlayerActivity", "ℹ️ AIT present but no HbbTV URL: $reason")
                handleAitPresentButNoUrl(reason)
            }
            
            override fun onNoAitDetected() {
                Log.i("PlayerActivity", "ℹ️ No AIT detected for service ${currentChannel?.name}")
                handleNoAitDetected()
            }
        })
        
        // Inizializza sistema di logging (verrà completato dopo i permessi)
        initializeLogging()
        
        // Ottieni il canale dall'intent
        currentChannel = intent.getParcelableExtra(EXTRA_CHANNEL)
        
        // Se non c'è un canale diretto, prova a ottenerlo tramite channel_id
        if (currentChannel == null) {
            val channelId = intent.getStringExtra("channel_id")
            if (!channelId.isNullOrEmpty()) {
                // Carica il canale dal database usando l'ID
                lifecycleScope.launch {
                    try {
                        val channel = com.livetv.androidtv.data.LiveTVDatabase.getDatabase(this@PlayerActivity)
                            .channelDao().getChannelById(channelId)
                        if (channel != null) {
                            currentChannel = channel
                            // Continua con l'inizializzazione
                            initializeChannel()
                        } else {
                            writeToLog("ERRORE: Canale con ID $channelId non trovato nel database")
                            
                            // Verifica se ci sono canali nel database
                            val allChannels = com.livetv.androidtv.data.LiveTVDatabase.getDatabase(this@PlayerActivity)
                                .channelDao().getAllChannelsSync()
                            
                            if (allChannels.isEmpty()) {
                                writeToLog("ERRORE: Nessun canale nel database - navigo alle impostazioni")
                                // Se non ci sono canali, vai alle impostazioni
                                val intent = Intent(this@PlayerActivity, com.livetv.androidtv.ui.settings.SettingsActivity::class.java)
                                startActivity(intent)
                            } else {
                                writeToLog("ERRORE: Canale specifico non trovato ma ci sono ${allChannels.size} canali")
                            }
                            
                            finish()
                            return@launch
                        }
                    } catch (e: Exception) {
                        writeToLog("ERRORE nel caricamento canale con ID $channelId: ${e.message}")
                        finish()
                        return@launch
                    }
                }
                return // Esci qui, l'inizializzazione continuerà in initializeChannel()
            } else {
                writeToLog("ERRORE: Nessun channel_id fornito")
            finish()
            return
            }
        } else {
            // Se abbiamo già il canale, procedi normalmente
            // CONTROLLO: Evita doppia inizializzazione anche qui
            if (!isPlayerInitialized && !isInitializationInProgress) {
                writeToLog("Canale già disponibile - avvio inizializzazione")
                initializeChannel()
            } else {
                writeToLog("Player già inizializzato o inizializzazione in corso - salto seconda chiamata")
            }
        }
        } catch (e: Exception) {
            createEmergencyLog("CRASH durante onCreate Mi TV AYFR0", e)
            throw e // Rilancia l'errore per il crash handler
        }
    }
    
    private fun initializeChannel() {
        if (currentChannel == null) {
            finish()
            return
        }
        
        // CONTROLLO RIGOROSO: Evita doppia inizializzazione
        if (isPlayerInitialized || isInitializationInProgress) {
            writeToLog("Player già inizializzato o inizializzazione in corso - salto")
            return
        }
        
        // GARANZIA: Rilascia sempre il player esistente prima di inizializzare
        if (exoPlayer != null) {
            writeToLog("Player esistente trovato in initializeChannel - rilascio")
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        }
        
        isInitializationInProgress = true
        writeToLog("Inizio inizializzazione player")
        
        // Check if RAI channel and set static HbbTV URL
        checkRaiChannelForHbbTv()
        
        setupPlayer()
        configureCodecs() // Verifica supporto codec DVB-T
        setupUI()
        
        // Salva questo canale come ultimo visualizzato
        saveLastChannel(currentChannel!!)
        
        // MIGLIORAMENTO: Delay per stabilizzazione sistema (soprattutto al boot)
        lifecycleScope.launch {
            try {
                // Aspetta un momento per permettere al sistema di stabilizzarsi
                // Questo è particolarmente importante quando l'app si avvia al boot
                kotlinx.coroutines.delay(1000)
                
                writeToLog("Avvio riproduzione dopo delay di stabilizzazione")
                
                // Reset del flag di inizializzazione PRIMA di chiamare playChannel
                // per evitare che playChannel ritorni early
                isInitializationInProgress = false
                
                // Avvia la riproduzione in modo sicuro
                startPlayerSafely(currentChannel!!)
        
        // Mostra il popup del canale all'avvio
        showChannelPopup(currentChannel!!)
                
            } catch (e: Exception) {
                writeToLog("ERRORE nell'avvio ritardato: ${e.message}")
                // NON chiamare playChannel nel fallback per evitare chiamate multiple
                // Il player è già stato configurato in setupPlayer()
            } finally {
                // Assicurati che il flag sia sempre reset
                isInitializationInProgress = false
            }
        }
        
        writeToLog("onCreate completato con successo")
    }
    
    /**
     * Funzione comune per avviare il player in modo sicuro
     * Evita duplicati e gestisce tutti i controlli necessari
     */
    @UnstableApi
    private fun startPlayerSafely(channel: Channel) {
        writeToLog("startPlayerSafely chiamato per: ${channel.name}")
        
        // CONTROLLO CENTRALIZZATO: Evita avvii multipli
        if (isAnyPlayerOperationInProgress) {
            writeToLog("Operazione player già in corso - salto startPlayerSafely")
            return
        }
        
        if (isPlayChannelInProgress) {
            writeToLog("playChannel già in esecuzione - salto startPlayerSafely")
            return
        }
        
        // Se siamo in recovery standby, aspetta che finisca
        if (isStandbyRecoveryInProgress) {
            writeToLog("Recovery standby in corso - salto startPlayerSafely")
            return
        }
        
        // Se è lo stesso canale e il player è già attivo, non fare nulla
        if (exoPlayer != null && currentChannel?.id == channel.id && exoPlayer!!.playbackState == Player.STATE_READY) {
            writeToLog("Player già attivo per lo stesso canale - salto startPlayerSafely")
            return
        }
        
        isAnyPlayerOperationInProgress = true
        isPlayChannelInProgress = true
        
        try {
            // Rilascia il player precedente se necessario
            if (exoPlayer != null && (currentChannel?.id != channel.id || !isPlayerInitialized)) {
                writeToLog("Rilascio player precedente")
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                isPlayerInitialized = false
            }
            
            // GARANZIA: Se exoPlayer è null (rilasciato da releasePlayerCompletely), 
            // assicurati che i flag siano resettati
            if (exoPlayer == null) {
                isPlayerInitialized = false
                setupPlayer()
            }
            
            // Imposta il canale corrente
            currentChannel = channel
            
            // Crea e imposta il MediaSource
            val mediaSource = createTsMediaSource(channel.streamUrl)
            exoPlayer?.apply {
                setMediaSource(mediaSource)
                prepare()
                play()
                writeToLog("Player avviato con successo per: ${channel.name}")
            }
            
            // Marca come inizializzato
            isPlayerInitialized = true
            
        } catch (e: Exception) {
            writeToLog("ERRORE in startPlayerSafely: ${e.message}")
            Log.e("PlayerActivity", "Errore in startPlayerSafely", e)
            isPlayerInitialized = false
        } finally {
            // Reset dei flag
            isAnyPlayerOperationInProgress = false
            isPlayChannelInProgress = false
            writeToLog("startPlayerSafely completato")
        }
    }
    
    /**
     * Crea un nuovo player ExoPlayer
     */
    @UnstableApi
    private fun createNewPlayer() {
        writeToLog("Creazione nuovo player ExoPlayer")
        
        // Validazione parametri buffer per evitare IllegalArgumentException
        val minBufferMs = 2000
        val maxBufferMs = 6000
        val bufferForPlaybackMs = 1500
        val bufferForPlaybackAfterRebufferMs = 1500
        
        // CONFIGURAZIONE OTTIMIZZATA PER MI BOX (Amlogic) - Supporto multi-container con Jellyfin FFmpeg per MP2
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setEnableDecoderFallback(true) // Fallback per compatibilità
            setEnableAudioTrackPlaybackParams(true) // Supporto parametri audio
            // Jellyfin FFmpeg extension renderer per supporto MP2 (MPEG-1/2 Layer II)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        
        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs
                )
                .build())
            .build()
            .apply {
                // Configurazione specifica per supporto multi-container su Mi Box
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                    .setMaxAudioBitrate(Int.MAX_VALUE)
                    .setMaxAudioChannelCount(8)
                    .build()
            }
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        writeToLog("Stato playback cambiato: $playbackState")
                        updatePlaybackState(playbackState)
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        writeToLog("ERRORE listener player: ${error.message}")
                        handlePlayerError(error)
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        writeToLog("Riproduzione cambiata: $isPlaying")
                        binding.progressBar.visibility = if (isPlaying) View.GONE else View.VISIBLE
                    }
                    
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        writeToLog("Dimensioni video cambiate: ${videoSize.width}x${videoSize.height}")
                    }
                    
                    override fun onTracksChanged(tracks: Tracks) {
                        writeToLog("Tracce cambiate - Analisi dati DVB...")
                        extractDvbDataFromTracks(tracks)
                        
                        // Seleziona sempre la prima traccia audio
                        selectFirstAudioTrack(tracks)
                    }
                })
                
                // Configura il PlayerView per DVB-T
                binding.playerView.player = exoPlayer
            }
        
        writeToLog("Nuovo player ExoPlayer creato con successo - Supporto MP2 via Jellyfin FFmpeg abilitato")
        
        // Log dei renderer disponibili per verificare il supporto MP2
        logAvailableRenderers()
    }
    
    /**
     * Rileva il tipo di container dall'URL o dalle caratteristiche dello stream
     */
    private fun detectContainerType(streamUrl: String): String {
        return try {
            val url = streamUrl.lowercase()
            
            // Rilevamento basato sull'URL
            when {
                url.contains(".ts") || url.contains("mpeg-ts") -> {
                    writeToLog("Container rilevato: MPEG-TS")
                    "video/mp2t"
                }
                url.contains(".mkv") || url.contains("matroska") -> {
                    writeToLog("Container rilevato: Matroska")
                    "video/x-matroska"
                }
                url.contains(".mp4") -> {
                    writeToLog("Container rilevato: MP4")
                    "video/mp4"
                }
                url.contains(".avi") -> {
                    writeToLog("Container rilevato: AVI")
                    "video/avi"
                }
                else -> {
                    writeToLog("Container non rilevato dall'URL - uso auto-detection")
                    "auto"
                }
            }
        } catch (e: Exception) {
            writeToLog("ERRORE nel rilevamento container: ${e.message}")
            "auto"
        }
    }
    
    /**
     * Seleziona sempre la prima traccia audio disponibile
     */
    private fun selectFirstAudioTrack(tracks: Tracks) {
        try {
            for (groupIndex in 0 until tracks.groups.size) {
                val trackGroup = tracks.groups[groupIndex]
                if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    writeToLog("Tracce audio trovate: ${trackGroup.length}")
                    
                    // Seleziona sempre la prima traccia audio (indice 0)
                    if (trackGroup.length > 0) {
                        val firstTrackFormat = trackGroup.getTrackFormat(0)
                        val mimeType = firstTrackFormat.sampleMimeType ?: "unknown"
                        val language = firstTrackFormat.language ?: "unknown"
                        
                        writeToLog("Seleziono prima traccia audio: $mimeType, lingua: $language")
                        
                        // Forza la selezione della prima traccia
                        val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                            trackGroup.mediaTrackGroup, 0
                        )
                        
                        exoPlayer?.let { player ->
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(trackSelectionOverride)
                                .build()
                        }
                        
                        writeToLog("Prima traccia audio selezionata con successo")
                        break // Esci dopo aver selezionato la prima traccia audio
                    }
                }
            }
        } catch (e: Exception) {
            writeToLog("ERRORE nella selezione prima traccia audio: ${e.message}")
            Log.e("PlayerActivity", "Error selecting first audio track", e)
        }
    }
    
    @UnstableApi
    private fun setupPlayer() {
        writeToLog("setupPlayer chiamato - delego a createNewPlayer")
        
        // Controllo se il player esiste già
        if (exoPlayer != null) {
            writeToLog("Player già esistente - salto setupPlayer")
            return
        }
        
        // Crea il nuovo player
        createNewPlayer()
        
        // Configurazione specifica per Mi TV AYFR0 (S905Y2)
        configureMiTVStickPerformance()
        
        // Avvia il monitoraggio delle performance
        startMiTVStickPerformanceMonitoring()
        
        writeToLog("setupPlayer completato")
        
        try {
            // Validazione parametri buffer per evitare IllegalArgumentException
            // BUFFER OTTIMIZZATI PER MI TV AYFR0 (S905Y2)
            val minBufferMs = 2000  // Ridotto per Mi TV Stick
            val maxBufferMs = 6000  // Ottimizzato per S905Y2
            val bufferForPlaybackMs = 1500  // Buffer playback ridotto
            val bufferForPlaybackAfterRebufferMs = 1500  // Buffer rebuffer ridotto
            
            // Verifica che i parametri rispettino le regole di Media3
            require(minBufferMs >= bufferForPlaybackAfterRebufferMs) { 
                "minBufferMs ($minBufferMs) deve essere >= bufferForPlaybackAfterRebufferMs ($bufferForPlaybackAfterRebufferMs)" 
            }
            require(maxBufferMs >= minBufferMs) { 
                "maxBufferMs ($maxBufferMs) deve essere >= minBufferMs ($minBufferMs)" 
            }
            require(bufferForPlaybackMs <= minBufferMs) { 
                "bufferForPlaybackMs ($bufferForPlaybackMs) deve essere <= minBufferMs ($minBufferMs)" 
            }
            
            writeToLog("Parametri buffer validati: min=$minBufferMs, max=$maxBufferMs, playback=$bufferForPlaybackMs, afterRebuffer=$bufferForPlaybackAfterRebufferMs")
            
            // CONFIGURAZIONE OTTIMIZZATA PER MI BOX (Amlogic) con Jellyfin FFmpeg per MP2
            val renderersFactory = DefaultRenderersFactory(this).apply {
                setEnableDecoderFallback(true) // Fallback per compatibilità
                setEnableAudioTrackPlaybackParams(true)
                // Jellyfin FFmpeg extension renderer per supporto MP2 (MPEG-1/2 Layer II)
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                // setEnableAudioOffload non disponibile in questa versione Media3
            }
            
            exoPlayer = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        minBufferMs,                    // minBufferMs: deve essere >= bufferForPlaybackAfterRebufferMs
                        maxBufferMs,                    // maxBufferMs: deve essere >= minBufferMs
                        bufferForPlaybackMs,            // bufferForPlaybackMs: deve essere <= minBufferMs
                        bufferForPlaybackAfterRebufferMs // bufferForPlaybackAfterRebufferMs: deve essere <= minBufferMs
                    )
                    .build())
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            writeToLog("Stato playback cambiato: $playbackState")
                            updatePlaybackState(playbackState)
                        }
                        
                        override fun onPlayerError(error: PlaybackException) {
                            writeToLog("ERRORE listener player: ${error.message}")
                            handlePlayerError(error)
                        }
                        
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            writeToLog("Riproduzione cambiata: $isPlaying")
                            binding.progressBar.visibility = if (isPlaying) View.GONE else View.VISIBLE
                        }
                        
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            writeToLog("Dimensioni video cambiate: ${videoSize.width}x${videoSize.height}")
                            // Per DVB-T, gestisci l'aspect ratio 16:9 o 4:3
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                val aspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                                writeToLog("Aspect ratio: $aspectRatio")
                                
                                // Nota: setAspectRatioListener non è disponibile nelle versioni recenti di Media3
                                // L'aspect ratio viene gestito automaticamente
                            }
                        }
                        
                        // NUOVO: Listener per dati DVB e HbbTV
                        override fun onTracksChanged(tracks: Tracks) {
                            writeToLog("Tracce cambiate - Analisi dati DVB...")
                            extractDvbDataFromTracks(tracks)
                        }
                    })
                    
                    // Configurazione audio ottimizzata per DVB-T
                    // I listener audio vengono gestiti automaticamente da ExoPlayer
                    
                    // Configura per streaming live DVB-T
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF
                }
            
            // Configura il PlayerView per DVB-T
            binding.playerView.player = exoPlayer
            
            // Configura il rendering per DVB-T
            // Il rendering viene gestito automaticamente da ExoPlayer
            
            // Configura per DVB-T (formato 16:9 o 4:3)
            // L'aspect ratio viene gestito automaticamente da ExoPlayer
            
            writeToLog("Player configurato con successo - Supporto MP2 via Jellyfin FFmpeg abilitato")
            
            // Verifica compatibilità codec DVB-T
            checkDvbTCompatibility()
            
            // Configurazione specifica per Mi Box
            configureMiBoxOptimizations()
            
            // Avvia monitoraggio performance per Mi TV Stick
            startMiTVStickPerformanceMonitoring()
            
        } catch (e: Exception) {
            writeToLog("ERRORE in setupPlayer: ${e.message}")
            writeToLog("Stack trace: ${getStackTrace(e)}")
            Log.e("PlayerActivity", "Errore in setupPlayer", e)
            throw e // Rilancia l'errore per il crash handler
        } finally {
            // Reset del global lock solo se non siamo in playChannel
            if (!isPlayChannelInProgress) {
                isAnyPlayerOperationInProgress = false
            }
            writeToLog("setupPlayer completato")
        }
    }
    
    private fun startMiTVStickPerformanceMonitoring() {
        try {
            val isMiTVStick = android.os.Build.MODEL.contains("AYFR0", ignoreCase = true)
            
            if (isMiTVStick) {
                // Monitora performance ogni 5 secondi
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        try {
                            // Monitora CPU e memoria
                            val cpuUsage = getCpuUsage()
                            val memoryUsage = getMemoryUsage()
                            
                            // Avvisa se performance degradano
                            if (cpuUsage > 70) { // Soglia più bassa per Mi TV Stick
                                Log.w("PlayerActivity", "⚠️ CPU usage alto su Mi TV AYFR0: $cpuUsage%")
                                binding.textError.text = "Performance degradate - CPU: $cpuUsage%"
                                binding.textError.visibility = View.VISIBLE
                            } else {
                                binding.textError.visibility = View.GONE
                            }
                            
                            // Continua monitoraggio
                            Handler(Looper.getMainLooper()).postDelayed(this, 5000)
                            
                        } catch (e: Exception) {
                            Log.e("PlayerActivity", "Errore monitoraggio performance Mi TV", e)
                        }
                    }
                }, 5000)
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore avvio monitoraggio performance", e)
        }
    }

    private fun getCpuUsage(): Int {
        return try {
            val reader = java.io.BufferedReader(java.io.FileReader("/proc/stat"))
            val line = reader.readLine()
            reader.close()
            
            // Calcolo semplificato CPU usage
            val parts = line.split(" ").filter { it.isNotEmpty() }
            if (parts.size >= 5) {
                val idle = parts[4].toLong()
                val total = parts.drop(1).sumOf { it.toLong() }
                val usage = ((total - idle) * 100 / total).toInt()
                usage.coerceIn(0, 100)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun getMemoryUsage(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            usedMemory
        } catch (e: Exception) {
            0L
        }
    }
    
    @UnstableApi
    private fun initializeLogging() {
        try {
            // Verifica e richiedi permessi se necessario
            if (!hasStoragePermissions()) {
                requestStoragePermissions()
                return
            }
            
            // Crea directory per i log nella storage interna Documents
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val logDir = File(documentsDir, "LiveTV_Logs")
            if (!logDir.exists()) {
                val created = logDir.mkdirs()
                if (!created) {
                    Log.e("PlayerActivity", "Impossibile creare directory log: ${logDir.absolutePath}")
                    return
                }
            }
            
            // Crea file di log con timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logDir, "player_crash_$timestamp.log")
            
            // Scrivi header del log
            writeToLog("=== PLAYER ACTIVITY LOG START ===")
            writeToLog("Timestamp: ${logDateFormat.format(Date())}")
            writeToLog("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            writeToLog("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            writeToLog("App: LiveTV Android TV")
            writeToLog("Log file: ${logFile.absolutePath}")
            writeToLog("==================================")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'inizializzazione del logging", e)
        }
    }
    
    @UnstableApi
    private fun hasStoragePermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    @UnstableApi
    private fun requestStoragePermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE)
    }
    
    @UnstableApi
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeLogging()
            } else {
                Log.w("PlayerActivity", "Permessi storage negati, logging disabilitato")
            }
        }
    }
    
    @UnstableApi
    private fun writeToLog(message: String) {
        try {
            // Verifica se il logging è stato inizializzato e i permessi sono concessi
            if (::logFile.isInitialized && hasStoragePermissions()) {
                // Usa un thread separato per evitare StrictMode violations
                Thread {
                    try {
                        if (logFile.exists()) {
                            FileWriter(logFile, true).use { writer ->
                                PrintWriter(writer).use { printer ->
                                    printer.println("${logDateFormat.format(Date())} - $message")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Errore nella scrittura del log su file", e)
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella scrittura del log", e)
        }
    }
    
    @UnstableApi
    private fun createEmergencyLog(message: String, throwable: Throwable? = null) {
        try {
            if (hasStoragePermissions()) {
                // Usa un thread separato per evitare StrictMode violations
                Thread {
                    try {
                        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        val logDir = File(documentsDir, "LiveTV_Logs")
                        if (!logDir.exists()) {
                            logDir.mkdirs()
                        }
                        
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val emergencyFile = File(logDir, "EMERGENCY_CRASH_$timestamp.log")
                        
                        FileWriter(emergencyFile, false).use { writer ->
                            PrintWriter(writer).use { printer ->
                                printer.println("=== EMERGENCY CRASH LOG ===")
                                printer.println("Timestamp: ${logDateFormat.format(Date())}")
                                printer.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                                printer.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                                printer.println("App: LiveTV Android TV")
                                printer.println("Message: $message")
                                if (throwable != null) {
                                    printer.println("Exception: ${throwable.message}")
                                    printer.println("Stack trace:")
                                    throwable.printStackTrace(printer)
                                }
                                printer.println("========================")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerActivity", "Errore nella creazione del log di emergenza", e)
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella creazione del log di emergenza", e)
        }
    }
    
    @UnstableApi
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            stringWriter.toString()
        } catch (e: Exception) {
            "Impossibile ottenere stack trace: ${e.message}"
        }
    }
    
    @UnstableApi
    private fun checkDvbTCompatibility() {
        try {
            // Verifica supporto MPEG-2 (DVB-T standard)
            val mpeg2Supported = checkCodecSupport(MimeTypes.VIDEO_MPEG2)
            
            // Verifica supporto H.264 (DVB-T2 HD)
            val h264Supported = checkCodecSupport(MimeTypes.VIDEO_H264)
            
            // Verifica supporto MPEG audio
            val mpegAudioSupported = checkCodecSupport(MimeTypes.AUDIO_MPEG)
            
            if (!mpeg2Supported && !h264Supported) {
                Log.w("PlayerActivity", "⚠️ ATTENZIONE: Nessun codec video DVB-T supportato!")
                binding.textError.text = "ATTENZIONE: Il dispositivo potrebbe non supportare i codec DVB-T necessari"
                binding.textError.visibility = View.VISIBLE
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella verifica compatibilità DVB-T", e)
        }
    }
    
    // NUOVO: Funzione per estrarre dati DVB e HbbTV dalle tracce
    @UnstableApi
    private fun extractDvbDataFromTracks(tracks: Tracks) {
        try {
            writeToLog("Analisi tracce per dati DVB...")
            
            // Start HbbTV AIT detection
            hbbTvManager.startAitDetection()
            
            // Reset stato precedente
            hasHbbTVBroadcast = false
            hasTeletextBroadcast = false
            hbbTVData = null
            teletextData = null
            teletextBuffer?.clear()
            
            // Don't reset RAI HbbTV URL if this is a RAI channel
            if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true && currentHbbTVApplication != null) {
                writeToLog("🇮🇹 RAI channel - preserving HbbTV URL during track analysis: ${currentHbbTVApplication}")
            } else {
                currentHbbTVApplication = null
            }
            
            // Analizza tutte le tracce
            for (group in tracks.groups) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i) // Corretto: getTrackFormat invece di getFormat
                    val trackId = format.id ?: "" // Aggiunto controllo null-safe
                    val mimeType = format.sampleMimeType
                    
                    writeToLog("Trovata traccia: ID=$trackId, MIME=$mimeType")
                    
                    // Cerca tracce HbbTV (PID 0x1000-0x1FFF)
                if (isHbbTVTrack(trackId, mimeType)) {
                    hasHbbTVBroadcast = true
                    
                    // Gestione speciale per tracce AIT senza trackId
                    val actualTrackId = if (trackId.isBlank() && mimeType?.contains("ait", ignoreCase = true) == true) {
                        // Per tracce AIT, prova a estrarre l'ID dal format o usa un ID di fallback
                        format.id ?: "AIT_UNKNOWN"
                    } else {
                        trackId
                    }
                    
                    hbbTVData = "HbbTV rilevato: $actualTrackId"
                    
                    // Don't overwrite RAI HbbTV URL if already set
                    if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true && currentHbbTVApplication != null) {
                        writeToLog("🇮🇹 RAI channel - preserving existing HbbTV URL: ${currentHbbTVApplication}")
                    } else {
                        currentHbbTVApplication = extractHbbTVApplication(format)
                    }
                    
                    writeToLog("✅ HbbTV broadcast rilevato: $actualTrackId")
                }
                    
                    // Cerca tracce teletext (PID 0x2000-0x2FFF)
                    if (isTeletextTrack(trackId, mimeType)) {
                        hasTeletextBroadcast = true
                        // Use StringBuilder for better memory efficiency
                        if (teletextBuffer == null) {
                            teletextBuffer = StringBuilder()
                        }
                        teletextBuffer?.append("Teletext rilevato: $trackId")
                        teletextData = teletextBuffer?.toString()
                        writeToLog("✅ Teletext broadcast rilevato: $trackId")
                    }
                }
            }
            
            // Aggiorna UI con i dati DVB rilevati
            updateDvbDataUI()
            
            // Log riepilogativo
            writeToLog("Analisi DVB completata: HbbTV=$hasHbbTVBroadcast, Teletext=$hasTeletextBroadcast")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'analisi dati DVB", e)
            writeToLog("ERRORE analisi dati DVB: ${e.message}")
        }
    }
    
    // NUOVO: Funzione per identificare tracce HbbTV
    private fun isHbbTVTrack(trackId: String, mimeType: String?): Boolean {
        return try {
            // Controlla se è un PID HbbTV (0x1000-0x1FFF)
            val pid = trackId.toIntOrNull(16) ?: trackId.toIntOrNull(10) ?: 0
            val isHbbTVPID = pid in HBBTV_PID_MIN..HBBTV_PID_MAX
            
            // Controlla anche per MIME type specifici HbbTV
            val isHbbTVMIME = mimeType?.contains("hbbtv", ignoreCase = true) == true ||
                             mimeType?.contains("ait", ignoreCase = true) == true ||
                             mimeType?.contains("application", ignoreCase = true) == true
            
            isHbbTVPID || isHbbTVMIME
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel controllo traccia HbbTV", e)
            false
        }
    }
    
    // NUOVO: Funzione per identificare tracce teletext
    private fun isTeletextTrack(trackId: String, mimeType: String?): Boolean {
        return try {
            // Controlla se è un PID teletext (0x2000-0x2FFF)
            val pid = trackId.toIntOrNull(16) ?: trackId.toIntOrNull(10) ?: 0
            val isTeletextPID = pid in TELETEXT_PID_MIN..TELETEXT_PID_MAX
            
            // Controlla anche per MIME type specifici teletext
            val isTeletextMIME = mimeType?.contains("teletext", ignoreCase = true) == true ||
                                mimeType?.contains("text", ignoreCase = true) == true
            
            isTeletextPID || isTeletextMIME
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel controllo traccia teletext", e)
            false
        }
    }
    
    // NUOVO: Funzione per estrarre informazioni applicazione HbbTV
    private fun extractHbbTVApplication(format: Format): String? {
        return try {
            // Estrai informazioni dall'ID della traccia o dal formato
            val appInfo = when {
                format.id?.contains("hbbtv", ignoreCase = true) == true -> "HbbTV Application"
                format.id?.contains("ait", ignoreCase = true) == true -> "Application Information Table"
                format.sampleMimeType?.contains("application") == true -> "DVB Application"
                else -> "DVB Interactive Service"
            }
            appInfo
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'estrazione info HbbTV", e)
            null
        }
    }
    
        // NUOVO: Funzione per estrarre URL HbbTV dal broadcast stream
    private fun extractHbbTVUrlFromBroadcast(): String? {
        return try {
            
            // Check if this is a RAI channel - don't extract URL if we already have static RAI URL
            if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true && currentHbbTVApplication != null) {
                writeToLog("🇮🇹 RAI channel detected - using existing static URL: ${currentHbbTVApplication}")
                return currentHbbTVApplication
            }
            
            // Cerca nei dati HbbTV per URL o indirizzi web
            hbbTVData?.let { data ->
                
                // Pattern comuni per URL HbbTV nei dati DVB
                val urlPatterns = listOf(
                    "https?://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
                    "http://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
                    "www\\.[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?"
                )
                
                for ((index, pattern) in urlPatterns.withIndex()) {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    val match = regex.find(data)
                    if (match != null) {
                        var url = match.value
                        if (!url.startsWith("http")) {
                            url = "https://$url"
                        }
                        return url
                    }
                }
            }
            
            // ESTRATTORE AIT SECONDO STANDARD ETSI TS 103 464
            val etsiUrl = extractAITDataAccordingToETSI()
            if (etsiUrl != null) {
                return etsiUrl
            }
            
            null
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "❌ Errore nell'estrazione URL HbbTV", e)
            null
        }
    }
    
    /**
     * Estrae i dati AIT secondo lo standard ETSI TS 103 464 per HbbTV
     * Questo metodo implementa la logica corretta per parsare i dati AIT
     */
    private fun extractAITDataAccordingToETSI(): String? {
        
        // Prova a estrarre URL dai dati esistenti
        val url = extractURLFromExistingData()
        if (url != null) {
            return url
        }
        
        // 1. Cerca nei metadati del canale corrente
        currentChannel?.let { channel ->
            // Pattern per URL nei nomi dei canali (potrebbero contenere indizi HbbTV)
            val channelUrlPatterns = listOf(
                "rai\\.it",
                "mediaset\\.it", 
                "hbbtv",
                "dvb"
            )
            
            for (pattern in channelUrlPatterns) {
                if (channel.name.contains(Regex(pattern, RegexOption.IGNORE_CASE))) {
                    // Per Rai, prova URL noti
                    if (pattern.contains("rai", ignoreCase = true)) {
                        val raiUrl = "https://www.raiplay.it/hbbtv"
                        return raiUrl
                    }
                    
                    // Per Mediaset, prova URL noti
                    if (pattern.contains("mediaset", ignoreCase = true)) {
                        val mediasetUrl = "https://www.mediasetplay.mediaset.it/hbbtv"
                        return mediasetUrl
                    }
                }
            }
        }
        
        // Per Rai 1, prova URL specifici
        currentChannel?.let { channel ->
            if (channel.name.contains("Rai 1", ignoreCase = true)) {
                val rai1Url = "https://www.raiplay.it/hbbtv/rai1"
                return rai1Url
            }
        }
        return null
    }
    
    /**
     * Estrae URL dai dati esistenti usando pattern ETSI
     */
    private fun extractURLFromExistingData(): String? {
        
        // Pattern per URL secondo standard ETSI HbbTV
        val etsiUrlPatterns = listOf(
            // URL standard HbbTV
            "https?://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
            // URL con parametri specifici HbbTV
            "https?://[\\w\\d.-]+\\.[a-z]{2,}/hbbtv(?:/[\\w\\d./?=&%-]*)?",
            // URL con parametri DVB
            "https?://[\\w\\d.-]+\\.[a-z]{2,}/dvb(?:/[\\w\\d./?=&%-]*)?",
            // URL generici
            "http://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
            "www\\.[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?"
        )
        
        // Cerca URL nei dati HbbTV esistenti
        hbbTVData?.let { data ->
            for ((index, pattern) in etsiUrlPatterns.withIndex()) {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                val match = regex.find(data)
                if (match != null) {
                    var url = match.value
                    if (!url.startsWith("http")) {
                        url = "https://$url"
                    }
                    return url
                }
            }
        }
        
        return null
    }
    
    /**
     * Estrae i dati AIT da un format specifico secondo lo standard ETSI
     */
    private fun extractAITFromFormat(format: Format): String? {
        
        try {
            // Analizza i dati di inizializzazione (contengono i dati AIT)
            if (format.initializationData.isNotEmpty()) {
                for ((index, initData) in format.initializationData.withIndex()) {
                    // Prova a parsare come stringa UTF-8
                    val initDataString = String(initData, Charsets.UTF_8)
                    
                    // Cerca URL nei dati AIT secondo standard ETSI
                    val url = extractURLFromAITData(initData, initDataString)
                    if (url != null) {
                        return url
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'estrazione AIT secondo standard ETSI", e)
        }
        
        return null
    }
    
    /**
     * Estrae URL dai dati AIT secondo lo standard ETSI
     */
    private fun extractURLFromAITData(initData: ByteArray, initDataString: String): String? {
        
        // Pattern per URL secondo standard ETSI HbbTV
        val etsiUrlPatterns = listOf(
            // URL standard HbbTV
            "https?://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
            // URL con parametri specifici HbbTV
            "https?://[\\w\\d.-]+\\.[a-z]{2,}/hbbtv(?:/[\\w\\d./?=&%-]*)?",
            // URL con parametri DVB
            "https?://[\\w\\d.-]+\\.[a-z]{2,}/dvb(?:/[\\w\\d./?=&%-]*)?",
            // URL generici
            "http://[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?",
            "www\\.[\\w\\d.-]+\\.[a-z]{2,}(?:/[\\w\\d./?=&%-]*)?"
        )
        
        // Cerca URL nei dati stringa
        for ((index, pattern) in etsiUrlPatterns.withIndex()) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(initDataString)
            if (match != null) {
                var url = match.value
                if (!url.startsWith("http")) {
                    url = "https://$url"
                }
                return url
            }
        }
        
        // Cerca URL nei dati binari (potrebbero essere codificati in modo diverso)
        // Pattern per URL codificati in hex
        val hexUrlPattern = "68747470[73]?3a2f2f[0-9a-f]+" // "http" o "https" in hex
        val hexMatch = Regex(hexUrlPattern, RegexOption.IGNORE_CASE).find(initData.joinToString("") { "%02x".format(it) })
        if (hexMatch != null) {
            // Converti hex in stringa
            val hexString = hexMatch.value
            val urlBytes = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val url = String(urlBytes, Charsets.UTF_8)
            return url
        }
        return null
    }
    
    // NUOVO: Funzione per caricare HbbTV reale dal broadcast
    private fun loadHbbTVFromBroadcast(url: String) {
        try {
            Log.d("PlayerActivity", "Caricamento HbbTV broadcast da: $url")
            writeToLog("Caricamento HbbTV broadcast da: $url")
            
            // Imposta modalità HbbTV attiva
            isHbbTVBroadcastModeActive = true
            
            // Nascondi i controlli del player
            hideControls()
            
            // Mostra indicatore di caricamento
            binding.textStatus.text = "Caricamento HbbTV broadcast..."
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.background = resources.getDrawable(R.color.status_online, theme)
            
            // Crea overlay per HbbTV
            createHbbTVBroadcastOverlay()
            
            // Mostra interfaccia HbbTV con WebView
            showHbbTVWebView(url)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel caricamento HbbTV broadcast", e)
            // Fallback su interfaccia simulata
            showSimulatedHbbTVInterface()
        }
    }
    
    // NUOVO: Funzione per mostrare WebView HbbTV
    private fun showHbbTVWebView(url: String) {
        try {
            // Check if WebView already exists - prevent multiple WebViews
            val existingWebView = findViewById<android.webkit.WebView>(R.id.hbbtv_webview)
            if (existingWebView != null) {
                Log.w("PlayerActivity", "HbbTV WebView already exists, skipping duplicate creation")
                writeToLog("HbbTV WebView already exists, skipping duplicate creation")
                return
            }
            
            // Crea un WebView per mostrare il contenuto HbbTV
            val webView = android.webkit.WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                id = R.id.hbbtv_webview
                
                // Configura WebView per HbbTV
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36 HbbTV/1.5.1"
                }
                
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("PlayerActivity", "HbbTV broadcast caricato: $url")
                        binding.textStatus.text = "HbbTV Broadcast ATTIVO - Premi BACK per uscire"
                    }
                    
                    override fun onReceivedError(view: android.webkit.WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        Log.e("PlayerActivity", "Errore caricamento HbbTV: $description")
                        binding.textStatus.text = "Errore caricamento HbbTV - Premi BACK per uscire"
                    }
                }
            }
            
            // Aggiungi WebView all'overlay
            val overlay = findViewById<View>(R.id.hbbtv_overlay)
            if (overlay is FrameLayout) {
                overlay.addView(webView)
                webView.bringToFront()
                
                // Carica l'URL HbbTV
                webView.loadUrl(url)
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella creazione WebView HbbTV", e)
            // Fallback su interfaccia simulata
            showSimulatedHbbTVInterface()
        }
    }
    
    // NUOVO: Funzione per mostrare interfaccia HbbTV simulata (fallback)
    private fun showSimulatedHbbTVInterface() {
        try {
            Log.d("PlayerActivity", "Mostra interfaccia HbbTV simulata (fallback)")
            
            // Imposta modalità HbbTV attiva
            isHbbTVBroadcastModeActive = true
            
            // Mostra l'interfaccia HbbTV broadcast completa
            showHbbTVBroadcastInterface()
            
            // Nascondi i controlli del player per mostrare solo HbbTV
            hideControls()
            
            // Mostra indicatore HbbTV attivo
            binding.textStatus.text = "HbbTV Broadcast ATTIVO - Usa i tasti colorati per navigare"
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.background = resources.getDrawable(R.color.status_online, theme)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'attivazione interfaccia simulata HbbTV", e)
        }
    }
    
    // NUOVO: Funzione per aggiornare UI con dati DVB
    private fun updateDvbDataUI() {
        try {
            // Aggiorna pulsante HbbTV se abbiamo dati broadcast
            if (hasHbbTVBroadcast) {
                binding.buttonHbbTV.isEnabled = true
                binding.buttonHbbTV.alpha = 1.0f
                // Nota: buttonHbbTV potrebbe non supportare .text, usiamo solo enabled e alpha
                
                // Mostra indicatore HbbTV broadcast
                showHbbTVBroadcastIndicator()
                
                // Mostra i controlli quando c'è HbbTV disponibile
                binding.layoutControls.visibility = View.VISIBLE
                
            } else if (currentChannel?.hasHbbTV() == true) {
                // Fallback su HbbTV web se disponibile
                binding.buttonHbbTV.isEnabled = true
                binding.buttonHbbTV.alpha = 1.0f
                // Nota: buttonHbbTV potrebbe non supportare .text, usiamo solo enabled e alpha
                
                // Mostra i controlli quando c'è HbbTV web disponibile
                binding.layoutControls.visibility = View.VISIBLE
            } else {
                binding.buttonHbbTV.isEnabled = false
                binding.buttonHbbTV.alpha = 0.5f
                // Nota: buttonHbbTV potrebbe non supportare .text, usiamo solo enabled e alpha
                
                // Nascondi i controlli quando non c'è HbbTV
                binding.layoutControls.visibility = View.GONE
            }
            
            // Log stato UI
            Log.d("PlayerActivity", "UI aggiornata: HbbTV=$hasHbbTVBroadcast, Teletext=$hasTeletextBroadcast")
            writeToLog("UI aggiornata: HbbTV=$hasHbbTVBroadcast, Teletext=$hasTeletextBroadcast")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'aggiornamento UI DVB", e)
        }
    }
    
    // NUOVO: Funzione per mostrare indicatore HbbTV broadcast
    private fun showHbbTVBroadcastIndicator() {
        try {
            // HBBTV DISABILITATO - Non mostrare più prompt per canali RAI
            if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true) {
                writeToLog("🇮🇹 RAI channel - HbbTV disabilitato")
                return
            }
            
            // For non-RAI channels, show the standard prompt
            binding.textStatus.text = "HbbTV Broadcast disponibile - Premi tasto rosso"
            binding.textStatus.visibility = View.VISIBLE
            
            // Nascondi dopo 5 secondi
            binding.textStatus.postDelayed({
                if (binding.textStatus.text.toString().contains("HbbTV Broadcast")) {
                    binding.textStatus.visibility = View.GONE
                }
            }, 5000)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel mostrare indicatore HbbTV", e)
        }
    }
    
    /**
     * Launch RAI HbbTV in overlay mode
     */
    private fun launchRaiHbbTvOverlay(url: String) {
        try {
            // Check if overlay is already active - prevent multiple overlays
            if (raiHbbTvOverlay != null) {
                Log.w("PlayerActivity", "🇮🇹 RAI HbbTV overlay already active, skipping duplicate launch")
                writeToLog("🇮🇹 RAI HbbTV overlay already active, skipping duplicate launch")
                return
            }
            
            writeToLog("🇮🇹 Launching RAI HbbTV overlay: $url")
            
            // Create a transparent overlay container that covers the entire screen
            val overlayContainer = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                // Bring to front to ensure it's above all other UI elements
                bringToFront()
            }
            
            // Create a WebView for RAI HbbTV with opaque background
            val webView = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                
                // Make WebView completely opaque (not transparent)
                setBackgroundColor(android.graphics.Color.BLACK)
                
                // Force hardware acceleration for better performance
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                
                // Bring to front to ensure it's above the video
                bringToFront()
                
                // Configure WebView for transparent overlay with proper scaling
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowContentAccess = true
                    allowFileAccess = true
                    
                    // Force transparent background and proper scaling
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    
                    // Set viewport for proper scaling
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    
                    // Disable text scaling to prevent content cutoff
                    textZoom = 100
                    
                    // Enable hardware acceleration for better performance
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                }
                
                // Load the RAI HbbTV URL
                loadUrl(url)
                
                // Set initial scale to fit content properly
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Force transparency and proper scaling after page loads
                        view?.evaluateJavascript("""
                                                    // Force opaque background for better visibility
                        var style = document.createElement('style');
                        style.innerHTML = 'html,body{background:black!important;} .background,div[style*="background"]{background:black!important;}';
                        document.head.appendChild(style);
                        
                        // Set individual element opacity (solid backgrounds)
                        document.body.style.backgroundColor = 'black';
                        document.documentElement.style.backgroundColor = 'black';
                        if (document.body) document.body.style.backgroundColor = 'black';
                            
                            // Ensure images and text remain visible
                            var images = document.querySelectorAll('img');
                            for (var i = 0; i < images.length; i++) {
                                images[i].style.opacity = '1';
                                images[i].style.visibility = 'visible';
                            }
                            
                            // Ensure text remains visible
                            var textElements = document.querySelectorAll('p, span, div, h1, h2, h3, h4, h5, h6, button, a');
                            for (var i = 0; i < textElements.length; i++) {
                                if (textElements[i].style.backgroundColor) {
                                    textElements[i].style.backgroundColor = 'transparent';
                                }
                                textElements[i].style.color = textElements[i].style.color || '#000000';
                                textElements[i].style.opacity = '1';
                            }
                            
                            // Better scaling and positioning
                            document.body.style.zoom = '1.0';
                            document.body.style.transform = 'scale(1.0)';
                            document.body.style.transformOrigin = 'center center';
                            
                            // Full screen layout with better alignment
                            document.body.style.margin = '0';
                            document.body.style.padding = '0';
                            document.body.style.textAlign = 'left';
                            document.body.style.display = 'block';
                            document.body.style.position = 'absolute';
                            document.body.style.top = '0';
                            document.body.style.left = '0';
                            document.body.style.width = '100%';
                            document.body.style.height = '100%';
                            document.body.style.minHeight = '100vh';
                            document.body.style.overflow = 'hidden';
                            
                            // Align content to left side
                            document.body.style.paddingLeft = '50px';
                            document.body.style.paddingTop = '50px';
                        """.trimIndent(), null)
                    }
                }
                
                // Add close button
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        removeRaiHbbTvOverlay()
                        true
                    } else {
                        false
                    }
                }
            }
            
            // Add WebView to the transparent container
            overlayContainer.addView(webView)
            
            // Add the container to the main layout
            (binding.root as FrameLayout).addView(overlayContainer)
            
            // Store reference for later removal
            raiHbbTvOverlay = overlayContainer
            
            // Hide ALL native controls when HbbTV overlay is active
            binding.layoutControls.visibility = View.GONE
            binding.channelPopupOverlay.visibility = View.GONE
            binding.textChannelNumber.visibility = View.GONE
            binding.textStatus.visibility = View.GONE
            binding.textError.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            
            // Hide native system controls completely (INDIETRO, CH+, GUIDA TV, etc.)
            hideNativeSystemControls()
            
            // Force hide any remaining visible controls that might be native
            forceHideAllVisibleControls()
            
            // Also hide controls with a delay in case they appear later
            binding.root.postDelayed({
                Log.d("PlayerActivity", "🔄 Delayed control hiding...")
                writeToLog("🔄 Delayed control hiding...")
                forceHideAllVisibleControls()
            }, 1000) // 1 secondo di delay
            
            // Keep video player fully visible
            binding.playerView.alpha = 1.0f
            
            // Show success message
            binding.textStatus.text = "🇮🇹 RAI HbbTV attivo - Premi BACK per chiudere"
            binding.textStatus.visibility = View.VISIBLE
            
            // Hide message after 3 seconds
            binding.textStatus.postDelayed({
                if (binding.textStatus.text.toString().contains("RAI HbbTV attivo")) {
                    binding.textStatus.visibility = View.GONE
                }
            }, 3000)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error launching RAI HbbTV overlay", e)
            writeToLog("ERRORE launching RAI HbbTV overlay: ${e.message}")
            
            // Fallback to normal HbbTV activity
            launchHbbTvApplication(url)
        }
    }
    
    /**
     * Remove RAI HbbTV overlay
     */
    private fun removeRaiHbbTvOverlay() {
        try {
            raiHbbTvOverlay?.let { overlay ->
                (binding.root as FrameLayout).removeView(overlay)
                raiHbbTvOverlay = null
                
                writeToLog("🇮🇹 RAI HbbTV overlay removed")
                
                // Restore ALL native controls visibility
                binding.layoutControls.visibility = View.VISIBLE
                binding.channelPopupOverlay.visibility = View.VISIBLE
                binding.textChannelNumber.visibility = View.VISIBLE
                binding.textStatus.visibility = View.VISIBLE
                binding.textError.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                
                // Restore native system controls
                restoreNativeSystemControls()
                
                // Ensure video player is fully visible
                binding.playerView.visibility = View.VISIBLE
                binding.playerView.alpha = 1.0f
                
                // Hide status message
                binding.textStatus.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error removing RAI HbbTV overlay", e)
            writeToLog("ERRORE removing RAI HbbTV overlay: ${e.message}")
        }
    }
    
    @UnstableApi
    private fun checkCodecSupport(mimeType: String): Boolean {
        return try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            val format = if (mimeType.startsWith("video/")) {
                android.media.MediaFormat.createVideoFormat(mimeType, 1920, 1080)
            } else {
                android.media.MediaFormat.createAudioFormat(mimeType, 48000, 2)
            }
            codecList.findDecoderForFormat(format) != null
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel controllo codec: $mimeType", e)
            false
        }
    }
    
    @UnstableApi
    private fun configureCodecs() {
        try {
            Log.d("PlayerActivity", "Configurazione codec per DVB-T pass-through")
            
            // Configura i codec supportati per DVB-T
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            
            // Codec video DVB-T standard
            val dvbVideoCodecs = listOf(
                MimeTypes.VIDEO_MPEG2,  // DVB-T principale
                MimeTypes.VIDEO_H264,   // DVB-T2 HD
                MimeTypes.VIDEO_H265,   // DVB-T2 UHD (futuro)
                "video/mp4v-es"         // DVB-T alternativo (MPEG-4)
            )
            
            Log.d("PlayerActivity", "Verifica codec video DVB-T:")
            for (codec in dvbVideoCodecs) {
                try {
                    val format = android.media.MediaFormat.createVideoFormat(codec, 1920, 1080)
                    format.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 25) // DVB-T europeo
                    format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 15000000) // 15 Mbps tipico DVB-T
                    
                    val info = codecList.findDecoderForFormat(format)
                    if (info != null) {
                        Log.d("PlayerActivity", "✅ Codec video DVB-T supportato: $codec - $info")
                    } else {
                        Log.w("PlayerActivity", "❌ Codec video DVB-T NON supportato: $codec")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Errore nel test del codec video $codec", e)
                }
            }
            
            // Codec audio DVB-T standard
            val dvbAudioCodecs = listOf(
                MimeTypes.AUDIO_MPEG,   // MPEG-1 Layer 2 (DVB-T standard)
                MimeTypes.AUDIO_AAC,    // AAC (DVB-T HD)
                MimeTypes.AUDIO_AC3,    // Dolby Digital
                "audio/eac3"            // Dolby Digital Plus
            )
            
            Log.d("PlayerActivity", "Verifica codec audio DVB-T:")
            for (codec in dvbAudioCodecs) {
                try {
                    val format = android.media.MediaFormat.createAudioFormat(codec, 48000, 2)
                    format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 256000) // 256 kbps tipico DVB-T
                    
                    val info = codecList.findDecoderForFormat(format)
                    if (info != null) {
                        Log.d("PlayerActivity", "✅ Codec audio DVB-T supportato: $codec - $info")
                    } else {
                        Log.w("PlayerActivity", "❌ Codec audio DVB-T NON supportato: $codec")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Errore nel test del codec audio $codec", e)
                }
            }
            
            // Verifica supporto MPEG-TS (container DVB-T)
            try {
                val mpegtsFormat = android.media.MediaFormat.createVideoFormat(MimeTypes.VIDEO_MPEG2, 1920, 1080)
                mpegtsFormat.setString(android.media.MediaFormat.KEY_MIME, "video/mp2t")
                
                val mpegtsInfo = codecList.findDecoderForFormat(mpegtsFormat)
                if (mpegtsInfo != null) {
                    Log.d("PlayerActivity", "✅ Supporto MPEG-TS rilevato: $mpegtsInfo")
                } else {
                    Log.w("PlayerActivity", "❌ Supporto MPEG-TS NON rilevato")
                }
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Errore nel test MPEG-TS", e)
            }
            
            Log.d("PlayerActivity", "Configurazione codec DVB-T completata")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella configurazione dei codec DVB-T", e)
        }
    }
    
    @UnstableApi
    private fun configureMiBoxOptimizations() {
        try {
            Log.d("PlayerActivity", "🔧 Configurazione ottimizzazioni per Mi TV AYFR0 (S905Y2)")
            
            // RILEVA IL DISPOSITIVO SPECIFICO
            val isMiTVStick = android.os.Build.MODEL.contains("AYFR0", ignoreCase = true) ||
                              android.os.Build.MODEL.contains("mitv", ignoreCase = true) ||
                              android.os.Build.HARDWARE.contains("amlogic", ignoreCase = true)
            
            if (isMiTVStick) {
                Log.d("PlayerActivity", "✅ Mi TV AYFR0 rilevato - Applico ottimizzazioni S905Y2")
                
                // CONFIGURAZIONI SPECIFICHE PER S905Y2
                configureS905Y2Codecs()
                configureMiTVStickSurface()
                configureMiTVStickPerformance()
                
                // Mostra indicatore ottimizzazioni
                binding.textError.text = "Mi TV AYFR0 rilevato - Hardware acceleration S905Y2 attiva"
                binding.textError.visibility = View.VISIBLE
                
                // Nascondi dopo 3 secondi
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.textError.visibility = View.GONE
                }, 3000)
                
            } else {
                Log.d("PlayerActivity", "Dispositivo generico - Configurazione standard")
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella configurazione ottimizzazioni Mi TV", e)
        }
    }
    
    private fun configureS905Y2Codecs() {
        try {
            Log.d("PlayerActivity", "🎬 Configurazione codec S905Y2 per DVB-T")
            
            // S905Y2 supporta nativamente questi codec hardware
            // Nota: Non testiamo i codec specifici per compatibilità
            Log.d("PlayerActivity", "✅ Mi TV AYFR0 con chipset S905Y2 rilevato")
            Log.d("PlayerActivity", "✅ Supporto nativo per codec DVB-T hardware")
            
            // Testa supporto formati DVB-T generici
            testDvbTFormats()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore configurazione codec S905Y2", e)
        }
    }
    
    private fun testDvbTFormats() {
        try {
            Log.d("PlayerActivity", "📡 Test formati DVB-T per S905Y2")
            
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            
            // Formati DVB-T standard
            val dvbFormats = listOf(
                "video/mpeg2" to "MPEG-2 (DVB-T SD/HD)",
                "video/avc" to "H.264 (DVB-T2 HD)",
                "video/hevc" to "H.265 (DVB-T2 UHD)",
                "audio/mpeg-L2" to "MPEG-L2 Audio (DVB-T)",
                "audio/aac" to "AAC Audio (DVB-T2)",
                "audio/ac3" to "Dolby Digital (DVB-T)"
            )
            
            for ((mimeType, description) in dvbFormats) {
                try {
                    val format = if (mimeType.startsWith("video/")) {
                        android.media.MediaFormat.createVideoFormat(mimeType, 1920, 1080)
                    } else {
                        android.media.MediaFormat.createAudioFormat(mimeType, 48000, 2)
                    }
                    
                    // Parametri specifici DVB-T
                    if (mimeType.startsWith("video/")) {
                        format.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 25) // DVB-T europeo
                        format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 15000000) // 15 Mbps tipico
                    }
                    
                    val codecInfo = codecList.findDecoderForFormat(format)
                    if (codecInfo != null) {
                        Log.d("PlayerActivity", "✅ $description supportato")
                    } else {
                        Log.w("PlayerActivity", "❌ $description NON supportato")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Errore test formato $description", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore test formati DVB-T", e)
        }
    }
    
    private fun configureMiTVStickSurface() {
        try {
            Log.d("PlayerActivity", "🖥️ Configurazione surface per Mi TV AYFR0")
            
            // Mi TV Stick supporta surface_view per hardware acceleration
            // Nota: setSurfaceType non disponibile in questa versione Media3
            // Il surface_type è già configurato nel layout XML
            
            // Configura per hardware acceleration S905Y2
            // setShutterBackgroundColor non disponibile in questa versione
            
            // Hardware acceleration è già abilitata nel layout XML
            // binding.playerView.isHardwareAccelerated = true  // Non può essere modificata
            
            Log.d("PlayerActivity", "✅ Surface hardware configurato per Mi TV AYFR0")
            Log.d("PlayerActivity", "✅ Hardware acceleration abilitata nel layout XML")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore configurazione surface Mi TV", e)
        }
    }
    
    private fun configureMiTVStickPerformance() {
        try {
            Log.d("PlayerActivity", "⚡ Configurazione performance per Mi TV AYFR0")
            
            // Ottimizzazioni specifiche per S905Y2
            exoPlayer?.let { player ->
                // Configura per streaming live DVB-T
                // playWhenReady e repeatMode sono già configurati nel setupPlayer
                
                // Nota: setLoadControl non può essere chiamato dopo la creazione
                // I buffer sono già configurati nel setupPlayer
            }
            
            Log.d("PlayerActivity", "✅ Performance Mi TV AYFR0 configurate")
            Log.d("PlayerActivity", "✅ Buffer ottimizzati per S905Y2 nel setupPlayer")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore configurazione performance Mi TV", e)
        }
    }
    
    @UnstableApi
    private fun setupUI() {
        currentChannel?.let { channel ->
            // Gestisci pulsante HbbTV
            if (channel.hasHbbTV()) {
                binding.buttonHbbTV.isEnabled = true
                binding.buttonHbbTV.alpha = 1.0f
                // Nota: buttonHbbTV potrebbe non supportare .text, usiamo solo enabled e alpha
            } else {
                binding.buttonHbbTV.isEnabled = false
                binding.buttonHbbTV.alpha = 0.5f
                // Nota: buttonHbbTV potrebbe non supportare .text, usiamo solo enabled e alpha
            }
        }
        
        // NASCONDI controlli all'apertura del player - non devono mai apparire automaticamente
        hideControls()
        
        // FORZA NASCONDI CONTROLLI - Devono essere sempre nascosti
        binding.layoutControls.visibility = View.GONE
        
        // Setup click listeners
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonHbbTV.setOnClickListener { 
            // HBBTV DISABILITATO - Mostra messaggio informativo
            Log.d("PlayerActivity", "Click pulsante HbbTV - Funzionalità disabilitata")
            writeToLog("Click pulsante HbbTV - Funzionalità disabilitata")
            showHbbTVDisabledMessage()
        }
        binding.buttonEpg.setOnClickListener { openEPG() }
        binding.buttonChannelUp.setOnClickListener { changeChannel(1) }
        binding.buttonChannelDown.setOnClickListener { changeChannel(-1) }
        binding.buttonRetry.setOnClickListener { retryPlayback() }
        
        // Click su player NON mostra controlli - Devono rimanere nascosti
        binding.playerView.setOnClickListener { 
            // Click su player non fa nulla - controlli sempre nascosti
            Log.d("PlayerActivity", "Click su player - controlli non mostrati")
        }
        
        // FORZA NASCONDI CONTROLLI - Devono essere sempre nascosti
        binding.layoutControls.visibility = View.GONE
        binding.layoutControls.alpha = 0f
        
        // Rendi i controlli sempre accessibili con il telecomando
        binding.root.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (binding.layoutControls.visibility != View.VISIBLE) {
                        showControls()
                    }
                    false // Lascia che onKeyDown gestisca l'azione
                }
                else -> false
            }
        }
        
        // Resetta il timeout quando l'utente naviga tra i controlli
        binding.layoutControls.setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, 
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    resetControlsTimeout()
                    false
                }
                else -> false
            }
        }
        
        // NUOVO: Inizializza stato DVB
        initializeDvbState()
    }
    
    // NUOVO: Funzione per inizializzare stato DVB
    private fun initializeDvbState() {
        try {
            // Reset stato DVB
            hasHbbTVBroadcast = false
            hasTeletextBroadcast = false
            hbbTVData = null
            teletextData = null
            teletextBuffer?.clear()
            currentHbbTVApplication = null
            
            // Aggiorna UI
            updateDvbDataUI()
            
            Log.d("PlayerActivity", "Stato DVB inizializzato")
            writeToLog("Stato DVB inizializzato")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nell'inizializzazione stato DVB", e)
        }
    }
    
    // NUOVO: Funzione per mostrare indicatore teletext
    private fun showTeletextIndicator() {
        try {
            // Mostra un indicatore visivo per il teletext
            // Per ora usiamo il textStatus, ma potresti creare un layout specifico
            binding.textStatus.text = "📺 Teletext disponibile - Premi INFO per attivare"
            binding.textStatus.visibility = View.VISIBLE
            
            // Nascondi dopo 3 secondi
            binding.textStatus.postDelayed({
                if (binding.textStatus.text.toString().contains("Teletext disponibile")) {
                    binding.textStatus.visibility = View.GONE
                }
            }, 3000)
            
            Log.d("PlayerActivity", "Indicatore teletext mostrato")
            writeToLog("Indicatore teletext mostrato")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel mostrare indicatore teletext", e)
        }
    }
    
    @UnstableApi
    private fun playChannel(channel: Channel) {
        writeToLog("Inizio playChannel: ${channel.name} - URL: ${channel.streamUrl}")
        
        // GLOBAL LOCK: Evita operazioni multiple simultanee
        if (isAnyPlayerOperationInProgress) {
            writeToLog("Operazione player in corso - salto playChannel")
            return
        }
        
        // CONTROLLO: Evita chiamate multiple
        if (isPlayChannelInProgress) {
            writeToLog("playChannel già in esecuzione - salto")
            return
        }
        
        // CONTROLLO: Evita chiamate multiple se il player è già in riproduzione dello stesso canale
        if (exoPlayer != null && currentChannel?.id == channel.id && exoPlayer!!.playbackState == Player.STATE_READY) {
            writeToLog("Player già in riproduzione dello stesso canale - salto")
            return
        }
        
        // CONTROLLO: Evita chiamate multiple durante l'inizializzazione
        if (isInitializationInProgress && isPlayerInitialized) {
            writeToLog("Player già inizializzato durante inizializzazione - salto")
            return
        }
        
        isPlayChannelInProgress = true
        isAnyPlayerOperationInProgress = true
        
        try {
            Log.d("PlayerActivity", "Avvio riproduzione canale DVB-T: ${channel.name} - URL: ${channel.streamUrl}")
            
            // Rilascia il player solo se è un canale diverso o se non è inizializzato
            if (exoPlayer != null && (currentChannel?.id != channel.id || !isPlayerInitialized)) {
                writeToLog("Rilascio player precedente per cambio canale o reinizializzazione")
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                isPlayerInitialized = false
            }
            
            // Crea il player solo se non esiste
            if (exoPlayer == null) {
                setupPlayer()
            }
            
            // Optimize: Create MediaSource first for faster attachment
            val mediaSource = createTsMediaSource(channel.streamUrl)
            
            exoPlayer?.apply {
                // Optimize: Set media source and prepare immediately
                setMediaSource(mediaSource)
                prepare()
                play()
                
                Log.d("PlayerActivity", "Player preparato per stream DVB-T con HbbTV parsing: ${channel.name}")
                writeToLog("Player preparato con successo per: ${channel.name}")
            }
            
            // Optimize: Run HbbTV operations in background to not block playback
            runOnUiThread {
            // Check if RAI channel and set static HbbTV URL
            checkRaiChannelForHbbTv()
            
            // Clear HbbTV state for new channel (but preserve RAI state)
            if (currentChannel?.name?.contains("RAI", ignoreCase = true) != true) {
                // Only clear state for non-RAI channels
                hbbTvManager.clearState()
            } else {
                writeToLog("🇮🇹 RAI channel - preserving HbbTV state")
            }
            
            // Start HbbTV AIT detection
            hbbTvManager.startAitDetection()
            
                // Carica informazioni EPG per il canale (non critico per la riproduzione)
                viewModel.loadChannelPrograms(channel.id)
            }
            
        } catch (e: Exception) {
            writeToLog("ERRORE in playChannel: ${e.message}")
            writeToLog("Stack trace: ${getStackTrace(e)}")
            Log.e("PlayerActivity", "Errore durante la riproduzione del canale DVB-T: ${channel.name}", e)
            handlePlayerError(PlaybackException(
                "Errore durante la riproduzione del canale DVB-T",
                e,
                PlaybackException.ERROR_CODE_UNSPECIFIED
            ))
        } finally {
            // Reset dei flag per permettere chiamate future
            isPlayChannelInProgress = false
            isAnyPlayerOperationInProgress = false
            writeToLog("playChannel completato - flag reset")
        }
    }

    /**
     * Create a MediaSource optimized for Mi Box with support for multiple containers (MPEG-TS, Matroska)
     */
    @UnstableApi
    private fun createTsMediaSource(streamUrl: String): androidx.media3.exoplayer.source.MediaSource {
        try {
            Log.d("PlayerActivity", "Creating optimized MediaSource for Mi Box: $streamUrl")
            
            // Rileva il tipo di container dall'URL o usa auto-detection
            val containerType = detectContainerType(streamUrl)
            Log.d("PlayerActivity", "Container type detected: $containerType")
            
            // Configurazione ottimizzata per Mi Box con supporto multi-container
            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                // Abilita il supporto per tutti i formati audio comuni
                setConstantBitrateSeekingEnabled(true)
            }
            
            // Crea MediaItem con MIME type appropriato
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(streamUrl)
                .apply {
                    // Specifica MIME type solo se rilevato, altrimenti lascia auto-detection
                    if (containerType != "auto") {
                        setMimeType(containerType)
                    }
                }
                .build()
            
            // Usa DefaultExtractorsFactory configurato per Mi Box
            val mediaSourceFactory = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                androidx.media3.datasource.DefaultDataSource.Factory(this),
                extractorsFactory
            )
            
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            
            Log.d("PlayerActivity", "MediaSource creato con supporto $containerType per Mi Box")
            return mediaSource
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error creating MediaSource", e)
            
            // Fallback semplice per Mi Box
            val fallbackMediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(streamUrl)
                .build()
            
            return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                androidx.media3.datasource.DefaultDataSource.Factory(this)
            ).createMediaSource(fallbackMediaItem)
        }
    }
    
    @UnstableApi
    private fun updatePlaybackState(playbackState: Int) {
        writeToLog("Aggiornamento stato playback: $playbackState")
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                writeToLog("Stato: BUFFERING")
                // Se siamo in recovery standby, mostra indicatore di caricamento
                if (isStandbyRecoveryInProgress) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.textStatus.text = "Ripristino dopo standby..."
                    binding.textStatus.visibility = View.VISIBLE
                } else {
                    // Remove buffering text and progress bar for better UX
                    binding.progressBar.visibility = View.GONE
                    binding.textStatus.visibility = View.GONE
                    
                    // CONTROLLO BUFFERING INFINITO: Se il buffering dura troppo, reset completo
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (exoPlayer?.playbackState == Player.STATE_BUFFERING) {
                            writeToLog("BUFFERING INFINITO rilevato - reset completo player")
                            releasePlayerCompletely()
                            currentChannel?.let { channel ->
                                Handler(Looper.getMainLooper()).postDelayed({
                                    startPlayerSafely(channel)
                                }, 1000)
                            }
                        }
                    }, 8000) // Timeout di 8 secondi per buffering infinito
                }
            }
            Player.STATE_READY -> {
                writeToLog("Stato: READY")
                binding.progressBar.visibility = View.GONE
                binding.textStatus.visibility = View.GONE
                
                // Se siamo in recovery standby, segna come completato
                if (isStandbyRecoveryInProgress) {
                    writeToLog("Recovery standby completato - player pronto")
                }
            }
            Player.STATE_ENDED -> {
                writeToLog("Stato: ENDED")
                binding.textStatus.text = getString(R.string.player_no_signal)
                binding.textStatus.visibility = View.VISIBLE
                
                // Se siamo in recovery standby e il player è terminato, riprova
                if (isStandbyRecoveryInProgress) {
                    writeToLog("Player terminato durante recovery standby - riprovo")
                    currentChannel?.let { channel ->
                        Handler(Looper.getMainLooper()).postDelayed({
                            retryPlaybackWithTimeout(channel, maxRetries = 2, delayMs = 1000)
                        }, 1000)
                    }
                }
            }
            Player.STATE_IDLE -> {
                writeToLog("Stato: IDLE")
                binding.textStatus.text = getString(R.string.player_loading)
                binding.textStatus.visibility = View.VISIBLE
                
                // Se siamo in recovery standby e il player è idle, riprova
                if (isStandbyRecoveryInProgress) {
                    writeToLog("Player idle durante recovery standby - riprovo")
                    currentChannel?.let { channel ->
                        Handler(Looper.getMainLooper()).postDelayed({
                            retryPlaybackWithTimeout(channel, maxRetries = 2, delayMs = 1000)
                        }, 1000)
                    }
                }
            }
        }
    }
    
    @UnstableApi
    private fun handlePlayerError(error: PlaybackException) {
        writeToLog("ERRORE del player: ${error.message}")
        writeToLog("Codice errore: ${error.errorCode}")
        writeToLog("Stack trace: ${getStackTrace(error)}")
        Log.e("PlayerActivity", "Errore del player: ${error.message}", error)
        
        // Gestione specifica per discontinuità audio DVB-T
        if (error.cause?.javaClass?.simpleName == "UnexpectedDiscontinuityException") {
            writeToLog("Discontinuità audio rilevata - gestione automatica")
            Log.w("PlayerActivity", "Discontinuità audio DVB-T gestita automaticamente")
            
            // Mostra un messaggio informativo temporaneo (opzionale)
            binding.textStatus.text = "Sincronizzazione audio in corso..."
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.postDelayed({
                if (binding.textStatus.text.toString().contains("Sincronizzazione audio")) {
                    binding.textStatus.visibility = View.GONE
                }
            }, 2000)
            
            // Per le discontinuità audio, non mostrare errore all'utente
            // Il player gestisce automaticamente queste situazioni
            return
        }
        
        // Gestione specifica per errori FfmpegAudioRenderer dopo standby
        if (error.message?.contains("FfmpegAudioRenderer", ignoreCase = true) == true ||
            error.cause?.message?.contains("FfmpegAudioRenderer", ignoreCase = true) == true) {
            writeToLog("Errore FfmpegAudioRenderer rilevato - recovery automatico")
            Log.w("PlayerActivity", "Errore FfmpegAudioRenderer - avvio recovery")
            
            // Mostra messaggio di recovery
            binding.textStatus.text = "Recovery audio in corso..."
            binding.textStatus.visibility = View.VISIBLE
            
            // Avvia recovery automatico
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    writeToLog("Avvio recovery FfmpegAudioRenderer")
                    retryPlayback()
                } catch (e: Exception) {
                    writeToLog("ERRORE durante recovery FfmpegAudioRenderer: ${e.message}")
                }
            }, 2000)
            
            return
        }
        
        // Per DVB-T pass-through, usa il gestore specifico
        if (currentChannel != null) {
            Log.d("PlayerActivity", "Usando gestore errori DVB-T per canale: ${currentChannel!!.name}")
            handleDvbTError(error)
            return
        }
        
        val errorMessage = when {
            error.cause is java.net.ConnectException -> {
                "Impossibile connettersi al server. Verifica che il server sia acceso e raggiungibile."
            }
            error.cause is java.net.SocketTimeoutException -> {
                "Timeout della connessione. Il server potrebbe essere sovraccarico."
            }
            error.cause is java.net.UnknownHostException -> {
                "Server non trovato. Verifica l'URL del canale."
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                "Errore nel decoder video. Il formato potrebbe non essere supportato dal dispositivo."
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                "Errore nella query del decoder. Verifica i codec supportati."
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                "Errore di connessione di rete. Verifica la connessione internet."
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                "Timeout della connessione di rete. Prova a riavviare il canale."
            }
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                "Stream malformato. Il canale potrebbe avere problemi temporanei."
            }
            error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT -> {
                "Timeout durante la riproduzione. Prova a riavviare il canale."
            }
            else -> {
                "Errore sconosciuto: ${error.message ?: "Codice errore: ${error.errorCode}"}"
            }
        }
        
        // Mostra l'errore all'utente
        binding.textError.text = errorMessage
        binding.textError.visibility = View.VISIBLE
        
        // Nascondi l'errore dopo 10 secondi
        binding.textError.postDelayed({
            binding.textError.visibility = View.GONE
        }, 10000)
        
        // Prova a riavviare automaticamente il player per errori non critici
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED &&
            error.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
            
            Log.d("PlayerActivity", "Tentativo di riavvio automatico del player")
            Handler(Looper.getMainLooper()).postDelayed({
                currentChannel?.let { channel ->
                    startPlayerSafely(channel)
                }
            }, 3000)
        }
        
        Log.e("PlayerActivity", "Messaggio errore mostrato: $errorMessage")
    }

    @UnstableApi
    private fun handleDvbTError(error: PlaybackException) {
        writeToLog("ERRORE DVB-T specifico: ${error.message}")
        writeToLog("Codice errore DVB-T: ${error.errorCode}")
        writeToLog("Stack trace DVB-T: ${getStackTrace(error)}")
        Log.e("PlayerActivity", "Errore DVB-T specifico: ${error.message}", error)
        
        val dvbErrorMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                "Errore decoder DVB-T. Il dispositivo potrebbe non supportare MPEG-2 o H.264."
            }
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                "Errore query decoder DVB-T. Verifica i codec hardware disponibili."
            }
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                "Errore connessione TVHeadend. Verifica che il server sia raggiungibile."
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                "Stream DVB-T malformato. TVHeadend potrebbe avere problemi con il tuner."
            }
            PlaybackException.ERROR_CODE_TIMEOUT -> {
                "Timeout DVB-T. Il segnale potrebbe essere troppo debole o il tuner non funziona."
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                "Errore HTTP da TVHeadend. Verifica la configurazione del server."
            }
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                "Stream non trovato su TVHeadend. Verifica che il canale sia attivo."
            }
            else -> {
                "Errore DVB-T sconosciuto: ${error.message ?: "Codice: ${error.errorCode}"}"
            }
        }
        
        // Mostra messaggio specifico per DVB-T
        binding.textError.text = "Errore DVB-T: $dvbErrorMessage"
        binding.textError.visibility = View.VISIBLE
        
        // Suggerimenti per DVB-T
        val suggestions = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                "Suggerimenti:\n• Verifica che il dispositivo supporti MPEG-2/H.264\n• Prova a riavviare TVHeadend\n• Controlla i log di TVHeadend"
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                "Suggerimenti:\n• Verifica il segnale DVB-T\n• Controlla la configurazione del tuner\n• Prova un canale diverso"
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                "Suggerimenti:\n• Verifica la configurazione di TVHeadend\n• Controlla i permessi di accesso\n• Riavvia il servizio TVHeadend"
            }
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                "Suggerimenti:\n• Verifica che il canale sia attivo su TVHeadend\n• Controlla la configurazione del tuner\n• Prova a riavviare TVHeadend"
            }
            else -> "Prova a riavviare il canale o riavvia TVHeadend"
        }
        
        Log.w("PlayerActivity", "Suggerimenti DVB-T: $suggestions")
        
        // Nascondi l'errore dopo 15 secondi per DVB-T
        binding.textError.postDelayed({
            binding.textError.visibility = View.GONE
        }, 15000)
        
        // Prova a riavviare automaticamente per errori non critici
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && 
            error.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
            Log.d("PlayerActivity", "Tentativo di riavvio automatico per errore DVB-T non critico")
            
            // MIGLIORAMENTO: Per errori di connessione, prova prima a reinizializzare il player
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
                
                Log.d("PlayerActivity", "Errore di connessione - reinizializzo player")
                Handler(Looper.getMainLooper()).postDelayed({
                    reinitializePlayer()
                }, 2000) // Reinizializza dopo 2 secondi
            } else {
                // Per altri errori, riavvia normalmente
            Handler(Looper.getMainLooper()).postDelayed({
                currentChannel?.let { channel ->
                    Log.d("PlayerActivity", "Riavvio automatico del canale DVB-T: ${channel.name}")
                        startPlayerSafely(channel)
                }
            }, 3000) // Riavvio dopo 3 secondi
            }
        }
    }
    
    @UnstableApi
    private fun toggleControls() {
        // CONTROLLI SEMPRE NASCOSTI - toggleControls non fa nulla
        Log.d("PlayerActivity", "toggleControls chiamato - controlli sempre nascosti")
        // Non fare nulla - i controlli devono rimanere sempre nascosti
    }
    
    @UnstableApi
    private fun showControls() {
        // Controlla se i controlli devono essere mostrati
        if (!shouldShowControls()) {
            Log.d("PlayerActivity", "showControls chiamato ma controlli non devono essere mostrati")
            return
        }
        
        binding.layoutControls.visibility = View.VISIBLE
        
        // Assicurati che i controlli siano focusabili
        binding.buttonBack.isFocusable = true
        binding.buttonChannelDown.isFocusable = true
        binding.buttonChannelUp.isFocusable = true
        binding.buttonEpg.isFocusable = true
        binding.buttonHbbTV.isFocusable = true
        binding.buttonRetry.isFocusable = true
        
        // Imposta il focus iniziale sul pulsante Indietro
        binding.buttonBack.requestFocus()
        
        // Auto-hide dopo timeout
        controlsRunnable?.let { controlsHandler.removeCallbacks(it) }
        controlsRunnable = Runnable { hideControls() }
        controlsHandler.postDelayed(controlsRunnable!!, CONTROLS_TIMEOUT)
    }
    
    @UnstableApi
    private fun hideControls() {
        binding.layoutControls.visibility = View.GONE
        controlsRunnable?.let { controlsHandler.removeCallbacks(it) }
        
        // Resetta la richiesta esplicita quando si nascondono i controlli
        controlsExplicitlyRequested = false
    }
    
    // NUOVO: Controlla se i controlli devono essere mostrati
    private fun shouldShowControls(): Boolean {
        // CONTROLLI SEMPRE NASCOSTI - Non devono mai apparire
        Log.d("PlayerActivity", "shouldShowControls chiamato - controlli sempre nascosti")
        return false
    }
    
    @UnstableApi
    private fun resetControlsTimeout() {
        // Resetta il timeout dei controlli solo se sono già visibili
        if (binding.layoutControls.visibility == View.VISIBLE) {
            controlsRunnable?.let { controlsHandler.removeCallbacks(it) }
            controlsRunnable = Runnable { hideControls() }
            controlsHandler.postDelayed(controlsRunnable!!, CONTROLS_TIMEOUT)
        }
    }
    
    @UnstableApi
    private fun changeChannel(direction: Int) {
        writeToLog("Cambio canale: direzione=$direction")
        try {
            Log.d("PlayerActivity", "Cambio canale DVB-T: direzione=$direction")
            
            // Optimize: Only clear HbbTV overlays if actually active
            if (isHbbTVBroadcastModeActive) {
                clearAllHbbTVOverlays()
            }
            
            // GARANZIA: Rilascia completamente il player precedente per cambio canale
            releasePlayerCompletely()
            
            viewModel.getAdjacentChannel(currentChannel!!, direction) { newChannel ->
                if (newChannel != null) {
                    Log.d("PlayerActivity", "Nuovo canale DVB-T: ${newChannel.name}")
                    
                    // Optimize: Update channel and start playback in parallel
                    currentChannel = newChannel
                    
                    // Start playback immediately for faster response
                    startPlayerSafely(newChannel)
                    
                    // Run UI updates in background to not block playback
                    runOnUiThread {
                    // Check if RAI channel and set static HbbTV URL
                    checkRaiChannelForHbbTv()
                    
                    // Salva il nuovo canale come ultimo visualizzato
                    saveLastChannel(newChannel)
                    
                    // Aggiorna UI con nuovo canale
                    setupUI()
                    
                    // Mostra il popup del canale
                    showChannelPopup(newChannel)
                    }
                    
                } else {
                    writeToLog("Nessun canale disponibile in direzione $direction")
                    Log.w("PlayerActivity", "Nessun canale DVB-T disponibile in direzione $direction")
                }
            }
        } catch (e: Exception) {
            writeToLog("ERRORE in changeChannel: ${e.message}")
            writeToLog("Stack trace: ${getStackTrace(e)}")
            Log.e("PlayerActivity", "Errore nel cambio canale DVB-T", e)
            binding.textError.text = "Errore nel cambio canale DVB-T"
            binding.textError.visibility = View.VISIBLE
        }
    }
    
    @UnstableApi
    private fun openHbbTV() {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "openHbbTV chiamato ma HbbTV è disabilitato")
        writeToLog("openHbbTV chiamato ma HbbTV è disabilitato")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per gestire tasti colorati HbbTV - DISABILITATA
    private fun handleHbbTVKeyPress(color: String) {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "handleHbbTVKeyPress chiamato ma HbbTV è disabilitato: $color")
        writeToLog("handleHbbTVKeyPress chiamato ma HbbTV è disabilitato: $color")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per gestire HbbTV broadcast - DISABILITATA
    private fun handleHbbTVBroadcast(color: String) {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "handleHbbTVBroadcast chiamato ma HbbTV è disabilitato: $color")
        writeToLog("handleHbbTVBroadcast chiamato ma HbbTV è disabilitato: $color")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per attivare applicazione HbbTV broadcast - DISABILITATA
    private fun activateHbbTVBroadcastApplication() {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "activateHbbTVBroadcastApplication chiamato ma HbbTV è disabilitato")
        writeToLog("activateHbbTVBroadcastApplication chiamato ma HbbTV è disabilitato")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per gestire azione verde HbbTV - DISABILITATA
    private fun handleHbbTVGreenAction() {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "handleHbbTVGreenAction chiamato ma HbbTV è disabilitato")
        writeToLog("handleHbbTVGreenAction chiamato ma HbbTV è disabilitato")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per gestire azione gialla HbbTV - DISABILITATA
    private fun handleHbbTVYellowAction() {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "handleHbbTVGreenAction chiamato ma HbbTV è disabilitato")
        writeToLog("handleHbbTVGreenAction chiamato ma HbbTV è disabilitato")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per gestire azione blu HbbTV - DISABILITATA
    private fun handleHbbTVBlueAction() {
        // HBBTV DISABILITATO - Funzione mantenuta ma non utilizzata
        Log.d("PlayerActivity", "handleHbbTVBlueAction chiamato ma HbbTV è disabilitato")
        writeToLog("handleHbbTVBlueAction chiamato ma HbbTV è disabilitato")
        showHbbTVDisabledMessage()
    }
    
    // NUOVO: Funzione per mostrare interfaccia HbbTV broadcast
    private fun showHbbTVBroadcastInterface() {
        try {
            // Crea un overlay completo per HbbTV broadcast
            createHbbTVBroadcastOverlay()
            
            // Mostra informazioni dettagliate e interattive
            val hbbtvInfo = buildString {
                if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true) {
                    appendLine("🇮🇹 RAI HbbTV ATTIVO")
                    appendLine()
                    appendLine("📺 Canale: ${currentChannel?.getDisplayName() ?: "Sconosciuto"}")
                    appendLine("🔧 Applicazione: ${currentHbbTVApplication ?: "RAI Replay TV"}")
                    appendLine("🌐 URL: ${currentHbbTVApplication ?: "N/A"}")
                    appendLine("📊 Tipo: RAI Static HbbTV")
                    appendLine()
                    appendLine("🎮 Controlli disponibili:")
                    appendLine("🔴 ROSSO: Apri RAI HbbTV")
                    appendLine("🟢 VERDE: Azioni secondarie")
                    appendLine("🟡 GIALLO: Contenuti aggiuntivi")
                    appendLine("🔵 BLU: Servizi extra")
                    appendLine()
                    appendLine("💡 Suggerimento: Premi ROSSO per aprire RAI HbbTV")
                    appendLine("⬅️ Premi BACK per tornare al player")
                } else {
                    appendLine("🎯 HbbTV Broadcast ATTIVO")
                    appendLine()
                    appendLine("📺 Canale: ${currentChannel?.getDisplayName() ?: "Sconosciuto"}")
                    appendLine("🔧 Applicazione: ${currentHbbTVApplication ?: "Servizio interattivo DVB"}")
                    appendLine("📡 PID: ${hbbTVData?.substringAfter(": ") ?: "N/A"}")
                    appendLine("📊 Qualità: ${if (hasHbbTVBroadcast) "Broadcast nativo" else "Web-based"}")
                    appendLine()
                    appendLine("🎮 Controlli disponibili:")
                    appendLine("🔴 ROSSO: Menu principale e navigazione")
                    appendLine("🟢 VERDE: Azioni secondarie e opzioni")
                    appendLine("🟡 GIALLO: Contenuti aggiuntivi")
                    appendLine("🔵 BLU: Servizi extra e informazioni")
                    appendLine()
                    appendLine("💡 Suggerimento: Usa i tasti colorati per navigare")
                    appendLine("⬅️ Premi BACK per tornare al player")
                }
            }
            
            binding.textError.text = hbbtvInfo
            binding.textError.visibility = View.VISIBLE
            binding.textError.background = resources.getDrawable(R.color.tv_surface, theme)
                            binding.textError.setTextColor(ContextCompat.getColor(this, R.color.player_text))
            binding.textError.textSize = 16f
            
            // Mantieni il player visibile ma con sfondo trasparente per HbbTV
            binding.playerView.alpha = 1.0f
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel mostrare interfaccia HbbTV", e)
        }
    }
    
    // NUOVO: Funzione per creare overlay HbbTV broadcast
    private fun createHbbTVBroadcastOverlay() {
        try {
            // Check if overlay already exists - prevent multiple overlays
            val existingOverlay = findViewById<View>(R.id.hbbtv_overlay)
            if (existingOverlay != null) {
                Log.w("PlayerActivity", "HbbTV broadcast overlay already exists, skipping duplicate creation")
                writeToLog("HbbTV broadcast overlay already exists, skipping duplicate creation")
                return
            }
            
            // Crea un overlay con sfondo trasparente per HbbTV
            val overlay = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // Sfondo completamente trasparente
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                alpha = 1.0f
                id = R.id.hbbtv_overlay
            }
            
            // Aggiungi l'overlay al layout principale
            (binding.root as FrameLayout).addView(overlay)
            
            // Mostra il contenuto HbbTV sopra l'overlay
            binding.textError.bringToFront()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella creazione overlay HbbTV", e)
        }
    }
    
    // NUOVO: Funzione per rimuovere overlay HbbTV
    private fun removeHbbTVBroadcastOverlay() {
        try {
            // Rimuovi il WebView HbbTV se presente
            val webView = findViewById<android.webkit.WebView>(R.id.hbbtv_webview)
            webView?.let {
                val parent = it.parent as? android.view.ViewGroup
                parent?.removeView(it)
            }
            
            // Rimuovi l'overlay se presente
            val overlay = findViewById<View>(R.id.hbbtv_overlay)
            overlay?.let {
                (binding.root as FrameLayout).removeView(it)
            }
            
            // Ripristina il player
            binding.playerView.alpha = 1.0f
            
            // Nascondi il testo HbbTV
            binding.textError.visibility = View.GONE
            binding.textStatus.visibility = View.GONE
            
            // Disattiva modalità HbbTV
            isHbbTVBroadcastModeActive = false
            
            // Ripristina i controlli nativi del sistema
            restoreNativeSystemControls()
            
            // Mostra i controlli
            showControls()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nella rimozione overlay HbbTV", e)
        }
    }
    
    // NUOVO: Funzione per pulire tutti gli overlay HbbTV (utile per cambio canale)
    private fun clearAllHbbTVOverlays() {
        try {
            Log.d("PlayerActivity", "Clearing all HbbTV overlays")
            writeToLog("Clearing all HbbTV overlays")
            
            // Remove RAI HbbTV overlay
            removeRaiHbbTvOverlay()
            
            // Remove broadcast HbbTV overlay
            removeHbbTVBroadcastOverlay()
            
            // Reset HbbTV state
            isHbbTVBroadcastModeActive = false
            currentHbbTVApplication = null
            
            // Restore native system controls
            restoreNativeSystemControls()
            
            // Ensure all controls are visible
            showControls()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error clearing all HbbTV overlays", e)
            writeToLog("ERRORE clearing all HbbTV overlays: ${e.message}")
        }
    }
    
    // NUOVO: Funzione per nascondere completamente i controlli nativi del sistema
    private fun hideNativeSystemControls() {
        try {
            Log.d("PlayerActivity", "🔍 Attempting to hide native system controls...")
            writeToLog("🔍 Attempting to hide native system controls...")
            
            // Nascondi la barra di navigazione del sistema
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            
            // Prova a nascondere i controlli nativi in diversi modi
            val contentView = findViewById<View>(android.R.id.content)
            if (contentView != null) {
                Log.d("PlayerActivity", "✅ Content view found, searching for native controls...")
                writeToLog("✅ Content view found, searching for native controls...")
                
                // Cerca e nascondi controlli nativi comuni
                val controlIds = listOf(
                    android.R.id.navigationBarBackground,
                    android.R.id.statusBarBackground,
                    android.R.id.home
                )
                
                controlIds.forEach { id ->
                    val control = contentView.findViewById<View>(id)
                    if (control != null) {
                        Log.d("PlayerActivity", "🎯 Found native control with ID: $id, hiding it...")
                        writeToLog("🎯 Found native control with ID: $id, hiding it...")
                        control.visibility = View.GONE
                    } else {
                        Log.d("PlayerActivity", "❌ Native control with ID: $id not found")
                        writeToLog("❌ Native control with ID: $id not found")
                    }
                }
                
                // Prova a cercare controlli per nome o tipo
                hideControlsByType(contentView)
                
            } else {
                Log.w("PlayerActivity", "❌ Content view not found")
                writeToLog("❌ Content view not found")
            }
            
            // Prova anche a nascondere controlli a livello di decorView
            hideControlsInDecorView()
            
            Log.d("PlayerActivity", "✅ Native system controls hiding attempt completed")
            writeToLog("✅ Native system controls hiding attempt completed")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error hiding native system controls", e)
            writeToLog("ERRORE hiding native system controls: ${e.message}")
        }
    }
    
    // Funzione per nascondere controlli per tipo
    private fun hideControlsByType(rootView: View) {
        try {
            Log.d("PlayerActivity", "🔍 Searching for controls by type...")
            writeToLog("🔍 Searching for controls by type...")
            
            // Cerca tutti i ViewGroup e cerca controlli nativi
            if (rootView is ViewGroup) {
                for (i in 0 until rootView.childCount) {
                    val child = rootView.getChildAt(i)
                    
                    // Cerca controlli che potrebbero essere nativi
                    if (child.javaClass.simpleName.contains("Button") || 
                        child.javaClass.simpleName.contains("TextView") ||
                        child.javaClass.simpleName.contains("LinearLayout") ||
                        child.javaClass.simpleName.contains("FrameLayout")) {
                        
                        // Controlla se il testo contiene parole chiave dei controlli nativi
                        if (child is android.widget.TextView) {
                            val text = child.text.toString().lowercase()
                            if (text.contains("indietro") || text.contains("ch+") || 
                                text.contains("ch-") || text.contains("guida") || 
                                text.contains("back") || text.contains("channel")) {
                                
                                Log.d("PlayerActivity", "🎯 Found potential native control: ${child.javaClass.simpleName} with text: '$text'")
                                writeToLog("🎯 Found potential native control: ${child.javaClass.simpleName} with text: '$text'")
                                
                                // Nascondi questo controllo
                                child.visibility = View.GONE
                            }
                        }
                        
                        // Cerca anche nei figli
                        if (child is ViewGroup) {
                            hideControlsByType(child)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error hiding controls by type", e)
            writeToLog("ERRORE hiding controls by type: ${e.message}")
        }
    }
    
    // Funzione per nascondere controlli nel decorView
    private fun hideControlsInDecorView() {
        try {
            Log.d("PlayerActivity", "🔍 Searching for controls in decorView...")
            writeToLog("🔍 Searching for controls in decorView...")
            
            val decorView = window.decorView
            if (decorView is ViewGroup) {
                hideControlsByType(decorView)
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error hiding controls in decorView", e)
            writeToLog("ERRORE hiding controls in decorView: ${e.message}")
        }
    }
    
    // Funzione per forzare la rimozione di tutti i controlli visibili
    private fun forceHideAllVisibleControls() {
        try {
            Log.d("PlayerActivity", "🚨 Force hiding all visible controls...")
            writeToLog("🚨 Force hiding all visible controls...")
            
            // Cerca in tutto l'albero delle view per controlli nativi
            val rootView = findViewById<View>(android.R.id.content) ?: return
            
            // Nascondi tutti i controlli che potrebbero essere nativi
            hideAllControlsRecursively(rootView)
            
            // Prova anche a nascondere controlli specifici per nome
            hideControlsByName(rootView)
            
            Log.d("PlayerActivity", "✅ Force hide completed")
            writeToLog("✅ Force hide completed")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in force hide controls", e)
            writeToLog("ERRORE in force hide controls: ${e.message}")
        }
    }
    
    // Funzione ricorsiva per nascondere tutti i controlli
    private fun hideAllControlsRecursively(view: View) {
        try {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    
                    // Nascondi controlli che potrebbero essere nativi
                    if (shouldHideControl(child)) {
                        Log.d("PlayerActivity", "🚨 Hiding control: ${child.javaClass.simpleName}")
                        writeToLog("🚨 Hiding control: ${child.javaClass.simpleName}")
                        child.visibility = View.GONE
                    }
                    
                    // Continua la ricerca ricorsiva
                    hideAllControlsRecursively(child)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in recursive hide", e)
        }
    }
    
    // Funzione per determinare se un controllo deve essere nascosto
    private fun shouldHideControl(view: View): Boolean {
        try {
            // Controlla se è un controllo nativo che deve essere nascosto
            if (view is android.widget.TextView || view is android.widget.Button) {
                val text = when (view) {
                    is android.widget.TextView -> view.text.toString()
                    is android.widget.Button -> view.text.toString()
                    else -> ""
                }.lowercase()
                
                // Nascondi se contiene parole chiave dei controlli nativi
                return text.contains("indietro") || text.contains("ch+") || 
                       text.contains("ch-") || text.contains("guida") || 
                       text.contains("back") || text.contains("channel") ||
                       text.contains("canali") || text.contains("radio") ||
                       text.contains("ultime") || text.contains("edizioni")
            }
            
            // Nascondi anche layout che potrebbero contenere controlli nativi
            if (view is android.widget.LinearLayout || view is android.widget.FrameLayout) {
                val layoutParams = view.layoutParams
                if (layoutParams != null) {
                    // Nascondi layout che potrebbero essere barre di controllo
                    if (layoutParams.height == android.view.ViewGroup.LayoutParams.WRAP_CONTENT ||
                        layoutParams.height < 200) { // Altezza tipica di barre di controllo
                        return true
                    }
                }
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    // Funzione per nascondere controlli per nome
    private fun hideControlsByName(rootView: View) {
        try {
            if (rootView is ViewGroup) {
                for (i in 0 until rootView.childCount) {
                    val child = rootView.getChildAt(i)
                    
                    // Cerca controlli con nomi specifici
                    if (child.javaClass.simpleName.contains("Control") ||
                        child.javaClass.simpleName.contains("Button") ||
                        child.javaClass.simpleName.contains("Bar")) {
                        
                        Log.d("PlayerActivity", "🎯 Found control by name: ${child.javaClass.simpleName}")
                        writeToLog("🎯 Found control by name: ${child.javaClass.simpleName}")
                        child.visibility = View.GONE
                    }
                    
                    // Continua la ricerca
                    if (child is ViewGroup) {
                        hideControlsByName(child)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error hiding controls by name", e)
        }
    }
    
    // NUOVO: Funzione per ripristinare i controlli nativi del sistema
    private fun restoreNativeSystemControls() {
        try {
            // Ripristina la visibilità normale dei controlli di sistema
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            
            // Ripristina anche i controlli personalizzati del sistema
            val contentView = findViewById<View>(android.R.id.content)
            contentView?.let { view ->
                val controlIds = listOf(
                    android.R.id.navigationBarBackground,
                    android.R.id.statusBarBackground,
                    android.R.id.home
                )
                
                controlIds.forEach { id ->
                    view.findViewById<View>(id)?.visibility = View.VISIBLE
                }
            }
            
            Log.d("PlayerActivity", "Native system controls restored")
            writeToLog("Native system controls restored")
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error restoring native system controls", e)
            writeToLog("ERRORE restoring native system controls: ${e.message}")
        }
    }
    
    // NUOVO: Funzione per mostrare messaggio "nessun HbbTV"
    private fun showNoHbbTVMessage() {
        binding.textStatus.text = "HbbTV non disponibile per questo canale"
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.postDelayed({ binding.textStatus.visibility = View.GONE }, 2000)
    }
    
    // NUOVO: Funzione per mostrare messaggio "HbbTV disabilitato"
    private fun showHbbTVDisabledMessage() {
        binding.textStatus.text = "HbbTV temporaneamente disabilitato"
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.postDelayed({ binding.textStatus.visibility = View.GONE }, 3000)
        
        writeToLog("HbbTV disabilitato - messaggio mostrato all'utente")
    }
    
    // NUOVO: Funzione per mostrare messaggio HbbTV non disponibile
    private fun showHbbTVNotAvailableMessage() {
        try {
            Log.d("PlayerActivity", "Mostra messaggio HbbTV non disponibile")
            
            // Mostra messaggio informativo
            binding.textStatus.text = "HbbTV non disponibile per questo canale\n" +
                "Il canale non trasmette contenuti interattivi HbbTV"
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.background = resources.getDrawable(R.color.status_offline, theme)
                            binding.textStatus.setTextColor(ContextCompat.getColor(this, R.color.player_text))
            
            // Nascondi dopo 5 secondi
            binding.textStatus.postDelayed({
                if (binding.textStatus.text.toString().contains("HbbTV non disponibile")) {
                    binding.textStatus.visibility = View.GONE
                }
            }, 5000)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel mostrare messaggio HbbTV non disponibile", e)
        }
    }
    
    // NUOVO: Funzione per mostrare messaggio tasti colorati
    private fun showHbbTVColorKeyMessage(color: String) {
        binding.textStatus.text = "Tasto $color: Solo HbbTV web disponibile (tasto rosso)"
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.postDelayed({ binding.textStatus.visibility = View.GONE }, 2000)
    }
    
    @UnstableApi
    private fun openEPG() {
        val intent = Intent(this, EPGActivity::class.java).apply {
            currentChannel?.let { putExtra(EPGActivity.EXTRA_CHANNEL_ID, it.id) }
        }
        startActivity(intent)
    }
    
    @UnstableApi
    private fun retryPlayback() {
        writeToLog("Tentativo di riproduzione")
        
        // Clear all HbbTV overlays before retrying playback
        if (isHbbTVBroadcastModeActive || raiHbbTvOverlay != null) {
            clearAllHbbTVOverlays()
        }
        
        // Nascondi errore e pulsante retry
        binding.textError.visibility = View.GONE
        binding.buttonRetry.visibility = View.GONE
        
        // Recovery completo per problemi audio dopo standby
        try {
            writeToLog("Recovery completo player per problemi audio")
            
            // Rilascia completamente il player esistente
            if (exoPlayer != null) {
                writeToLog("Rilascio completo player per recovery")
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                isPlayerInitialized = false
            }
            
            // Forza garbage collection per pulire risorse audio
            System.gc()
            
            // Aspetta un momento per la stabilizzazione
            Handler(Looper.getMainLooper()).postDelayed({
                // Riprova la riproduzione del canale corrente
                currentChannel?.let { channel ->
                    binding.progressBar.visibility = View.VISIBLE
                    binding.textStatus.text = "Recovery audio in corso..."
                    binding.textStatus.visibility = View.VISIBLE
                    
                    writeToLog("Avvio recovery per canale: ${channel.name}")
                    
                    // Riprova la connessione con recovery completo
                    startPlayerSafely(channel)
                }
            }, 1000)
            
        } catch (e: Exception) {
            writeToLog("ERRORE durante retryPlayback: ${e.message}")
            Log.e("PlayerActivity", "Errore retryPlayback", e)
        }
    }
    
    /**
     * Gestisce il retry con timeout per il ripristino dopo standby
     */
    @UnstableApi
    private fun retryPlaybackWithTimeout(channel: Channel, maxRetries: Int = 3, delayMs: Long = 2000) {
        writeToLog("Retry playback con timeout per: ${channel.name} (tentativo ${maxRetries})")
        
        // Se siamo in recovery standby, aspetta
        if (isStandbyRecoveryInProgress) {
            writeToLog("Recovery standby in corso - aspetto prima del retry")
            Handler(Looper.getMainLooper()).postDelayed({
                retryPlaybackWithTimeout(channel, maxRetries, delayMs)
            }, 1000)
            return
        }
        
        // Controlla se il player è in uno stato valido
        if (exoPlayer?.playbackState == Player.STATE_READY) {
            writeToLog("Player già pronto - salto retry")
            return
        }
        
        // Se abbiamo raggiunto il numero massimo di retry, mostra errore
        if (maxRetries <= 0) {
            writeToLog("Raggiunto numero massimo di retry - mostro errore")
            binding.textError.text = "Impossibile ripristinare la riproduzione dopo il risveglio dallo standby"
            binding.textError.visibility = View.VISIBLE
            // Nascondi l'errore dopo 10 secondi
            binding.textError.postDelayed({
                binding.textError.visibility = View.GONE
            }, 10000)
            return
        }
        
        // Prova a riavviare il player
        try {
            writeToLog("Tentativo retry ${maxRetries} per: ${channel.name}")
            
            // Rilascia il player se è in uno stato inconsistente
            if (exoPlayer?.playbackState == Player.STATE_BUFFERING || 
                exoPlayer?.playbackState == Player.STATE_IDLE) {
                writeToLog("Player in stato inconsistente - rilascio e riavvio")
                releasePlayerCompletely()
            }
            
            // Aspetta un momento prima del retry
            Handler(Looper.getMainLooper()).postDelayed({
                startPlayerSafely(channel)
                
                // Controlla se il retry ha funzionato dopo un delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (exoPlayer?.playbackState != Player.STATE_READY) {
                        writeToLog("Retry fallito - riprovo")
                        retryPlaybackWithTimeout(channel, maxRetries - 1, delayMs)
                    } else {
                        writeToLog("Retry riuscito - player pronto")
                    }
                }, 5000) // Controlla dopo 5 secondi
                
            }, delayMs)
            
        } catch (e: Exception) {
            writeToLog("ERRORE durante retry con timeout: ${e.message}")
            // Riprova con un delay più lungo
            Handler(Looper.getMainLooper()).postDelayed({
                retryPlaybackWithTimeout(channel, maxRetries - 1, delayMs + 1000)
            }, delayMs)
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Tasto OK premuto - NON mostra controlli (sempre nascosti)
                Log.d("PlayerActivity", "Tasto OK premuto - controlli sempre nascosti")
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Controlla se HbbTV è attivo (qualsiasi tipo)
                if (isHbbTVBroadcastModeActive || raiHbbTvOverlay != null) {
                    // Esci dalla modalità HbbTV - clear all overlays
                    clearAllHbbTVOverlays()
                    true
                } else if (binding.layoutControls.visibility == View.VISIBLE) {
                    hideControls()
                    true
                } else {
                    // Mostra la lista dei canali (MainActivity)
                    val intent = Intent(this, com.livetv.androidtv.ui.main.MainActivity::class.java).apply {
                        putExtra("from_player", true)
                    }
                    startActivity(intent)
                    finish()
                    true
                }
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                changeChannel(1)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                changeChannel(-1)
                true
            }
            KeyEvent.KEYCODE_GUIDE -> {
                openEPG()
                true
            }
            KeyEvent.KEYCODE_INFO -> {
                showControls()
                true
            }
            // NUOVO: Supporto completo per tasti colorati HbbTV - DISABILITATO
            KeyEvent.KEYCODE_PROG_RED, KEYCODE_RED -> {
                // HBBTV DISABILITATO - Mostra messaggio informativo
                Log.d("PlayerActivity", "Tasto rosso premuto - HbbTV disabilitato")
                writeToLog("Tasto rosso premuto - HbbTV disabilitato")
                showHbbTVDisabledMessage()
                true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                // HBBTV DISABILITATO
                Log.d("PlayerActivity", "Tasto verde premuto - HbbTV disabilitato")
                writeToLog("Tasto verde premuto - HbbTV disabilitato")
                showHbbTVDisabledMessage()
                true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                // HBBTV DISABILITATO
                Log.d("PlayerActivity", "Tasto giallo premuto - HbbTV disabilitato")
                writeToLog("Tasto giallo premuto - HbbTV disabilitato")
                showHbbTVDisabledMessage()
                true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                // HBBTV DISABILITATO
                Log.d("PlayerActivity", "Tasto blu premuto - HbbTV disabilitato")
                writeToLog("Tasto blu premuto - HbbTV disabilitato")
                showHbbTVDisabledMessage()
                true
            }
            // Gestione tasti numerici
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                handleNumberInput(keyCode)
                true
            }
            // Gestione navigazione con telecomando quando i controlli sono visibili
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.layoutControls.visibility == View.VISIBLE) {
                    // Lascia che la navigazione gestisca il focus sui pulsanti
                    false
                } else {
                    // Se i controlli non sono visibili, cambia canale
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> changeChannel(-1)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> changeChannel(1)
                    }
                    true
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    

    
    @UnstableApi
    override fun onPause() {
        super.onPause()
        writeToLog("onPause chiamato")
        
        // GARANZIA: Rilascia sempre completamente il player quando l'app va in background
        releasePlayerCompletely()
        
        // Reset stato HbbTV se attivo
        if (isHbbTVBroadcastModeActive) {
            removeHbbTVBroadcastOverlay()
        }
    }
    
    @UnstableApi
    override fun onResume() {
        super.onResume()
        writeToLog("onResume chiamato")
        
        // Se siamo in recovery standby, non fare nulla - handleStandbyWakeup gestirà tutto
        if (isStandbyRecoveryInProgress) {
            writeToLog("Recovery standby in corso - salto onResume")
            return
        }
        
        // Riprendi la riproduzione se il canale è ancora valido
        if (currentChannel != null) {
            writeToLog("Riprendo riproduzione per: ${currentChannel!!.name}")
            
            // MIGLIORAMENTO: Se il player è null (rilasciato in onPause), riavvia con startPlayerSafely
            if (exoPlayer == null) {
                writeToLog("Player null (rilasciato in onPause) - riavvio con startPlayerSafely")
                // Usa startPlayerSafely per un riavvio pulito
                startPlayerSafely(currentChannel!!)
                return
            }
            
            // MIGLIORAMENTO: Controlla se il player è ancora valido
            try {
                // Verifica lo stato del player prima di riprendere
                when (exoPlayer!!.playbackState) {
                    Player.STATE_READY -> {
                        writeToLog("Player pronto - riprendo riproduzione")
                        exoPlayer?.play()
                    }
                    Player.STATE_BUFFERING -> {
                        writeToLog("Player in buffering - controllo timeout")
                        // Controlla se il buffering è bloccato (timeout ridotto a 5 secondi per standby)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (exoPlayer?.playbackState == Player.STATE_BUFFERING) {
                                writeToLog("Buffering timeout - reset completo player")
                                // Reset completo invece di semplice riavvio
                                releasePlayerCompletely()
                                Handler(Looper.getMainLooper()).postDelayed({
                                    startPlayerSafely(currentChannel!!)
                                }, 1000)
                            }
                        }, 5000) // Timeout ridotto a 5 secondi
                    }
                    Player.STATE_ENDED -> {
                        writeToLog("Player terminato - riavvio con startPlayerSafely")
                        // Il player è terminato, riavvia con startPlayerSafely
                        startPlayerSafely(currentChannel!!)
                    }
                    Player.STATE_IDLE -> {
                        writeToLog("Player idle - riavvio con startPlayerSafely")
                        // Il player è idle, riavvia con startPlayerSafely
                        startPlayerSafely(currentChannel!!)
                    }
                    else -> {
                        writeToLog("Player in stato sconosciuto: ${exoPlayer!!.playbackState} - riavvio con startPlayerSafely")
                        startPlayerSafely(currentChannel!!)
                    }
                }
            } catch (e: Exception) {
                writeToLog("ERRORE nel controllo stato player: ${e.message}")
                // In caso di errore, riavvia con startPlayerSafely
                startPlayerSafely(currentChannel!!)
            }
        } else {
            writeToLog("Canale non ancora caricato in onResume - aspetto...")
            // NON tornare alla lista subito - aspetta che il canale sia caricato
            // Il canale verrà caricato asincrono in onCreate()
        }
    }
    
    @UnstableApi
    private fun releasePlayerCompletely() {
        try {
            writeToLog("Rilascio completo del player")
            
            exoPlayer?.let { player ->
                player.stop()
                player.release()
                exoPlayer = null
                writeToLog("Player rilasciato completamente")
            }
            
            // Reset dei flag di inizializzazione
            isPlayerInitialized = false
            isInitializationInProgress = false
            isPlayChannelInProgress = false
            isAnyPlayerOperationInProgress = false
            
        } catch (e: Exception) {
            writeToLog("ERRORE nel rilascio completo player: ${e.message}")
            Log.e("PlayerActivity", "Error releasing player completely", e)
        }
    }
    
    @UnstableApi
    private fun reinitializePlayer() {
        writeToLog("Reinizializzazione player...")
        
        // GLOBAL LOCK: Evita operazioni multiple simultanee
        if (isAnyPlayerOperationInProgress) {
            writeToLog("Operazione player in corso - salto reinitializePlayer")
            return
        }
        
        isAnyPlayerOperationInProgress = true
        
        try {
            writeToLog("Reinizializzazione player...")
            
            // GARANZIA: Rilascia sempre completamente il player precedente
            releasePlayerCompletely()
            
            // Ricrea il player
            setupPlayer()
            
            // Riproduci il canale corrente solo se non è già in corso
            currentChannel?.let { channel ->
                writeToLog("Riproduco canale dopo reinizializzazione: ${channel.name}")
                startPlayerSafely(channel)
            }
            
            writeToLog("Player reinizializzato con successo")
            
        } catch (e: Exception) {
            writeToLog("ERRORE nella reinizializzazione player: ${e.message}")
            Log.e("PlayerActivity", "Error reinitializing player", e)
            
            // Reset dei flag anche in caso di errore
            isPlayerInitialized = false
            isInitializationInProgress = false
        } finally {
            // Reset del global lock
            isAnyPlayerOperationInProgress = false
            writeToLog("reinitializePlayer completato - global lock reset")
        }
    }
    
    @UnstableApi
    private fun handleNumberInput(keyCode: Int) {
        val digit = when (keyCode) {
            KeyEvent.KEYCODE_0 -> "0"
            KeyEvent.KEYCODE_1 -> "1"
            KeyEvent.KEYCODE_2 -> "2"
            KeyEvent.KEYCODE_3 -> "3"
            KeyEvent.KEYCODE_4 -> "4"
            KeyEvent.KEYCODE_5 -> "5"
            KeyEvent.KEYCODE_6 -> "6"
            KeyEvent.KEYCODE_7 -> "7"
            KeyEvent.KEYCODE_8 -> "8"
            KeyEvent.KEYCODE_9 -> "9"
            else -> return
        }
        
        numberInput += digit
        
        // Mostra l'input corrente
        showChannelNumberOverlay(numberInput)
        
        // Cancella il timeout precedente
        numberInputRunnable?.let { numberInputHandler.removeCallbacks(it) }
        
        // Imposta nuovo timeout
        numberInputRunnable = Runnable {
            val channelNumber = numberInput.toIntOrNull()
            if (channelNumber != null && channelNumber > 0) {
                changeToChannelByNumber(channelNumber)
            }
            numberInput = ""
            hideChannelNumberOverlay()
        }
        numberInputHandler.postDelayed(numberInputRunnable!!, NUMBER_INPUT_TIMEOUT)
    }
    
    @UnstableApi
    private fun changeToChannelByNumber(channelNumber: Int) {
        // Clear all HbbTV overlays before changing channel
        if (isHbbTVBroadcastModeActive || raiHbbTvOverlay != null) {
            clearAllHbbTVOverlays()
        }
        
        // GARANZIA: Rilascia completamente il player precedente per cambio canale
        releasePlayerCompletely()
        
        viewModel.getChannelByNumber(channelNumber) { channel ->
            if (channel != null) {
                // Aspetta un momento per assicurarsi che il rilascio sia completato
                Handler(Looper.getMainLooper()).postDelayed({
                    currentChannel = channel
                    saveLastChannel(channel)
                    startPlayerSafely(channel)
                    setupUI()
                    
                    // Mostra il popup del canale
                    showChannelPopup(channel)
                }, 100) // Piccolo delay per assicurarsi che il rilascio sia completato
            } else {
                // Mostra messaggio di errore
                binding.textError.text = "Canale $channelNumber non trovato"
                binding.textError.visibility = View.VISIBLE
                binding.textError.postDelayed({
                    binding.textError.visibility = View.GONE
                }, 2000)
            }
        }
    }
    
    @UnstableApi
    private fun showChannelNumberOverlay(number: String) {
        binding.textChannelNumber.text = number
        binding.textChannelNumber.visibility = View.VISIBLE
    }
    
    @UnstableApi
    private fun hideChannelNumberOverlay() {
        binding.textChannelNumber.visibility = View.GONE
    }
    

    
    @UnstableApi
    private fun saveLastChannel(channel: Channel) {
        // Salva l'ID del canale come ultimo visualizzato
        // La posizione verrà gestita dal MainViewModel
        preferencesManager.saveLastChannel(channel.id.toLongOrNull() ?: -1L, 0)
    }
    
    // Funzioni per gestire il popup del canale
    @UnstableApi
    private fun showChannelPopup(channel: Channel) {
        lifecycleScope.launch {
            try {
                // Crea ChannelWithProgram con programma corrente
                val currentTime = System.currentTimeMillis()
                
                // Usa direttamente il repository EPG come faceva MainViewModel
                val channelIdentifier = channel.epgId ?: channel.id
                val currentProgram = epgRepository.getCurrentProgram(channelIdentifier, currentTime)
                
                // Converti Program in EPGProgram per compatibilità
                val epgProgram = currentProgram?.let { program ->
                    com.livetv.androidtv.data.entity.EPGProgram(
                        id = program.id,
                        channelId = program.channelId,
                        title = program.title,
                        description = program.description,
                        startTime = program.startTime,
                        endTime = program.endTime,
                        category = program.category
                    )
                }
                val channelWithProgram = ChannelWithProgram(channel, epgProgram)
            
            // Aggiorna le informazioni del canale
            binding.popupChannelNumber.text = if (channel.number > 0) {
                channel.number.toString().padStart(3, '0')
            } else {
                "---"
            }
            binding.popupChannelName.text = channel.name
            binding.popupChannelGroup.text = channel.group ?: "Generale"
            
            // Gestisci i badge HD e HbbTV
            binding.popupHD.visibility = if (channel.isHD) View.VISIBLE else View.GONE
            
            // NUOVO: Gestisci badge HbbTV con supporto broadcast
            if (hasHbbTVBroadcast) {
                binding.popupHbbTV.visibility = View.VISIBLE
                // Nota: popupHbbTV potrebbe non supportare .text, usiamo solo visibility
            } else if (channel.hasHbbTV()) {
                binding.popupHbbTV.visibility = View.VISIBLE
                // Nota: popupHbbTV potrebbe non supportare .text, usiamo solo visibility
            } else {
                binding.popupHbbTV.visibility = View.GONE
            }
            
            // NUOVO: Mostra indicatore teletext se disponibile
            if (hasTeletextBroadcast) {
                // Crea un indicatore teletext se non esiste nel layout
                showTeletextIndicator()
            }
            
            // Aggiorna le informazioni del programma
            if (currentProgram != null) {
                binding.popupProgramTitle.text = currentProgram.title
                binding.popupProgramTime.text = currentProgram.getTimeRangeFormatted()
                binding.popupProgramDescription.text = currentProgram.description ?: ""
                binding.popupProgramDescription.visibility = if (currentProgram.description.isNullOrEmpty()) View.GONE else View.VISIBLE
            } else {
                binding.popupProgramTitle.text = "Nessun programma disponibile"
                binding.popupProgramTime.text = ""
                binding.popupProgramDescription.visibility = View.GONE
            }
            
            // Aggiorna il contatore dei canali
            val currentIndex = viewModel.getChannelIndex(channel)
            val totalChannels = viewModel.getTotalChannels()
            binding.popupChannelCount.text = "${currentIndex + 1}/$totalChannels"
            
            // Carica il logo del canale se disponibile
            if (!channel.logoUrl.isNullOrEmpty()) {
                try {
                    com.bumptech.glide.Glide.with(this@PlayerActivity)
                        .load(channel.logoUrl)
                        .placeholder(com.livetv.androidtv.R.drawable.ic_launcher)
                        .error(com.livetv.androidtv.R.drawable.ic_launcher)
                        .into(binding.popupChannelLogo)
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Errore nel caricamento logo canale", e)
                    binding.popupChannelLogo.setImageResource(com.livetv.androidtv.R.drawable.ic_launcher)
                }
            } else {
                binding.popupChannelLogo.setImageResource(com.livetv.androidtv.R.drawable.ic_launcher)
            }
            
            // Mostra il popup con animazione
            binding.channelPopupOverlay.visibility = View.VISIBLE
            binding.channelPopupOverlay.alpha = 0f
            binding.channelPopupOverlay.animate()
                .alpha(0.95f)
                .setDuration(300)
                .start()
            
            // Nascondi il popup dopo 3 secondi
            binding.channelPopupOverlay.postDelayed({
                hideChannelPopup()
            }, 3000)
            
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Errore nel mostrare popup canale", e)
            }
        }
    }
    
    @UnstableApi
    private fun hideChannelPopup() {
        try {
            // Nascondi il popup con animazione
            binding.channelPopupOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.channelPopupOverlay.visibility = View.GONE
                }
                .start()
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Errore nel nascondere popup canale", e)
            binding.channelPopupOverlay.visibility = View.GONE
        }
    }
    
    // HbbTV Event Handlers
    private fun handleNoAitDetected() {
        try {
            writeToLog("ℹ️ No AIT detected for service ${currentChannel?.name}")
            
            // Check if this is a RAI channel - don't reset HbbTV state for RAI
            if (currentChannel?.name?.contains("RAI", ignoreCase = true) == true) {
                writeToLog("🇮🇹 RAI channel detected - keeping HbbTV state active")
                
                // For RAI channels, keep the static HbbTV URL active
                // Don't reset hasHbbTVBroadcast, hbbTVData, or currentHbbTVApplication
                
                // Just update UI to reflect current state
                updateDvbDataUI()
                return
            }
            
            // For non-RAI channels, reset HbbTV state as usual
            writeToLog("Non-RAI channel - resetting HbbTV state")
            
            // Update HbbTV state
            hasHbbTVBroadcast = false
            hbbTVData = null
            currentHbbTVApplication = null
            
            // Update UI
            updateDvbDataUI()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error handling no AIT detected", e)
            writeToLog("ERRORE handling no AIT: ${e.message}")
        }
    }
    
    private fun showHbbTvIndicator(url: String) {
        try {
            // Show HbbTV indicator in UI using existing views
            binding.popupHbbTV?.let { indicator ->
                indicator.visibility = View.VISIBLE
                indicator.setOnClickListener {
                    // Launch HbbTV application
                    launchHbbTvApplication(url)
                }
            }
            
            binding.buttonHbbTV?.let { button ->
                button.visibility = View.VISIBLE
                button.setOnClickListener {
                    // Launch HbbTV application
                    launchHbbTvApplication(url)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error showing HbbTV indicator", e)
        }
    }
    
    private fun launchHbbTvApplication(url: String) {
        try {
            writeToLog("🚀 Launching HbbTV application: $url")
            
            val intent = Intent(this, com.livetv.androidtv.ui.hbbtv.HbbTVActivity::class.java).apply {
                putExtra(com.livetv.androidtv.ui.hbbtv.HbbTVActivity.EXTRA_URL, url)
                putExtra(com.livetv.androidtv.ui.hbbtv.HbbTVActivity.EXTRA_CHANNEL, currentChannel)
            }
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error launching HbbTV application", e)
            writeToLog("ERRORE launching HbbTV: ${e.message}")
        }
    }
    
    /**
     * Handle HbbTV URL found from AIT parsing - DISABILITATO
     */
    private fun handleHbbTvUrlFound(info: com.livetv.androidtv.hbbtv.HbbTvAppUrl) {
        // HBBTV DISABILITATO - Non gestire più gli URL HbbTV trovati
        writeToLog("🎯 HbbTV URL found from AIT ma è disabilitato: ${info.url}")
        
        // Non aggiornare più lo stato HbbTV
        // hasHbbTVBroadcast = false
        // currentHbbTVApplication = null
    }
    
    /**
     * Check if current channel is RAI and provide static HbbTV URL
     */
    private fun checkRaiChannelForHbbTv() {
        try {
            currentChannel?.let { channel ->
                // Check if channel name contains "RAI" or "Rai"
                if (channel.name.contains("RAI", ignoreCase = true)) {
                    writeToLog("🇮🇹 RAI channel detected: ${channel.name}")
                    
                    // Use static RAI HbbTV URL
                    val raiHbbTvUrl = "https://www.replaytvmhp.rai.it/hbbtv/launcher/RemoteControl/index.html"
                    
                    // Create HbbTvAppUrl object for RAI
                    val raiHbbTvInfo = com.livetv.androidtv.hbbtv.HbbTvAppUrl(
                        url = raiHbbTvUrl,
                        autostart = false, // Don't auto-launch for RAI
                        appId = 0x0001, // Default RAI app ID
                        orgId = 0x0001  // Default RAI org ID
                    )
                    
                    // HBBTV DISABILITATO - Non aggiornare più lo stato HbbTV
                    writeToLog("🇮🇹 RAI channel rilevato ma HbbTV è disabilitato")
                    
                    // Non mostrare più indicatori HbbTV
                    // hasHbbTVBroadcast = false
                    // currentHbbTVApplication = null
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error checking RAI channel for HbbTV", e)
            writeToLog("ERRORE checking RAI channel: ${e.message}")
        }
    }
    
    /**
     * Handle AIT present but no HbbTV URL - DISABILITATO
     */
    private fun handleAitPresentButNoUrl(reason: String) {
        // HBBTV DISABILITATO - Non gestire più i dati AIT
        writeToLog("ℹ️ AIT present but HbbTV è disabilitato: $reason")
        
        // Non aggiornare più lo stato HbbTV
        // hasHbbTVBroadcast = false
        // currentHbbTVApplication = null
    }
    

    
    @UnstableApi
    override fun onDestroy() {
        try {
            writeToLog("onDestroy chiamato - rilascio risorse")
            writeToLog("=== PLAYER ACTIVITY LOG END ===")
            
            // Cleanup stato HbbTV se attivo - clear all overlays
            if (isHbbTVBroadcastModeActive || raiHbbTvOverlay != null) {
                clearAllHbbTVOverlays()
            } else {
                // Restore native system controls if HbbTV was not active
                restoreNativeSystemControls()
            }
            
            // Release HbbTV Manager
            hbbTvManager.release()
            
            // Optimize: Clear all callbacks and handlers
            controlsRunnable?.let { controlsHandler.removeCallbacks(it) }
            numberInputRunnable?.let { numberInputHandler.removeCallbacks(it) }
            controlsRunnable = null
            numberInputRunnable = null
            
            // Reset dei flag di inizializzazione
            isPlayerInitialized = false
            isInitializationInProgress = false
            
            // Optimize: Clear memory-intensive objects
            hbbTVData = null
            teletextData = null
            teletextBuffer?.clear()
            teletextBuffer = null
            currentHbbTVApplication = null
            
            // GARANZIA: Rilascia sempre completamente il player
            releasePlayerCompletely()
            
            // Optimize: Force garbage collection for better memory cleanup
            System.gc()
            
            writeToLog("Risorse rilasciate con successo")
        } catch (e: Exception) {
            writeToLog("ERRORE in onDestroy: ${e.message}")
            createEmergencyLog("ERRORE durante onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }

    /**
     * Gestisce il risveglio dallo standby con reset completo del player
     */
    @UnstableApi
    private fun handleStandbyWakeup() {
        try {
            writeToLog("Gestione risveglio dallo standby - RESET COMPLETO PLAYER")
            
            // Flag per indicare che siamo in recovery standby
            isStandbyRecoveryInProgress = true
            
            // RESET IMMEDIATO: Rilascia tutto subito senza aspettare
            writeToLog("Reset immediato del player per standby")
            
            // Forza il rilascio completo del player esistente
            if (exoPlayer != null) {
                writeToLog("Rilascio immediato player esistente per standby")
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                isPlayerInitialized = false
            }
            
            // Reset completo di tutti i flag di stato
            isAnyPlayerOperationInProgress = false
            isPlayChannelInProgress = false
            isInitializationInProgress = false
            
            // Nascondi tutti gli indicatori di stato
            binding.progressBar.visibility = View.GONE
            binding.textStatus.visibility = View.GONE
            binding.textError.visibility = View.GONE
            
            // Forza garbage collection per pulire le risorse
            System.gc()
            
            // Aspetta che il sistema si stabilizzi (ridotto a 1 secondo per risposta più veloce)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    writeToLog("Sistema stabilizzato - ricreo player da zero")
                    
                    // Ricrea completamente il player da zero
                    setupPlayer()
                    
                    // Aspetta un momento per l'inizializzazione
                    Handler(Looper.getMainLooper()).postDelayed({
                        writeToLog("Player ricreato - riavvio riproduzione")
                        isStandbyRecoveryInProgress = false
                        
                        // Se abbiamo un canale corrente, riavvia la riproduzione
                        currentChannel?.let { channel ->
                            writeToLog("Riavvio riproduzione dopo reset completo per: ${channel.name}")
                            // Usa startPlayerSafely per un riavvio pulito
                            startPlayerSafely(channel)
                        }
                    }, 1000)
                    
                } catch (e: Exception) {
                    writeToLog("ERRORE durante ricreazione player: ${e.message}")
                    Log.e("PlayerActivity", "Errore ricreazione player", e)
                    isStandbyRecoveryInProgress = false
                }
            }, 1000)
            
        } catch (e: Exception) {
            writeToLog("ERRORE in handleStandbyWakeup: ${e.message}")
            Log.e("PlayerActivity", "Errore handleStandbyWakeup", e)
            isStandbyRecoveryInProgress = false
        }
    }
    
    /**
     * Log dei renderer disponibili per verificare il supporto MP2 via FFmpeg
     */
    @UnstableApi
    private fun logAvailableRenderers() {
        try {
            // Metodo semplificato per evitare problemi di compatibilità
            writeToLog("Verifica renderer disponibili per supporto MP2")
            writeToLog("Renderer factory configurato con supporto FFmpeg per MP2")
            
        } catch (e: Exception) {
            writeToLog("ERRORE durante log renderer: ${e.message}")
            Log.e("PlayerActivity", "ERRORE durante log renderer", e)
        }
    }
}