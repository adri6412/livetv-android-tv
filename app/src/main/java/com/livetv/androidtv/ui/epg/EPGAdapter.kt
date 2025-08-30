package com.livetv.androidtv.ui.epg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.databinding.ItemEpgProgramBinding
import java.text.SimpleDateFormat
import java.util.*

class EPGAdapter(
    private val onProgramClick: (EPGProgram) -> Unit
) : ListAdapter<EPGProgram, EPGAdapter.EPGViewHolder>(EPGProgramDiffCallback()) {
    
    private var selectedPosition = -1
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EPGViewHolder {
        val binding = ItemEpgProgramBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EPGViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: EPGViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }
    
    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }
    
    fun getSelectedPosition(): Int = selectedPosition
    
    inner class EPGViewHolder(
        private val binding: ItemEpgProgramBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onProgramClick(getItem(position))
                    setSelectedPosition(position)
                }
            }
        }
        
        fun bind(program: EPGProgram, isSelected: Boolean) {
            binding.root.isSelected = isSelected
            
            // Imposta il focus se è selezionato
            if (isSelected) {
                binding.root.requestFocus()
            }
            
            // Titolo del programma
            binding.textTitle.text = program.title
            
            // Descrizione (se disponibile)
            if (!program.description.isNullOrEmpty()) {
                binding.textDescription.text = program.description
                binding.textDescription.visibility = android.view.View.VISIBLE
            } else {
                binding.textDescription.visibility = android.view.View.GONE
            }
            
            // Orario
            val timeText = "${formatTime(program.startTime)} - ${formatTime(program.endTime)}"
            binding.textTime.text = timeText
            
            // Durata
            val duration = program.endTime - program.startTime
            val durationText = formatDuration(duration)
            binding.textDuration.text = durationText
            
            // Categoria
            if (!program.category.isNullOrEmpty()) {
                binding.textCategory.text = program.category
                binding.textCategory.visibility = android.view.View.VISIBLE
            } else {
                binding.textCategory.visibility = android.view.View.GONE
            }
            
            // Evidenzia il programma corrente
            if (program.isCurrentlyAiring()) {
                binding.root.setBackgroundResource(com.livetv.androidtv.R.color.program_current)
                binding.textCurrent.visibility = android.view.View.VISIBLE
            } else {
                binding.root.setBackgroundResource(com.livetv.androidtv.R.color.program_normal)
                binding.textCurrent.visibility = android.view.View.GONE
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatter.format(date)
        }
        
        private fun formatDuration(durationMs: Long): String {
            val minutes = durationMs / (1000 * 60)
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "${hours}h ${remainingMinutes}m" else "${hours}h"
            } else {
                "${minutes}m"
            }
        }
    }
    
    private class EPGProgramDiffCallback : DiffUtil.ItemCallback<EPGProgram>() {
        override fun areItemsTheSame(oldItem: EPGProgram, newItem: EPGProgram): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: EPGProgram, newItem: EPGProgram): Boolean {
            return oldItem == newItem
        }
    }
}
