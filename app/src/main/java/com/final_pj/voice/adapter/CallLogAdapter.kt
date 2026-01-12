package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.CallUiItem
import com.final_pj.voice.feature.call.model.CallRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter(
    private val items: MutableList<CallUiItem>,
    private val onDetailClick: (CallRecord) -> Unit,
    private val onBlockClick: (CallRecord) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CallUiItem.DateHeader -> TYPE_HEADER
            is CallUiItem.CallRow -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val v = inflater.inflate(R.layout.item_call_date_header, parent, false)
                HeaderVH(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_call_log, parent, false) // ✅ 너 카드 레이아웃 파일명
                ItemVH(v)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CallUiItem.DateHeader -> (holder as HeaderVH).bind(item)
            is CallUiItem.CallRow -> (holder as ItemVH).bind(item.record)
        }
    }

    fun submit(newItems: List<CallUiItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tv_date_header)
        fun bind(item: CallUiItem.DateHeader) {
            tv.text = item.title
        }
    }

    inner class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.call_name)
        private val number = itemView.findViewById<TextView>(R.id.call_number)
        private val type = itemView.findViewById<TextView>(R.id.call_type)
        private val time = itemView.findViewById<TextView>(R.id.call_time)
        private val more = itemView.findViewById<ImageButton>(R.id.btn_more)

        fun bind(record: CallRecord) {
            name.text = record.name ?: "알 수 없음"
            number.text = record.phoneNumber ?: ""
            type.text = record.callType ?: "-"
            time.text = timeFormat.format(Date(record.date))

            itemView.setOnClickListener { onDetailClick(record) }
            more.setOnClickListener { onBlockClick(record) }
        }
    }
}

