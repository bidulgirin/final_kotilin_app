package com.final_pj.voice.feature.call.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.databinding.ItemCallDateHeaderBinding
import com.final_pj.voice.databinding.ItemCallLogBinding
import com.final_pj.voice.feature.call.fragment.CallUiItem
import com.final_pj.voice.feature.call.model.CallRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter(
    private val items: MutableList<CallUiItem>,
    private val isSummaryFeatureEnabled: () -> Boolean, // 설정 확인 함수 주입 (요청 사항 2)
    private val onDetailClick: (CallRecord) -> Unit,
    private val onBlockClick: (CallRecord) -> Unit,
    private val onCallClick: (String) -> Unit,
    private val onDeleteClick: (CallRecord) -> Unit,
    private val onReportClick: (CallRecord) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CallUiItem.DateHeader -> TYPE_HEADER
            is CallUiItem.CallRow -> TYPE_ITEM
        }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemCallDateHeaderBinding.inflate(inflater, parent, false))
            else -> ItemVH(ItemCallLogBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CallUiItem.DateHeader -> (holder as HeaderVH).bind(item)
            is CallUiItem.CallRow -> (holder as ItemVH).bind(item.record)
        }
    }

    fun submitItems(newItems: List<CallUiItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class HeaderVH(
        private val binding: ItemCallDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CallUiItem.DateHeader) {
            binding.tvDateHeader.text = item.title
        }
    }

    inner class ItemVH(
        private val binding: ItemCallLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: CallRecord) {
            val phone = record.phoneNumber.orEmpty()

            binding.callName.text = record.name ?: phone
            binding.callNumber.text = phone
            binding.callType.text = record.callType.orEmpty()
            binding.callTime.text = timeFormat.format(Date(record.date))

            // --- 리포트 UI 제어 로직 시작 (요청 사항 1, 2, 3 반영) ---
            val isSummaryFeatureOn = isSummaryFeatureEnabled()
            
            // 루트 뷰 클릭은 항상 onDetailClick로 연결 (전체 아이템 클릭 시 상세 보기)
            binding.root.setOnClickListener { onDetailClick(record) }

            if (!isSummaryFeatureOn) {
                // 1. 요약 기능이 꺼져 있으면 요약/리포트 버튼 숨김
                binding.callSummary.isVisible = false
            } else {
                // 2. 요약 기능이 켜져 있는 경우, 요약 버튼 표시 상태 제어
                binding.callSummary.isVisible = true
                
                when {
                    // 3. 요약이 완료되었고 내용이 있는 경우: 리포트 보기
                    record.isSummaryDone && !record.summary.isNullOrEmpty() -> {
                        binding.callSummary.text = "리포트보기"
                        binding.callSummary.isEnabled = true
                        // 리포트 버튼 클릭 시 onDetailClick 호출 (루트 클릭과 동일)
                        binding.callSummary.setOnClickListener { onDetailClick(record) }
                    }
                    // 4. 요약 중인 경우: 요약 중... 표시 및 비활성화
                    !record.isSummaryDone && record.summary != null -> {
                        binding.callSummary.text = "리포트 요약 중..."
                        binding.callSummary.isEnabled = false // 클릭 비활성화
                        binding.callSummary.setOnClickListener(null) // 클릭 리스너 제거
                    }
                    // 5. 그 외: 아직 요약이 시작되지 않았거나 내용이 없는 경우
                    else -> {
                        binding.callSummary.isVisible = false // 숨김
                    }
                }
            }
            // --- 리포트 UI 제어 로직 끝 ---

            // ✅ 통화 버튼은 그대로
            binding.btnCall.isEnabled = phone.isNotBlank()
            binding.btnCall.alpha = if (phone.isNotBlank()) 1f else 0.3f
            binding.btnCall.setOnClickListener {
                if (phone.isNotBlank()) onCallClick(phone)
            }

            // ✅ 더보기 -> 팝업 메뉴(신고/차단/삭제)
            binding.btnMore.setOnClickListener { anchor ->
                showMoreMenu(anchor, record)
            }
        }

        private fun showMoreMenu(anchor: android.view.View, record: CallRecord) {
            PopupMenu(anchor.context, anchor).apply {
                menuInflater.inflate(R.menu.call_item_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_report -> {
                            onReportClick(record)
                            true
                        }
                        R.id.menu_block -> {
                            onBlockClick(record)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(record)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }
}
