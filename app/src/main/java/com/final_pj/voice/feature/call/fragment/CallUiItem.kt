package com.final_pj.voice.feature.call.fragment

import com.final_pj.voice.feature.call.model.CallRecord

sealed class CallUiItem {
    data class DateHeader(val title: String) : CallUiItem()
    data class CallRow(val record: CallRecord) : CallUiItem()
}