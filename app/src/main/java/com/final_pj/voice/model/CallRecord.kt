package com.final_pj.voice.model

data class CallRecord(
    val id: Long,
    val name: String?,
    val phoneNumber: String,
    val callType: String,  // Incoming/Outgoing/Missed
    val date: Long,
    var summary: String? = null,  // 딥러닝 요약
    var isSummaryDone: Boolean = false
)
