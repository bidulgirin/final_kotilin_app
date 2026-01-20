package com.final_pj.voice.feature.call.fragment

import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.final_pj.voice.feature.report.network.RetrofitClient
import com.final_pj.voice.feature.report.network.dto.VoicePhisingCreateReq
import com.final_pj.voice.feature.report.network.dto.VoicePhisingOutRes

class DetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }



    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.detail_name)
        val tvSub = view.findViewById<TextView>(R.id.detail_number)

        val chipStatus = view.findViewById<Chip>(R.id.chipStatus)
        val chipCategory = view.findViewById<Chip>(R.id.chipCategory)
        // 변경된 점수 칩들
        val chipDeepScore = view.findViewById<Chip>(R.id.chipDeepScore)
        val chipKoberScore = view.findViewById<Chip>(R.id.chipKoberScore)
        
        val chipGroupKeywords = view.findViewById<ChipGroup>(R.id.chipGroupKeywords)
        val keywordCard = view.findViewById<CardView>(R.id.keywordCard)

        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        val tvText = view.findViewById<TextView>(R.id.tvText)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)
        // 신고 버튼
        val btnOpenReportView = view.findViewById<Button>(R.id.report_button)

        val callId = arguments?.getLong("call_id") ?: run {
            Log.e("c", "callId is null")
            return
        }

        val passedNumber = arguments?.getString("phone_number")

        // 번호 정규화
        fun normalizePhone(raw: String): String {
            return raw.trim().replace(Regex("[^0-9+]"), "")
        }


        val toolbar = view.findViewById<MaterialToolbar>(R.id.detailToolbar)
        toolbar.setNavigationOnClickListener {
            runCatching { findNavController().navigateUp() }
                .getOrElse {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
        }

        val app = requireContext().applicationContext as App

        val btnOpenChatbot = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenChatbot)

        btnOpenChatbot.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    app.db.sttSummaryDao().getById(callId.toString())
                }
                val summary = tvSummary.text?.toString().orEmpty()
                val text = tvText.text?.toString().orEmpty()



                val keywords = result?.keywords

                val bundle = Bundle().apply {
                    putLong("CALL_ID", callId)
                    putString("SUMMARY_TEXT", summary)
                    putString("CALL_TEXT", text) // 잠시 교체
                    if (keywords != null) {
                        putStringArrayList("KEYWORDS", ArrayList(keywords))
                    }
                }

                findNavController().navigate(R.id.action_detailFragment_to_chatbotFragment, bundle)
            }
        }

        suspend fun postVoicePhisingNumber(
            number: String,
            description: String?
        ): VoicePhisingOutRes {

            val api = RetrofitClient.voicePhisingApi
            val req = VoicePhisingCreateReq(
                number = normalizePhone(number),
                description = description?.takeIf { it.isNotBlank() }
            )

            val res = api.insertNumber(req)

            if (res.isSuccessful) {
                return res.body() ?: throw IllegalStateException("응답 바디가 비어있습니다.")
            }

            if (res.code() == 409) {
                throw IllegalStateException("이미 등록된 번호입니다.")
            }

            val err = res.errorBody()?.string()
            throw IllegalStateException("서버 오류 (${res.code()}): ${err ?: "unknown"}")
        }



        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                app.db.sttSummaryDao().getById(callId.toString())
            }
            Log.d("DetailFragment", "DB result: $result")

            if (result == null) {
                tvTitle.text = "통화 상세"
                tvSub.text = "callId: $callId"
                tvSummary.text = ""
                tvText.text = ""
                chipStatus.text = "결과없음"
                chipCategory.visibility = View.GONE
                chipDeepScore.visibility = View.GONE
                chipKoberScore.visibility = View.GONE
                keywordCard.visibility = View.GONE
                tvMeta.text = ""
                return@launch
            }

            tvTitle.text = "통화 상세"
            tvSub.text = "callId: ${result.callId}"


//            val exampleText = """
//                    - 서울지방남부경찰청 범죄수익환수에 김*규 사무관입니다.
//
//                    - 본인은 ****년 **월 **일생 이*민씨 본인 맞습니까?
//
//                    - 주범 김*호씨랑 관계가 어떻게 되십니까?
//
//                    - 금융감독원에서 본인이 이용중이신 금융권 진술하실 때 계좌번호 및 비밀번호 개인정보는 말씀하시는게 아니고 적금, 예금, 신용카드, 가상화폐 등등 보유중인 금액만 말씀해주시면 됩니다.
//
//                    - 만약에 오차범위가 클 경우에는 저희가 범죄수익금에 은닉한다고 간주가 될 수 있으시니까 사실의 근거해서 얘기해주세요.
//
//                    - 지금 진술해주신 대로 정보 정리해서 담당 검사님한테 이관처리해드릴겁니다.
//
//                    - 전화 연결되시면 본인 성함, 생년월일, 사건번호 말씀해주시면 됩니다.
//                """.trimIndent()

            //tvText.text = exampleText

            tvText.text = (result.conversation as? List<*>)?.joinToString("\n\n") ?: result.conversation?.toString() ?: ""

            tvSummary.text = result.summary?.takeIf { it.isNotBlank() } ?: "요약 결과가 없습니다."

            val isVp = result.isVoicephishing ?: false
            chipStatus.text = if (isVp) "보이스피싱 의심" else "일반 통화"

            val cat = result.category
            if (isVp && !cat.isNullOrBlank()) {
                chipCategory.visibility = View.VISIBLE
                chipCategory.text = cat
            } else {
                chipCategory.visibility = View.GONE
            }

            // 딥보이스 점수 표시
            val dScore = result.deepvoiceScore
            if (dScore != null && dScore > 0.01) {
                chipDeepScore.visibility = View.VISIBLE
                val pct = (dScore * 100).roundToInt()
                chipDeepScore.text = "딥보이스: ${pct}%"
            } else {
                chipDeepScore.visibility = View.GONE
            }

            // 문맥분석 점수 표시
            val kScore = result.koberScore
            if (kScore != null && kScore > 0.01) {
                chipKoberScore.visibility = View.VISIBLE
                val pct = (kScore * 100).roundToInt()
                chipKoberScore.text = "문맥분석: ${pct}%"
            } else {
                chipKoberScore.visibility = View.GONE
            }

            val keywords = result.keywords
            if (keywords.isNullOrEmpty()) {
                keywordCard.visibility = View.GONE
            } else {
                keywordCard.visibility = View.VISIBLE
                chipGroupKeywords.removeAllViews()
                keywords.forEach { keyword ->
                    val chip = Chip(requireContext()).apply {
                        text = keyword
                        isClickable = false
                        isCheckable = false
                    }
                    chipGroupKeywords.addView(chip)
                }
            }

            tvMeta.text = "createdAt: ${result.createdAt}"
        }



        fun confirmReport(number: String, description: String) {
            AlertDialog.Builder(requireContext())
                .setTitle("신고하기")
                .setMessage("$number 제보하시겠습니까?")
                .setPositiveButton("예", null)
                .setNegativeButton("아니오") { d, _ -> d.dismiss() }
                .create()
                .also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val normalized = normalizePhone(number)
                            val descOrNull = description
                                .takeIf { it != "- 선택 -" }
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }

                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    val out = withContext(Dispatchers.IO) {
                                        postVoicePhisingNumber(normalized, descOrNull)
                                    }

                                    Toast.makeText(requireContext(), "신고가 접수되었습니다.", Toast.LENGTH_SHORT).show()
                                    Log.d("DetailFragment", "신고 성공: id=${out.id}, number=${out.number}")
                                    dialog.dismiss()

                                } catch (e: IllegalStateException) {
                                    Log.e("DetailFragment", "신고 실패(논리): ${e.message}", e)
                                    Toast.makeText(requireContext(), e.message ?: "실패", Toast.LENGTH_SHORT).show()

                                } catch (e: Exception) {
                                    Log.e("DetailFragment", "신고 실패(예외)", e)
                                    Toast.makeText(requireContext(), "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    dialog.show()
                }
        }


        fun showSaveDialog(defaultPhone: String) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.save_report_contact, null)

            val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
            val etDescription = dialogView.findViewById<Spinner>(R.id.report_reason_spinner)

            etPhone.setText(defaultPhone)

            val spinner = dialogView.findViewById<Spinner>(R.id.report_reason_spinner)
            val data = listOf("- 선택 -","광고/마케팅","기관사칭","금융사기","가족/지인 사칭")

            val adapter = ArrayAdapter(requireContext(),R.layout.report_reason_item, data)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("신고하기")
                .setView(dialogView)
                .setNegativeButton("취소", null)
                .setPositiveButton("확인", null)
                .create()


            dialog.setOnShowListener {
                val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveBtn.setOnClickListener {
                    val phone = etPhone.text.toString().trim()
                    val description = etDescription.selectedItem.toString().trim()

                    if (phone.isEmpty()) {
                        etPhone.error = "연락처를 입력하세요"
                        return@setOnClickListener
                    }
                    confirmReport(phone, description)
                    dialog.dismiss()
                }
            }

            dialog.show()
        }

        btnOpenReportView.setOnClickListener {
            val defaultPhone = passedNumber?.takeIf { it.isNotBlank() }
                ?: run {
                    Toast.makeText(requireContext(), "전화번호 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

            showSaveDialog(defaultPhone)
        }
    }


}