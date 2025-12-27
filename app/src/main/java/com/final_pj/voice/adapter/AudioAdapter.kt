package com.final_pj.voice.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView

import com.final_pj.voice.R
import com.final_pj.voice.databinding.ItemAudioBinding
import com.final_pj.voice.model.AudioItem

// 오디오 파일 리스트를 RecyclerView에 보여주기 위한 Adapter
class AudioAdapter(
    private val items: List<AudioItem>,
    private val onClick: (AudioItem) -> Unit
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    private var selectedPosition = -1

    inner class AudioViewHolder(val binding: ItemAudioBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val binding = ItemAudioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AudioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.title
        holder.binding.tvDuration.text = formatDuration(item.duration)
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition) Color.LTGRAY else Color.TRANSPARENT
        )

        holder.itemView.setOnClickListener {
            selectedPosition = position
            notifyDataSetChanged()
            onClick(item)
        }
    }

    override fun getItemCount() = items.size

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

