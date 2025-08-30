/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.livetv.androidtv.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.livetv.androidtv.R
import com.livetv.androidtv.databinding.ActivityMainBinding
import com.livetv.androidtv.ui.player.PlayerActivity
import com.livetv.androidtv.ui.settings.SettingsActivity
import com.livetv.androidtv.ui.epg.EPGActivity
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.utils.PreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var preferencesManager: PreferencesManager
    private var autoStartPending = false
    
    // Gestione input numerico
    private var numberInput = ""
    private val numberInputHandler = Handler(Looper.getMainLooper())
    private var numberInputRunnable: Runnable? = null
    private val NUMBER_INPUT_TIMEOUT = 2000L
    
    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "🚀 onCreate chiamato")
        
        // Inizializza PreferencesManager
        preferencesManager = PreferencesManager(this)
        
        // Controlla se siamo stati chiamati dal PlayerActivity (tasto indietro)
        val fromPlayer = intent.getBooleanExtra("from_player", false)
        val fromStandbyWakeup = intent.getBooleanExtra("from_standby_wakeup", false)
        
        if (fromPlayer) {
            android.util.Log.d("MainActivity", "📋 Chiamato dal player - mostro lista")
            // Se siamo stati chiamati dal player, mostra direttamente la lista
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupUI()
            setupObservers()
            setupClickListeners()
        } else if (fromStandbyWakeup) {
            android.util.Log.d("MainActivity", "📺 Risveglio dallo standby senza ultimo canale - avvio normale")
            // Se siamo stati chiamati dal risveglio dallo standby ma non c'è ultimo canale, avvia normalmente
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupUI()
            setupObservers()
            setupClickListeners()
            
            // Avvia il player con il primo canale disponibile
            checkAndStartPlayerDirectly()
        } else {
            // NUOVO: Controlla se dobbiamo avviare direttamente il player
            android.util.Log.d("MainActivity", "🎯 Avvio normale - controllo diretto player")
            checkAndStartPlayerDirectly()
        }
    }
    
    @androidx.media3.common.util.UnstableApi
    private fun checkAndStartPlayerDirectly() {
        android.util.Log.d("MainActivity", "🚀 checkAndStartPlayerDirectly chiamato")
        
        // Carica i canali per verificare se sono configurati
        viewModel.loadChannels()
        
        // Osserva i canali per decidere se avviare direttamente il player
        viewModel.channels.observe(this) { channels ->
            android.util.Log.d("MainActivity", "📺 Canali ricevuti: ${channels.size}")
            android.util.Log.d("MainActivity", "🔍 Binding inizializzato: ${::binding.isInitialized}")
            
            // Solo se il binding non è ancora inizializzato (prima volta)
            if (!::binding.isInitialized) {
                if (channels.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "🚀 Prima volta - avvio diretto player")
                    
                    // Se ci sono canali, controlla se c'è un ultimo canale salvato
                    val lastChannelId = preferencesManager.getLastChannelId()
                    android.util.Log.d("MainActivity", "🔍 Last channel ID: $lastChannelId")
                    
                    var channelToStart: Channel? = null
                    
                    if (lastChannelId != -1L) {
                        // Trova il canale con l'ID salvato
                        val lastChannel = channels.find { it.id == lastChannelId.toString() }
                        android.util.Log.d("MainActivity", "🎯 Last channel trovato: ${lastChannel?.name}")
                        channelToStart = lastChannel
                    }
                    
                    // Se non c'è un ultimo canale valido, usa il primo canale
                    if (channelToStart == null) {
                        channelToStart = channels.first()
                        android.util.Log.d("MainActivity", "🎯 Usando primo canale: ${channelToStart.name}")
                    }
                    
                    // Avvia direttamente il PlayerActivity (channelToStart è sempre non null qui)
                    android.util.Log.d("MainActivity", "✅ Avvio diretto PlayerActivity con canale: ${channelToStart.name}")
                    android.util.Log.d("MainActivity", "📤 Passando channel_id: ${channelToStart.id}")
                    startPlayerActivity(channelToStart)
                    return@observe
                } else {
                    android.util.Log.d("MainActivity", "⚙️ Nessun canale configurato - vado direttamente alle impostazioni")
                    // Se non ci sono canali, vai direttamente alle impostazioni
                    goToSettingsForConfiguration()
                    return@observe
                }
            }
        }
        
        // NON setup degli observer qui - verranno configurati in showChannelList() se necessario
    }
    
    private fun showChannelList() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupObservers()
        setupClickListeners()
        
        // Imposta l'avvio automatico se è configurato
        autoStartPending = true
    }
    
    private fun setupOtherObservers() {
        // Osserva i messaggi del ViewModel (per status EPG)
        viewModel.message.observe(this) { message ->
            if (message != null) {
                showMessage(message)
            }
        }
        
        // Osserva i programmi EPG per aggiornare l'adapter
        viewModel.currentProgram.observe(this) { program ->
            // Aggiorna l'adapter con i programmi EPG solo se ci sono canali
            if (viewModel.channels.value?.isNotEmpty() == true) {
                updateAdapterWithPrograms()
            }
        }
        
        // Osserva tutti i programmi EPG per aggiornare l'adapter
        viewModel.allPrograms.observe(this) { programs ->
            // Aggiorna l'adapter con i programmi EPG solo se ci sono canali
            if (viewModel.channels.value?.isNotEmpty() == true) {
                updateAdapterWithPrograms()
            }
        }
        
        // Osserva il canale corrente per aggiornare l'UI
        viewModel.currentChannel.observe(this) { channel ->
            channel?.let {
                // Solo se il binding è inizializzato
                if (::binding.isInitialized) {
                    highlightCurrentChannel()
                }
            }
        }
        
        // Osserva la posizione di scroll
        viewModel.scrollToPosition.observe(this) { position ->
            if (position >= 0 && ::binding.isInitialized) {
                layoutManager.scrollToPosition(position)
            }
        }
        
        // Osserva lo stato di caricamento
        viewModel.isLoading.observe(this) { isLoading ->
            // Gestisci lo stato di caricamento se necessario
        }
        
        // Osserva gli errori
        viewModel.error.observe(this) { error ->
            error?.let {
                showMessage(it)
            }
        }
    }
    


    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) private fun setupUI() {
        channelAdapter = ChannelAdapter { channel ->
            // Aggiorna il canale selezionato nel ViewModel
            viewModel.selectChannel(channel)
            openPlayer(channel)
        }
        
        layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChannels.layoutManager = layoutManager
        binding.recyclerViewChannels.adapter = channelAdapter
        
        // 🎮 NAVIGAZIONE OTTIMIZZATA PER TELECOMANDO
        setupRemoteControlNavigation()
        
        // Migliora la gestione del focus per la navigazione con le frecce
        binding.recyclerViewChannels.isFocusable = true
        binding.recyclerViewChannels.requestFocus()
        
        // Gestisce il focus degli elementi del RecyclerView
        binding.recyclerViewChannels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Assicurati che un elemento sia selezionato quando il RecyclerView ha il focus
                highlightCurrentChannel()
                android.util.Log.d("MainActivity", "🎯 RecyclerView ha il focus")
            }
        }
        
        // Aggiungi listener per i tasti del RecyclerView
        binding.recyclerViewChannels.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Se siamo sul primo elemento visibile, vai ai pulsanti header
                        if (layoutManager.findFirstVisibleItemPosition() == 0) {
                            android.util.Log.d("MainActivity", "⬆️ SU dal primo canale - vai ai pulsanti header")
                            binding.buttonFavorites.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }
    
    /**
     * 🎮 Configura la navigazione ottimizzata per il telecomando
     */
    private fun setupRemoteControlNavigation() {
        android.util.Log.d("MainActivity", "🎯 Configurazione navigazione telecomando iniziata")
        
        // Il layout XML gestisce già nextFocusDown/Up, ma assicuriamoci che funzioni
        binding.recyclerViewChannels.nextFocusUpId = binding.buttonRefresh.id
        
        // Migliora la visibilità del focus sui pulsanti dell'header
        setupHeaderFocusIndicators()
        
        // Imposta focus iniziale sui pulsanti principali
        binding.buttonFavorites.requestFocus()
        
        // Forza il focus sui pulsanti dell'header
        forceFocusOnHeaderButtons()
        
        android.util.Log.d("MainActivity", "✅ Navigazione telecomando configurata")
    }
    
    /**
     * Forza il focus sui pulsanti dell'header per assicurarsi che siano raggiungibili
     */
    private fun forceFocusOnHeaderButtons() {
        // Assicurati che tutti i pulsanti siano focusabili
        val headerButtons = listOf(
            binding.buttonFavorites,
            binding.buttonEpg,
            binding.buttonSettings,
            binding.buttonRefresh
        )
        
        headerButtons.forEach { button ->
            button.isFocusable = true
            button.isFocusableInTouchMode = false // Importante per telecomando
            button.isClickable = true
        }
        
        // Imposta il focus sul primo pulsante
        binding.buttonFavorites.post {
            binding.buttonFavorites.requestFocus()
            android.util.Log.d("MainActivity", "🎯 Focus forzato su buttonFavorites")
        }
    }
    
    /**
     * Configura gli indicatori di focus per i pulsanti dell'header
     */
    private fun setupHeaderFocusIndicators() {
        val headerButtons = listOf(
            binding.buttonFavorites,
            binding.buttonEpg,
            binding.buttonSettings,
            binding.buttonRefresh
        )
        
        headerButtons.forEach { button ->
            button.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Pulsante in focus - evidenzia visivamente
                    button.alpha = 1.0f
                    button.scaleX = 1.1f
                    button.scaleY = 1.1f
                    
                    android.util.Log.d("MainActivity", "🎯 Focus su: ${button.text}")
                } else {
                    // Pulsante non in focus - ripristina dimensioni
                    button.alpha = 0.8f
                    button.scaleX = 1.0f
                    button.scaleY = 1.0f
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Inizializza l'adapter con una lista vuota per evitare crash
        channelAdapter.submitList(emptyList<ChannelWithProgram>())
        
        viewModel.channels.observe(this) { channels ->
            android.util.Log.d("MainActivity", "Canali ricevuti dal ViewModel: ${channels.size}")
            
            // Aggiorna l'adapter con i canali (anche senza programmi EPG)
            if (channels.isNotEmpty()) {
                updateAdapterWithChannelsOnly(channels)
            }
            
            // DISABILITATO: Avvio automatico dell'ultimo canale (ora gestito da checkAndStartPlayerDirectly)
            // if (autoStartPending && channels.isNotEmpty()) {
            //     autoStartPending = false
            //     lifecycleScope.launch {
            //         // Aspetta un momento per assicurarsi che tutto sia inizializzato
            //         delay(1000)
            //         viewModel.currentChannel.value?.let { channel ->
            //             openPlayer(channel)
            //         }
            //     }
            // }
        }
        
        // Osserva i messaggi del ViewModel (per status EPG)
        viewModel.message.observe(this) { message ->
            if (message != null) {
                showMessage(message)
            }
        }
        
        // Osserva i programmi EPG per aggiornare l'adapter
        viewModel.currentProgram.observe(this) { program ->
            // Aggiorna l'adapter con i programmi EPG solo se ci sono canali
            if (viewModel.channels.value?.isNotEmpty() == true) {
                updateAdapterWithPrograms()
            }
        }
        
        // Osserva tutti i programmi EPG per aggiornare l'adapter
        viewModel.allPrograms.observe(this) { programs ->
            // Aggiorna l'adapter con i programmi EPG solo se ci sono canali
            if (viewModel.channels.value?.isNotEmpty() == true) {
                updateAdapterWithPrograms()
            }
        }
        
        // Osserva quando scrollare alla posizione
        viewModel.scrollToPosition.observe(this) { position ->
            if (position >= 0) {
                scrollToPosition(position)
                channelAdapter.setSelectedPosition(position)
            }
        }
        

    }
    
    private fun setupClickListeners() {
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.buttonEpg.setOnClickListener {
            android.util.Log.d("MainActivity", "Click su GUIDA TV - Avvio EPGActivity")
            try {
                val intent = Intent(this, EPGActivity::class.java)
                startActivity(intent)
                android.util.Log.d("MainActivity", "EPGActivity avviata con successo")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Errore nell'avvio EPGActivity", e)
            }
        }
        
        binding.buttonFavorites.setOnClickListener {
            viewModel.toggleFavoritesFilter()
        }
        
        binding.buttonRefresh.setOnClickListener {
            viewModel.loadEPGFromURL()
        }
    }
    
    private fun updateAdapterWithPrograms() {
        try {
            // Ottieni i canali e programmi dal ViewModel
            val channels = viewModel.channels.value ?: emptyList()
            val programs = viewModel.allPrograms.value ?: emptyList()
            
            // Verifica che i dati siano disponibili
            if (channels.isEmpty()) {
                android.util.Log.d("MainActivity", "Nessun canale disponibile per l'aggiornamento adapter")
                // Passa una lista vuota di ChannelWithProgram invece di non fare nulla
                binding.recyclerViewChannels.post {
                    try {
                        channelAdapter.submitList(emptyList<ChannelWithProgram>())
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Errore nell'aggiornamento adapter con lista vuota", e)
                    }
                }
                return
            }
            
            val currentTime = System.currentTimeMillis()
            
            // Crea la lista di canali con programmi correnti
            val channelsWithPrograms = channels.map { channel ->
                val currentProgram = programs.find { program ->
                    // Usa epgId se disponibile, altrimenti id
                    val channelIdentifier = channel.epgId ?: channel.id
                    program.channelId == channelIdentifier && 
                    program.startTime <= currentTime && 
                    program.endTime > currentTime
                }
                ChannelWithProgram(channel, currentProgram)
            }
            
            android.util.Log.d("MainActivity", "Aggiornamento adapter con ${channelsWithPrograms.size} canali")
            
            // Aggiorna l'adapter in modo sicuro
            binding.recyclerViewChannels.post {
                try {
                    channelAdapter.submitList(channelsWithPrograms)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Errore nell'aggiornamento adapter", e)
                    // In caso di errore, prova a passare una lista vuota
                    try {
                        channelAdapter.submitList(emptyList<ChannelWithProgram>())
                    } catch (e2: Exception) {
                        android.util.Log.e("MainActivity", "Errore anche con lista vuota", e2)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nella preparazione dati per l'adapter", e)
            // In caso di errore, passa una lista vuota
            binding.recyclerViewChannels.post {
                try {
                    channelAdapter.submitList(emptyList<ChannelWithProgram>())
                } catch (e2: Exception) {
                    android.util.Log.e("MainActivity", "Errore anche con lista vuota nel catch esterno", e2)
                }
            }
        }
    }
    
    private fun updateAdapterWithChannelsOnly(channels: List<Channel>) {
        try {
            android.util.Log.d("MainActivity", "Aggiornamento adapter con solo canali: ${channels.size}")
            
            if (channels.isEmpty()) {
                // Se non ci sono canali, mostra un messaggio di configurazione
                android.util.Log.d("MainActivity", "Nessun canale configurato - mostra messaggio di configurazione")
                binding.recyclerViewChannels.post {
                    try {
                        channelAdapter.submitList(emptyList<ChannelWithProgram>())
                        // Mostra messaggio di configurazione
                        showConfigurationMessage()
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Errore nell'aggiornamento adapter con lista vuota", e)
                    }
                }
                return
            }
            
            // Crea la lista di canali senza programmi EPG
            val channelsWithPrograms = channels.map { channel ->
                ChannelWithProgram(channel, null)
            }
            
            // Aggiorna l'adapter in modo sicuro
            binding.recyclerViewChannels.post {
                try {
                    channelAdapter.submitList(channelsWithPrograms)
                    // Nasconde il messaggio di configurazione se ci sono canali
                    hideConfigurationMessage()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Errore nell'aggiornamento adapter con solo canali", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nella preparazione canali per l'adapter", e)
        }
    }
    
    @androidx.media3.common.util.UnstableApi
    private fun startPlayerActivity(channel: Channel) {
        android.util.Log.d("MainActivity", "🎬 Avvio PlayerActivity per: ${channel.name}")
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("auto_start", true)
        }
        startActivity(intent)
        finish() // Chiudi MainActivity
    }
    
    @androidx.media3.common.util.UnstableApi
    private fun openPlayer(channel: Channel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_CHANNEL, channel)
        }
        startActivity(intent)
    }
    
    @OptIn(UnstableApi::class) override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.d("MainActivity", "🎮 Tasto premuto: $keyCode")
        
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Se siamo sui pulsanti dell'header, naviga tra i pulsanti (SU va al precedente)
                if (binding.buttonFavorites.hasFocus() || 
                    binding.buttonEpg.hasFocus() || 
                    binding.buttonSettings.hasFocus() || 
                    binding.buttonRefresh.hasFocus()) {
                    // Navigazione tra pulsanti header: AGGIORNA -> IMPOSTAZIONI -> GUIDA TV -> PREFERITI
                    when {
                        binding.buttonRefresh.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬆️ SU da AGGIORNA -> IMPOSTAZIONI")
                            binding.buttonSettings.requestFocus()
                        }
                        binding.buttonSettings.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬆️ SU da IMPOSTAZIONI -> GUIDA TV")
                            binding.buttonEpg.requestFocus()
                        }
                        binding.buttonEpg.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬆️ SU da GUIDA TV -> PREFERITI")
                            binding.buttonFavorites.requestFocus()
                        }
                        binding.buttonFavorites.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬆️ SU da PREFERITI -> AGGIORNA")
                            binding.buttonRefresh.requestFocus()
                        }
                    }
                    true
                }
                // Se siamo sul primo canale, vai all'ultimo pulsante dell'header (AGGIORNA)
                else if (layoutManager.findFirstVisibleItemPosition() == 0) {
                    android.util.Log.d("MainActivity", "⬆️ SU dal primo canale - vai a AGGIORNA")
                    binding.buttonRefresh.requestFocus()
                    true
                }
                // Altrimenti naviga tra i canali
                else {
                    navigateChannel(-1)
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Se siamo sui pulsanti dell'header, naviga tra i pulsanti (GIÙ va al successivo)
                if (binding.buttonFavorites.hasFocus() || 
                    binding.buttonEpg.hasFocus() || 
                    binding.buttonSettings.hasFocus() || 
                    binding.buttonRefresh.hasFocus()) {
                    // Navigazione tra pulsanti header: PREFERITI -> GUIDA TV -> IMPOSTAZIONI -> AGGIORNA
                    when {
                        binding.buttonFavorites.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬇️ GIÙ da PREFERITI -> GUIDA TV")
                            binding.buttonEpg.requestFocus()
                        }
                        binding.buttonEpg.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬇️ GIÙ da GUIDA TV -> IMPOSTAZIONI")
                            binding.buttonSettings.requestFocus()
                        }
                        binding.buttonSettings.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬇️ GIÙ da IMPOSTAZIONI -> AGGIORNA")
                            binding.buttonRefresh.requestFocus()
                        }
                        binding.buttonRefresh.hasFocus() -> {
                            android.util.Log.d("MainActivity", "⬇️ GIÙ da AGGIORNA -> primo canale")
                            binding.recyclerViewChannels.requestFocus()
                        }
                    }
                    true
                }
                // Altrimenti naviga tra i canali
                else {
                    navigateChannel(1)
                    true
                }
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                navigateChannel(1)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                navigateChannel(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                viewModel.currentChannel.value?.let { channel ->
                    openPlayer(channel)
                }
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            KeyEvent.KEYCODE_GUIDE -> {
                startActivity(Intent(this, EPGActivity::class.java))
                true
            }
            KeyEvent.KEYCODE_STAR -> {
                viewModel.toggleFavoritesFilter()
                true
            }
            // Gestione tasti numerici per selezione diretta canale
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                handleNumberInput(keyCode)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun navigateChannel(direction: Int) {
        lifecycleScope.launch {
            if (direction > 0) {
                viewModel.selectNextChannel()
            } else {
                viewModel.selectPreviousChannel()
            }
        }
    }
    
    private fun scrollToPosition(position: Int) {
        // Scroll smooth alla posizione
        binding.recyclerViewChannels.post {
            layoutManager.scrollToPositionWithOffset(position, 0)
            
            // Assicurati che l'elemento sia visibile
            binding.recyclerViewChannels.postDelayed({
                val viewHolder = binding.recyclerViewChannels.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.requestFocus()
            }, 100)
        }
    }
    
    private fun highlightCurrentChannel() {
        // Questo metodo può essere esteso per evidenziare visivamente il canale corrente
        // Per ora assicuriamo solo che il RecyclerView mantenga il focus
        if (!binding.recyclerViewChannels.hasFocus()) {
            binding.recyclerViewChannels.requestFocus()
        }
    }
    
    private fun showMessage(message: String) {
        // Mostra un messaggio temporaneo all'utente
        // Per ora usiamo un Toast, ma potresti implementare un sistema più elegante
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    

    
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
        
        // Mostra l'input corrente nel titolo
        binding.textChannelsTitle.text = "Canale: $numberInput"
        
        // Cancella il timeout precedente
        numberInputRunnable?.let { numberInputHandler.removeCallbacks(it) }
        
        // Imposta nuovo timeout
        numberInputRunnable = Runnable {
            val channelNumber = numberInput.toIntOrNull()
            if (channelNumber != null && channelNumber > 0) {
                selectChannelByNumber(channelNumber)
            }
            numberInput = ""
            // Ripristina il titolo originale
            val channelCount = viewModel.channels.value?.size ?: 0
            binding.textChannelsTitle.text = getString(R.string.channels_title)
        }
        numberInputHandler.postDelayed(numberInputRunnable!!, NUMBER_INPUT_TIMEOUT)
    }
    
    @OptIn(UnstableApi::class) private fun selectChannelByNumber(channelNumber: Int) {
        lifecycleScope.launch {
            val channels = viewModel.channels.value ?: return@launch
            val channel = channels.find { it.number == channelNumber }
            
            if (channel != null) {
                viewModel.selectChannel(channel)
                openPlayer(channel)
            } else {
                // Mostra messaggio di errore temporaneo
                binding.textChannelsTitle.text = "Canale $channelNumber non trovato"
                binding.textChannelsTitle.postDelayed({
                    binding.textChannelsTitle.text = getString(R.string.channels_title)
                }, 2000)
            }
        }
    }
    
    @OptIn(UnstableApi::class) override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "🔄 onResume chiamato")
        
        // Controlla se siamo stati chiamati dal PlayerActivity (tasto indietro)
        val fromPlayer = intent.getBooleanExtra("from_player", false)
        
        if (fromPlayer) {
            android.util.Log.d("MainActivity", "📋 Tornato dal player - mostro lista")
            // Se torniamo dal player, mostra la lista
            if (!::binding.isInitialized) {
                showChannelList()
            }
        } else {
            android.util.Log.d("MainActivity", "🚀 Ripresa normale - controllo avvio diretto")
            // Se è una ripresa normale, controlla se dobbiamo avviare il player
            if (!::binding.isInitialized) {
                checkAndStartPlayerDirectly()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        numberInputRunnable?.let { numberInputHandler.removeCallbacks(it) }
    }
    
    private fun showConfigurationMessage() {
        try {
            if (::binding.isInitialized) {
                binding.textChannelsTitle.text = "Nessun canale configurato"
                binding.textChannelsTitle.setTextColor(getColor(android.R.color.holo_orange_light))
                
                // Mostra un messaggio di aiuto
                android.widget.Toast.makeText(
                    this, 
                    "Vai nelle Impostazioni per configurare i canali", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nel mostrare messaggio di configurazione", e)
        }
    }
    
    private fun hideConfigurationMessage() {
        try {
            if (::binding.isInitialized) {
                binding.textChannelsTitle.text = getString(R.string.channels_title)
                binding.textChannelsTitle.setTextColor(getColor(android.R.color.white))
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nel nascondere messaggio di configurazione", e)
        }
    }
    
    private fun goToSettingsForConfiguration() {
        try {
            android.util.Log.d("MainActivity", "🎯 Avvio SettingsActivity per configurazione canali")
            
            // Mostra un messaggio informativo
            android.widget.Toast.makeText(
                this, 
                "Configura i canali nelle Impostazioni", 
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            // Vai direttamente alle impostazioni
            val intent = Intent(this, com.livetv.androidtv.ui.settings.SettingsActivity::class.java)
            startActivity(intent)
            
            // Chiudi MainActivity per evitare confusione
            finish()
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nell'avvio delle impostazioni", e)
            // Fallback: mostra la lista normale
            showChannelList()
        }
    }
}