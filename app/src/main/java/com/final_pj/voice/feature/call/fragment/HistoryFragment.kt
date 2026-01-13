package com.final_pj.voice.feature.call.fragment

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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.blocklist.BlocklistCache
import com.final_pj.voice.R
import com.final_pj.voice.adapter.CallLogAdapter
import com.final_pj.voice.feature.call.CallUiItem
import com.final_pj.voice.feature.call.model.CallRecord
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callLogObserver: ContentObserver? = null

    // 디바운스용 Job
    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)


    // 기존 코드
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        setupRecycler(view)
//        // 최초 로드
//        reloadAllCallLogs()
//    }

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
                val bundle = Bundle().apply {
                    putLong("call_id", record.id)
                    putString("phone_number", record.phoneNumber)  // ✅ 추가
                }
                findNavController().navigate(R.id.detailFragment, bundle)
            },
            onBlockClick = { record ->
                showBlockConfirm(record)
            }
        )
        recycler.adapter = adapter

        reloadAllCallLogs()
    }

    private fun reloadAllCallLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) { queryCallLogs() } // List<CallRecord>
            val newUi = buildSectionedItems(records)                      // List<CallUiItem>
            adapter.submit(newUi)
        }
    }
    private fun setupRecycler(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.history_list)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter = CallLogAdapter(
            uiItems,
            onDetailClick = { record ->
                val bundle = Bundle().apply { putLong("call_id", record.id) }
                findNavController().navigate(R.id.detailFragment, bundle)
            },
            onBlockClick = { record ->
                showBlockConfirm(record)
            }
        )
        recycler.adapter = adapter
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



    private fun queryCallLogs(): List<CallRecord> {
        val result = mutableListOf<CallRecord>()

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

        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)
                val number = c.getString(2)
                val typeInt = c.getInt(3)
                val date = c.getLong(4)

                result.add(
                    CallRecord(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        callType = mapCallType(typeInt),
                        date = date
                    )
                )
            }
        }

        return result
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
}
