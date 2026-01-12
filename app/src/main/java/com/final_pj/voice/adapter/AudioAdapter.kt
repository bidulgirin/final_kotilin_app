package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.model.AudioItem
import com.google.android.material.button.MaterialButton
import java.io.File

class AudioAdapter(
    private val items: MutableList<AudioItem>,
    private val onPlay: (AudioItem) -> Unit,
    private val onSingleDelete: (AudioItem, Int) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, inSelectionMode: Boolean) -> Unit
) : RecyclerView.Adapter<AudioAdapter.VH>() {

    private var selectionMode = false
    private val selectedUris = LinkedHashSet<String>() // uri.toString()로 관리

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cb: CheckBox = itemView.findViewById(R.id.cbSelect)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val key = item.uri.toString()

        // 제목/서브 (모델 필드가 다를 수 있어 파일명 fallback 처리)
        val title = runCatching {
            // item.name 같은 게 있으면 그걸 쓰세요
            File(item.path).name
        }.getOrDefault("녹음 파일")

        holder.tvTitle.text = title
        holder.tvSub.text = item.path.ifBlank { item.uri.toString() }

        val checked = selectedUris.contains(key)

        // 선택모드 UI
        holder.cb.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.cb.isChecked = checked
        holder.btnDelete.visibility = if (selectionMode) View.GONE else View.VISIBLE

        holder.cb.setOnClickListener {
            toggleSelection(position)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(position)
            } else {
                onPlay(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                enterSelectionMode()
                toggleSelection(position) // 길게 누른 항목 자동 선택
            }
            true
        }

        holder.btnDelete.setOnClickListener {
            onSingleDelete(item, position)
        }
    }

    private fun enterSelectionMode() {
        selectionMode = true
        selectedUris.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedUris.size, selectionMode)
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedUris.clear()
        notifyDataSetChanged()
        onSelectionChanged(0, false)
    }

    private fun toggleSelection(position: Int) {
        val item = items[position]
        val key = item.uri.toString()

        if (selectedUris.contains(key)) selectedUris.remove(key) else selectedUris.add(key)

        notifyItemChanged(position)
        onSelectionChanged(selectedUris.size, selectionMode)

        // 아무것도 선택 안 남으면 자동 종료(원하면 유지해도 됨)
        if (selectionMode && selectedUris.isEmpty()) {
            exitSelectionMode()
        }
    }

    fun getSelectedItems(): List<AudioItem> {
        val set = selectedUris
        return items.filter { set.contains(it.uri.toString()) }
    }

    fun removeItems(toRemove: List<AudioItem>) {
        val removeKeys = toRemove.map { it.uri.toString() }.toSet()
        items.removeAll { removeKeys.contains(it.uri.toString()) }
        selectedUris.removeAll(removeKeys)
        notifyDataSetChanged()
        onSelectionChanged(selectedUris.size, selectionMode)

        if (selectionMode && items.isEmpty()) exitSelectionMode()
        if (selectionMode && selectedUris.isEmpty()) exitSelectionMode()
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }
}
