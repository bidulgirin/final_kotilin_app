package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.final_pj.voice.R
import com.final_pj.voice.model.AudioItem

// 오디오 파일 리스트를 RecyclerView에 보여주기 위한 Adapter
class AudioAdapter(
    // 화면에 표시할 AudioItem 리스트
    private var items: List<AudioItem>,

    // 아이템 클릭 시 실행할 콜백 함수
    private val onClick: (AudioItem) -> Unit
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    // ViewHolder
    inner class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        // 녹음 파일 이름 TextView
        val name: TextView = view.findViewById(R.id.tvName)

        // 녹음 길이 TextView
        val duration: TextView = view.findViewById(R.id.tvDuration)
    }

    // ViewHolder 생성
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AudioViewHolder {

        // item_audio.xml inflate
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)

        return AudioViewHolder(view)
    }

    // 데이터 바인딩
    override fun onBindViewHolder(
        holder: AudioViewHolder,
        position: Int
    ) {
        val item = items[position]

        holder.name.text = item.displayName
        holder.duration.text = formatDuration(item.duration)

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<AudioItem>) {
        items = newList
        notifyDataSetChanged()
    }

    // ms → mm:ss 변환
    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return "${sec / 60}:${"%02d".format(sec % 60)}"
    }
}
