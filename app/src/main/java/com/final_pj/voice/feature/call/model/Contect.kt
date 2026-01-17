package com.final_pj.voice.feature.call.model
// 연락처 관련 data 모델
data class Contact(
    val name: String,
    val phone: String,
    val contactId: Long? = null
)
