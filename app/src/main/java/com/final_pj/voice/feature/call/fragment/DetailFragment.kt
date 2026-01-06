package com.final_pj.voice.feature.call.fragment

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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.chatbot.fragment.ChatbotFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.widget.AdapterView
import com.final_pj.voice.databinding.ActivityMainBinding

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


        // 챗봇버튼
        val btnOpenChatbot = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenChatbot)
        btnOpenChatbot.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.nav_host, ChatbotFragment())
                .addToBackStack(null)
                .commit()
        }

        // --------------------
        // 절차
        // 1. 신고 버튼 누르면 화면 나옴 (number은 자동입력 + 신고 사유 선택해야함 )
        // 2. 화면안에 직접 정말 신고하시겠습니까? 예
        // 3. 백엔드 통신 post 로 보내면 됨
        // --------------------

        // 신고 버튼
        val btnOpenReportView = view.findViewById<Button>(R.id.report_button)


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
                tvSummary.text = "-"
                tvText.text = "-"
                chipStatus.text = "결과없음"
                chipCategory.visibility = View.GONE
                chipScore.visibility = View.GONE
                tvMeta.text = "-"
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


        // 진짜 신고할꺼임? 물어보는거
        fun confirmReport(number: String, description: String) {
            AlertDialog.Builder(requireContext())
                .setTitle("신고하기")
                .setMessage("$number 제보하시겠습니까?")
                .setPositiveButton("예") { d, _ ->
                    // 실제 백엔드 통신
                    try{
                        Log.d("test", "신고가 되었습니다~~${number} ${description}")
                        // 백엔드 통신 + 신고 목록에 넣기

                    }catch (e: Error){
                        Log.e("Error", "${e}")
                    }

                    d.dismiss()
                }
                .setNegativeButton("아니오") { d, _ -> d.dismiss() }
                .show()
        }


        // 다이얼 로그 화면 나와~~~!!
        fun showSaveDialog(defaultPhone: String) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.save_report_contact, null)

            val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
            //val etDescription = dialogView.findViewById<EditText>(R.id.etDialogDescription)
            // 이제 값을 연결해야함
            val etDescription = dialogView.findViewById<Spinner>(R.id.report_reason_spinner)

            // 기본값: 다이얼 입력 번호
            etPhone.setText(defaultPhone)


            //------------
            //신고 사유 뿌리기~~~~
            //------------
            val spinner = dialogView.findViewById<Spinner>(R.id.report_reason_spinner)
            val data = listOf("- 선택 -","광고/마케팅","기관사칭","금융사기","가족/지인 사칭")

            val adapter = ArrayAdapter(requireContext(),R.layout.report_reason_item, data)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("신고하기")
                .setView(dialogView)
                .setNegativeButton("취소", null)
                .setPositiveButton("확인", null) // 아래에서 커스텀 클릭 처리
                .create()


            dialog.setOnShowListener {
                val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveBtn.setOnClickListener {
                    val phone = etPhone.text.toString().trim()
                    //val description = etDescription.text.toString().trim()
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

        // 신고 저장!!!!!!
        fun saveContact( number: String, description: String) {
            val ops = ArrayList<ContentProviderOperation>()

            // RawContact 생성 (이건 뭐여 )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // 전화번호 저장
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // 설명 저장(필수는아닌데...필수로해야하나?)
            if (description.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, description)
                        .build()
                )
            }

            try {
                requireContext().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                Toast.makeText(requireContext(), "연락처가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        // 신고하기 버튼 이벤트
        btnOpenReportView.setOnClickListener {
            showSaveDialog("010-4909-9553") // 테스트용
        }

        // 백엔드랑 통신하는 함수
        fun saveReportBackend(){
        }

    }


}
