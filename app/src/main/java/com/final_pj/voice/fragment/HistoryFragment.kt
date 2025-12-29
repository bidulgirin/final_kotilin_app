package com.final_pj.voice.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.adapter.CallLogAdapter
import com.final_pj.voice.model.CallRecord

class HistoryFragment : Fragment() {

    private lateinit var adapter: CallLogAdapter
    private val callRecords = mutableListOf<CallRecord>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.contact_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = CallLogAdapter(callRecords) { record ->
            // 상세 페이지로 이동
            val bundle = Bundle().apply {
                putLong("call_id", record.id)
            }
            findNavController().navigate(R.id.action_home_to_detail, bundle)
        }
        recycler.adapter = adapter

        loadCallLogs()
    }

    private fun loadCallLogs() {
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val record = CallRecord(
                    id = it.getLong(0),
                    name = it.getString(1),
                    phoneNumber = it.getString(2),
                    callType = when(it.getInt(3)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Unknown"
                    },
                    date = it.getLong(4)
                )
                callRecords.add(record)
                adapter.notifyItemInserted(callRecords.size - 1)

                // 딥러닝 요약 시뮬레이션
                simulateSummary(record)
            }
        }
    }

    private fun simulateSummary(record: CallRecord) {
        // 예: 2초 후 요약 완료
        Handler(Looper.getMainLooper()).postDelayed({
            record.isSummaryDone = true
            record.summary = "요약된 내용 예시"
            adapter.notifyItemChanged(callRecords.indexOf(record))
        }, 2000)
    }
}
