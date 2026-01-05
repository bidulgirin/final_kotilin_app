package com.final_pj.voice.feature.blocklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R

class BlockListAdapter(
    private val items: MutableList<String>,
    private val onUnblock: (String) -> Unit
) : RecyclerView.Adapter<BlockListAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number = v.findViewById<TextView>(R.id.tv_number)
        val btn = v.findViewById<View>(R.id.btn_unblock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_block_number, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        holder.number.text = n
        holder.btn.setOnClickListener { onUnblock(n) }
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newList: List<String>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}