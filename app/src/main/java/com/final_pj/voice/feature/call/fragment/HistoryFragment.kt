package com.final_pj.voice.feature.call.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.blocklist.service.BlocklistCache
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.activity.CallingActivity
import com.final_pj.voice.feature.call.adapter.CallLogAdapter
import com.final_pj.voice.feature.call.model.CallRecord
import com.final_pj.voice.feature.report.network.RetrofitClient
import com.final_pj.voice.feature.report.network.dto.VoicePhisingCreateReq
import com.final_pj.voice.feature.report.network.dto.VoicePhisingOutRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var adapter: CallLogAdapter
    private val uiItems = mutableListOf<CallUiItem>()
    private val callRecords = mutableListOf<CallRecord>()

    private val REQ_CONTACTS = 1001
    private val REQ_CALL = 1002

    private var pendingCallNumber: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callLogObserver: ContentObserver? = null

    // 디바운스용 Job
    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onStart() {
        super.onStart()
        registerCallLogObserver()
    }

    override fun onStop() {
        unregisterCallLogObserver()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 최신화
        reloadAllCallLogs()
    }

    private val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
    private val headerFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA)

    private fun buildSectionedItems(records: List<CallRecord>): List<CallUiItem> {
        val out = mutableListOf<CallUiItem>()
        var lastKey: String? = null

        for (r in records) {
            val key = dayKeyFormat.format(Date(r.date))
            if (key != lastKey) {
                lastKey = key
                out.add(CallUiItem.DateHeader(headerFormat.format(Date(r.date))))
            }
            out.add(CallUiItem.CallRow(r))
        }
        return out
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.history_list)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter = CallLogAdapter(
            uiItems,
            onDetailClick = { record ->
                // summary가 있을 때만 detailFragment로 이동
                if (!record.summary.isNullOrEmpty()) {
                    val bundle = Bundle().apply {
                        putLong("call_id", record.id)
                        putString("phone_number", record.phoneNumber)
                    }
                    findNavController().navigate(R.id.detailFragment, bundle)
                } else {
                    // 요약이 없는 경우 클릭 시 피드백 (선택 사항)
                    Toast.makeText(requireContext(), "분석된 요약 내용이 없습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onBlockClick = { record ->
                showBlockConfirm(record)
            },
            onCallClick = { number ->
                callPhone(number) //  1. 전화 걸기 호출
            },
            onDeleteClick = { record ->
                showDeleteConfirm(record) // 2. 삭제 확인 창 호출
            },
            onReportClick = { record ->
                val number = record.phoneNumber ?: ""
                if (number.isNotBlank()) {
                    showReportDialog(number)
                } else {
                    Toast.makeText(requireContext(), "전화번호 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
        recycler.adapter = adapter

        reloadAllCallLogs()
    }

    // 1. 전화 걸기 로직
    // 전화 거는 화면으로 이동 (발신)
    private fun callPhone(number: String) {
        if (number.isNotEmpty()) {
            val intent = Intent(requireContext(), CallingActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", true)   // ⭐ 발신 표시
            }
            startActivity(intent)
        }
    }
    // 2. 삭제 확인 다이얼로그
    private fun showDeleteConfirm(record: CallRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("이 통화 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteCallLog(record)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 3. 실제 시스템 데이터베이스에서 삭제
    private fun deleteCallLog(record: CallRecord) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rows = requireContext().contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(record.id.toString())
                )

                withContext(Dispatchers.Main) {
                    if (rows > 0) {
                        Toast.makeText(context, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        // 삭제 후 리스트 새로고침은 ContentObserver가 감지하여 자동으로 reloadAllCallLogs()를 실행함
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun reloadAllCallLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) { queryCallLogs() } // List<CallRecord>
            val newUi = buildSectionedItems(records)                      // List<CallUiItem>
            adapter.submitItems(newUi)
        }
    }

    private fun registerCallLogObserver() {
        if (callLogObserver != null) return

        callLogObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                refreshCallLogsDebounced()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                refreshCallLogsDebounced()
            }
        }

        requireContext().contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver!!
        )
    }


    private fun unregisterCallLogObserver() {
        callLogObserver?.let {
            requireContext().contentResolver.unregisterContentObserver(it)
        }
        callLogObserver = null
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun refreshCallLogsDebounced() {
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(400)
            reloadAllCallLogs()
        }
    }



    private suspend fun queryCallLogs(): List<CallRecord> {
        val result = mutableListOf<CallRecord>()
        val app = requireActivity().application as App
        val summaryDao = app.db.callSummaryDao()

        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        val tempRecords = mutableListOf<CallRecord>()
        val callIds = mutableListOf<String>()

        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0).toString() // Room의 callId가 String이므로 변환
                val name = c.getString(1)
                val number = c.getString(2)
                val typeInt = c.getInt(3)
                val date = c.getLong(4)

                tempRecords.add(
                    CallRecord(
                        id = id.toLong(),
                        name = name,
                        phoneNumber = number,
                        callType = mapCallType(typeInt),
                        date = date,
                        summary = null // 일단 비워둠
                    )
                )
                callIds.add(id)
            }
        }

        // [중요] DB에서 해당 ID들의 요약본을 한꺼번에 가져옴
        val summaries = summaryDao.getSummariesByCallIds(callIds)
        // 빠른 조회를 위해 Map으로 변환 (Key: callId, Value: summary)
        val summaryMap = summaries.associate { it.callId to it.summary }

        // 생성된 Record들에 summary 매핑
        return tempRecords.map { record ->
            record.copy(summary = summaryMap[record.id.toString()])
        }
    }


    // 이거 어디서 사용...하던데...
    //    private fun mapCallType(typeInt: Int): String =
    //        when (typeInt) {
    //            CallLog.Calls.INCOMING_TYPE -> "Incoming"
    //            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    //            CallLog.Calls.MISSED_TYPE -> "Missed"
    //            else -> "Unknown"
    //        }

    private fun mapCallType(typeInt: Int): String =
        when (typeInt) {
            CallLog.Calls.INCOMING_TYPE -> "수신"
            CallLog.Calls.OUTGOING_TYPE -> "발신"
            CallLog.Calls.MISSED_TYPE -> "부재중"
            CallLog.Calls.REJECTED_TYPE -> "거절"
            else -> "알 수 없음"
        }

    // 차단
    private fun showBlockConfirm(record: CallRecord) {
        val number = record.phoneNumber?.trim()
        if (number.isNullOrEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("차단")
            .setMessage("$number 를 차단 하시겠습니까?")
            .setPositiveButton("예") { dialog, _ ->
                blockNumber(number)
                dialog.dismiss()
            }
            .setNegativeButton("아니오") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun blockNumber(rawNumber: String) {
        val app = requireActivity().application as App

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = app.repo.add(rawNumber)
            if (ok) BlocklistCache.add(rawNumber)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    if (ok) "차단 목록에 추가됨" else "이미 차단되어 있거나 저장 실패",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --- 신고 기능 추가 ---

    private fun normalizePhone(raw: String): String {
        return raw.trim().replace(Regex("[^0-9+]"), "")
    }

    private suspend fun postVoicePhisingNumber(number: String, description: String?): VoicePhisingOutRes {
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

    private fun confirmReport(number: String, description: String) {
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
                                withContext(Dispatchers.IO) {
                                    postVoicePhisingNumber(normalized, descOrNull)
                                }
                                Toast.makeText(requireContext(), "신고가 접수되었습니다.", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } catch (e: IllegalStateException) {
                                Toast.makeText(requireContext(), e.message ?: "실패", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showReportDialog(defaultPhone: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.save_report_contact, null)

        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
        val spinner = dialogView.findViewById<Spinner>(R.id.report_reason_spinner)

        etPhone.setText(defaultPhone)

        val data = listOf("- 선택 -", "광고/마케팅", "기관사칭", "금융사기", "가족/지인 사칭")
        val adapter = ArrayAdapter(requireContext(), R.layout.report_reason_item, data)
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
                val description = spinner.selectedItem.toString().trim()

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
}
