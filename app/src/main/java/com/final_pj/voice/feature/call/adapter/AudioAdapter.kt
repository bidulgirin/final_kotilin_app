package com.final_pj.voice.feature.call.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.PopupMenu
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
    private val onExport: (AudioItem) -> Unit, // 추가
    private val onSelectionChanged: (selectedCount: Int, inSelectionMode: Boolean) -> Unit
) : RecyclerView.Adapter<AudioAdapter.VH>() {

    private var selectionMode = false
    private val selectedUris = LinkedHashSet<String>()

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cb: CheckBox = itemView.findViewById(R.id.cbSelect)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        val btnMore: MaterialButton = itemView.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val key = item.uri.toString()

        val title = runCatching { File(item.path).name }.getOrDefault("녹음 파일")

        holder.tvTitle.text = title
        holder.tvSub.text = item.path.ifBlank { item.uri.toString() }

        val checked = selectedUris.contains(key)

        holder.cb.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.cb.isChecked = checked
        holder.btnMore.visibility = if (selectionMode) View.GONE else View.VISIBLE

        holder.cb.setOnClickListener { toggleSelection(position) }

        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(position) else onPlay(item)
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                enterSelectionMode()
                toggleSelection(position)
            }
            true
        }

        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add("공유/추출")
            popup.menu.add("삭제")
            popup.setOnMenuItemClickListener { menu ->
                when (menu.title) {
                    "공유/추출" -> onExport(item)
                    "삭제" -> onSingleDelete(item, position)
                }
                true
            }
            popup.show()
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
        if (selectionMode && selectedUris.isEmpty()) exitSelectionMode()
    }

    fun getSelectedItems(): List<AudioItem> = items.filter { selectedUris.contains(it.uri.toString()) }

    fun removeItems(toRemove: List<AudioItem>) {
        val removeKeys = toRemove.map { it.uri.toString() }.toSet()
        items.removeAll { removeKeys.contains(it.uri.toString()) }
        selectedUris.removeAll(removeKeys)
        notifyDataSetChanged()
        onSelectionChanged(selectedUris.size, selectionMode)
        if (selectionMode && (items.isEmpty() || selectedUris.isEmpty())) exitSelectionMode()
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getItems(): List<AudioItem> = items.toList()

    @SuppressLint("NotifyDataSetChanged")
    fun clearItems() {
        items.clear()
        selectedUris.clear()
        notifyDataSetChanged()
        if (selectionMode) {
            selectionMode = false
            onSelectionChanged(0, false)
        }
    }
}
