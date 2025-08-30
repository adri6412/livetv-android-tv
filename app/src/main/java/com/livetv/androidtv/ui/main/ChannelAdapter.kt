package com.livetv.androidtv.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.livetv.androidtv.R
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.databinding.ItemChannelBinding

// Data class per tenere insieme canale e programma corrente
data class ChannelWithProgram(
    val channel: Channel,
    val currentProgram: EPGProgram?
)

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit
) : ListAdapter<ChannelWithProgram, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {
    
    private var selectedPosition = 0
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }
    
    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }
    
    // Metodo per aggiornare i programmi EPG
    fun updatePrograms(channels: List<Channel>, programs: List<EPGProgram>) {
        try {
            val currentTime = System.currentTimeMillis()
            val channelsWithPrograms = channels.map { channel ->
                val currentProgram = programs.find { program ->
                    program.channelId == channel.id && 
                    currentTime >= program.startTime && 
                    currentTime < program.endTime
                }
                ChannelWithProgram(channel, currentProgram)
            }
            submitList(channelsWithPrograms)
        } catch (e: Exception) {
            android.util.Log.e("ChannelAdapter", "Errore nell'aggiornamento programmi", e)
            // In caso di errore, passa una lista vuota
            submitList(emptyList())
        }
    }
    
    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                try {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        if (item != null) {
                            setSelectedPosition(position)
                            onChannelClick(item.channel)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChannelAdapter", "Errore nel click listener", e)
                }
            }
            
            // Migliora la gestione del focus per la navigazione TV
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }
        
        fun bind(channelWithProgram: ChannelWithProgram, isSelected: Boolean) {
            try {
                val channel = channelWithProgram.channel
                val currentProgram = channelWithProgram.currentProgram
                
                binding.apply {
                    // Numero e nome canale
                    textChannelNumber.text = if (channel.number > 0) {
                        channel.number.toString().padStart(3, '0')
                    } else {
                        "---"
                    }
                    
                    textChannelName.text = channel.name ?: "Nome non disponibile"
                    
                    // Gruppo
                    textChannelGroup.text = channel.group ?: "Generale"
                    
                    // Qualità
                    textChannelQuality.text = try {
                        channel.getQualityInfo()
                    } catch (e: Exception) {
                        "N/A"
                    }
                    
                    // Logo del canale
                    if (!channel.logoUrl.isNullOrEmpty()) {
                        try {
                            Glide.with(imageChannelLogo.context)
                                .load(channel.logoUrl)
                                .placeholder(R.drawable.ic_launcher)
                                .error(R.drawable.ic_launcher)
                                .into(imageChannelLogo)
                        } catch (e: Exception) {
                            imageChannelLogo.setImageResource(R.drawable.ic_launcher)
                        }
                    } else {
                        imageChannelLogo.setImageResource(R.drawable.ic_launcher)
                    }
                    
                    // Icona preferito
                    imageFavorite.visibility = if (channel.isFavorite) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                    
                    // Badge HD
                    textHD.visibility = if (channel.isHD) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                    
                    // Icona HbbTV
                    imageHbbTV.visibility = try {
                        if (channel.hasHbbTV()) {
                            android.view.View.VISIBLE
                        } else {
                            android.view.View.GONE
                        }
                    } catch (e: Exception) {
                        android.view.View.GONE
                    }
                    
                    // Indicatore di stato (sempre online per ora)
                    try {
                        viewStatusIndicator.setBackgroundColor(
                            itemView.context.getColor(R.color.status_online)
                        )
                    } catch (e: Exception) {
                        // Ignora errori di colore
                    }
                    
                    // Mostra programma corrente se disponibile
                    if (currentProgram != null && currentProgram.title.isNotEmpty()) {
                        textCurrentProgram.text = currentProgram.title
                        textCurrentProgram.visibility = android.view.View.VISIBLE
                        android.util.Log.d("ChannelAdapter", "Canale ${channel.name}: programma corrente = ${currentProgram.title}")
                    } else {
                        textCurrentProgram.visibility = android.view.View.GONE
                        android.util.Log.d("ChannelAdapter", "Canale ${channel.name}: nessun programma corrente")
                    }
                    
                    // Evidenzia il canale selezionato
                    root.isSelected = isSelected
                }
            } catch (e: Exception) {
                android.util.Log.e("ChannelAdapter", "Errore nel binding del canale", e)
                // In caso di errore, mostra dati di fallback
                binding.textChannelName.text = "Errore caricamento"
                binding.textChannelNumber.text = "---"
            }
        }
    }
    
    class ChannelDiffCallback : DiffUtil.ItemCallback<ChannelWithProgram>() {
        override fun areItemsTheSame(oldItem: ChannelWithProgram, newItem: ChannelWithProgram): Boolean {
            return try {
                oldItem.channel.id == newItem.channel.id
            } catch (e: Exception) {
                false
            }
        }
        
        override fun areContentsTheSame(oldItem: ChannelWithProgram, newItem: ChannelWithProgram): Boolean {
            return try {
                oldItem == newItem
            } catch (e: Exception) {
                false
            }
        }
    }
}