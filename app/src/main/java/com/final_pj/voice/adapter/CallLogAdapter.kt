package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.model.CallRecord

class CallLogAdapter(
    private val callRecords: List<CallRecord>,
    private val onDetailClick: (CallRecord) -> Unit,
    private val onBlockClick: (CallRecord) -> Unit // 추가: 차단 클릭 콜백
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.call_name)
        val numberText: TextView = itemView.findViewById(R.id.call_number)
        val type: TextView = itemView.findViewById(R.id.call_type)
        val summaryText: TextView = itemView.findViewById(R.id.call_summary)
        val moreBtn: ImageButton = itemView.findViewById(R.id.btn_more) // 차단버튼
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val record = callRecords[position]

        holder.nameText.text = record.name ?: record.phoneNumber
        holder.numberText.text = record.phoneNumber ?: ""
        holder.type.text = record.callType

        // 상세보기 (기존 유지)
        holder.summaryText.setOnClickListener {
            onDetailClick(record)
        }

        // (선택) 타입별 강조
        when (record.callType) {
            "수신" -> holder.type.setTextColor(0xFF2E7D32.toInt())
            "발신" -> holder.type.setTextColor(0xFF1565C0.toInt())
            "부재중" -> holder.type.setTextColor(0xFFC62828.toInt())
            "거절" -> holder.type.setTextColor(0xFF000000.toInt())
            else -> holder.type.setTextColor(0xFF444444.toInt())
        }

        // 메뉴
        holder.moreBtn.setOnClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            //popup.menu.add(0, R.id.menu_block, 0, "차단") // menu xml 없이도 가능
            popup.menuInflater.inflate(R.menu.call_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_block -> {
                        onBlockClick(record)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount() = callRecords.size
}
