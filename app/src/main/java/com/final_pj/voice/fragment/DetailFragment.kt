package com.final_pj.voice.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.final_pj.voice.R

class DetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val callId = arguments?.getLong("call_id") ?: return
//        val callRecord = callRecords.find { it.id == callId }
//
//        view.findViewById<TextView>(R.id.detail_name).text = callRecord?.name ?: "알 수 없음"
//        view.findViewById<TextView>(R.id.detail_number).text = callRecord?.phoneNumber
//        view.findViewById<TextView>(R.id.detail_summary).text = callRecord?.summary ?: "요약 중..."
    }
}
