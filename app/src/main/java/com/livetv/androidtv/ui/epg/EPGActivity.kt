package com.livetv.androidtv.ui.epg

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.livetv.androidtv.R
import com.livetv.androidtv.databinding.ActivityEpgBinding
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.ui.epg.EPGViewModel
import kotlinx.coroutines.launch

class EPGActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }
    
    private lateinit var binding: ActivityEpgBinding
    private val viewModel: EPGViewModel by viewModels()
    private lateinit var epgAdapter: EPGAdapter
    private lateinit var layoutManager: LinearLayoutManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("EPGActivity", "=== EPGActivity onCreate ===")
        
        try {
            binding = ActivityEpgBinding.inflate(layoutInflater)
            setContentView(binding.root)
            android.util.Log.d("EPGActivity", "Layout inflato e impostato")
            
            setupUI()
            setupObservers()
            setupClickListeners()
            
            // Carica i dati EPG
            android.util.Log.d("EPGActivity", "Avvio caricamento dati EPG")
            viewModel.loadEPGData()
            
            android.util.Log.d("EPGActivity", "EPGActivity onCreate completato")
        } catch (e: Exception) {
            android.util.Log.e("EPGActivity", "Errore in onCreate", e)
        }
    }
    
    private fun setupUI() {
        android.util.Log.d("EPGActivity", "Setup UI - Creazione adapter e layout manager")
        
        try {
            epgAdapter = EPGAdapter { program ->
                // Gestione click su programma (opzionale)
                showProgramDialog(program)
            }
            android.util.Log.d("EPGActivity", "EPGAdapter creato")
            
            layoutManager = LinearLayoutManager(this)
            binding.recyclerViewEpg.layoutManager = layoutManager
            binding.recyclerViewEpg.adapter = epgAdapter
            android.util.Log.d("EPGActivity", "Layout manager e adapter impostati")
            
            // Migliora la gestione del focus per la navigazione con le frecce
            binding.recyclerViewEpg.isFocusable = true
            binding.recyclerViewEpg.requestFocus()
            android.util.Log.d("EPGActivity", "Focus impostato su RecyclerView")
            
        } catch (e: Exception) {
            android.util.Log.e("EPGActivity", "Errore in setupUI", e)
            throw e
        }
    }
    
    private fun setupObservers() {
        android.util.Log.d("EPGActivity", "Setup observers")
        
        viewModel.epgData.observe(this) { epgData ->
            android.util.Log.d("EPGActivity", "Observer epgData: ricevuti ${epgData.size} programmi")
            
            try {
                epgAdapter.submitList(epgData)
                binding.textEpgTitle.text = "Guida TV (${epgData.size} programmi)"
                android.util.Log.d("EPGActivity", "Lista aggiornata nell'adapter, titolo aggiornato")
            } catch (e: Exception) {
                android.util.Log.e("EPGActivity", "Errore nell'aggiornamento della lista", e)
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        viewModel.error.observe(this) { error ->
            if (error != null) {
                binding.textError.text = error
                binding.textError.visibility = View.VISIBLE
                android.util.Log.e("EPGActivity", "Errore EPG: $error")
            } else {
                binding.textError.visibility = View.GONE
            }
        }
        
        viewModel.currentChannel.observe(this) { channel ->
            channel?.let {
                // Aggiorna UI con canale corrente
                binding.textCurrentChannel.text = it.getDisplayName()
                android.util.Log.d("EPGActivity", "Canale corrente: ${it.getDisplayName()}")
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
        
        binding.buttonRefresh.setOnClickListener {
            viewModel.refreshEPG()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                navigateProgram(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateProgram(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateChannel(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateChannel(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Mostra dettagli programma selezionato
                showProgramDetails()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun navigateProgram(direction: Int) {
        // Navigazione tra programmi dello stesso canale
        val currentPosition = layoutManager.findFirstVisibleItemPosition()
        if (currentPosition >= 0) {
            val newPosition = currentPosition + direction
            if (newPosition >= 0 && newPosition < (viewModel.epgData.value?.size ?: 0)) {
                binding.recyclerViewEpg.smoothScrollToPosition(newPosition)
                // Evidenzia il programma selezionato
                epgAdapter.setSelectedPosition(newPosition)
            }
        }
    }
    
    private fun navigateChannel(direction: Int) {
        // Navigazione tra canali (cambia il filtro)
        viewModel.navigateChannel(direction)
    }
    
    private fun showProgramDetails() {
        // Mostra dettagli del programma selezionato
        val selectedPosition = epgAdapter.getSelectedPosition()
        if (selectedPosition >= 0) {
            val program = viewModel.epgData.value?.get(selectedPosition)
            program?.let {
                // Mostra dialog con dettagli programma
                showProgramDialog(it)
            }
        }
    }
    
    private fun showProgramDialog(program: EPGProgram) {
        // Implementa dialog per mostrare dettagli programma
        // Per ora mostra solo un toast
        android.widget.Toast.makeText(
            this,
            "${program.title}\n${program.description}\n${formatTime(program.startTime)} - ${formatTime(program.endTime)}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}