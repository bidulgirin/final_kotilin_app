package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.model.CallRecord

class CallLogAdapter(
    private val callRecords: List<CallRecord>,
    private val onDetailClick: (CallRecord) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.call_name)
        val numberText: TextView = itemView.findViewById(R.id.call_number)
        val summaryText: TextView = itemView.findViewById(R.id.call_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val record = callRecords[position]
        holder.nameText.text = record.name ?: "알 수 없음"
        holder.numberText.text = record.phoneNumber
        holder.summaryText.text = if (record.isSummaryDone) "자세히보기" else "요약중..."

        holder.summaryText.setOnClickListener {
            if (record.isSummaryDone) {
                onDetailClick(record)
            }
        }
    }

    override fun getItemCount() = callRecords.size
}
