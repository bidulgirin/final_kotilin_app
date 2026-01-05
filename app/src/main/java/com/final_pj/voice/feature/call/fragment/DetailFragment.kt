package com.final_pj.voice.feature.call.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.feature.chatbot.fragment.ChatbotFragment
import com.google.android.material.appbar.MaterialToolbar

class DetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.detailToolbar)
        toolbar.setNavigationOnClickListener {
            // NavController 쓰는 경우
            runCatching { findNavController().navigateUp() }
                .getOrElse {
                    // NavController가 아니면(수동 FragmentTransaction) fallback
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
        }

        // 신고 버튼
        val btnOpenChatbot = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenChatbot)
        btnOpenChatbot.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.nav_host, ChatbotFragment())
                .addToBackStack(null)
                .commit()
        }


        val callId = arguments?.getLong("call_id") ?: run {
            Log.e("DetailFragment", "callId is null")
            return
        }

        val tvTitle = view.findViewById<TextView>(R.id.detail_name)
        val tvSub = view.findViewById<TextView>(R.id.detail_number)

        val chipStatus = view.findViewById<Chip>(R.id.chipStatus)
        val chipCategory = view.findViewById<Chip>(R.id.chipCategory)
        val chipScore = view.findViewById<Chip>(R.id.chipScore)

        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        val tvText = view.findViewById<TextView>(R.id.tvText)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)

        val app = requireContext().applicationContext as App

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                app.db.SttResultDao().getById(callId.toString())
            }

            if (result == null) {
                tvTitle.text = "통화 상세"
                tvSub.text = "callId: $callId"
                tvSummary.text = "결과를 찾을 수 없습니다."
                tvText.text = ""
                chipStatus.text = "N/A"
                chipCategory.visibility = View.GONE
                chipScore.visibility = View.GONE
                tvMeta.text = ""
                return@launch
            }

            tvTitle.text = "통화 상세"
            tvSub.text = "callId: ${result.callId}"

            // 원문
            tvText.text = result.text

            // 요약
            tvSummary.text = result.summary?.takeIf { it.isNotBlank() } ?: "요약 결과가 없습니다."

            // 보이스피싱 여부
            val isVp = result.isVoicephishing ?: false
            chipStatus.text = if (isVp) "보이스피싱 의심" else "일반 통화"

            // 카테고리
            val cat = result.category
            if (isVp && !cat.isNullOrBlank()) {
                chipCategory.visibility = View.VISIBLE
                chipCategory.text = cat
            } else {
                chipCategory.visibility = View.GONE
            }

            // 점수
            val score = result.voicephishingScore
            if (score != null) {
                chipScore.visibility = View.VISIBLE
                // 0.0~1.0 점수 가정 → 퍼센트로 표시
                val pct = (score * 100).roundToInt()
                chipScore.text = "점수 $pct%"
            } else {
                chipScore.visibility = View.GONE
            }

            tvMeta.text = "createdAt: ${result.createdAt}"
        }
    }


}
