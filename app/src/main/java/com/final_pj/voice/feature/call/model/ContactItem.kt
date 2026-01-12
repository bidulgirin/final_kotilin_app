package com.final_pj.voice.feature.call.model

data class ContactItem(
    val name: String,
    val phone: String,
    val contactId: Long? = null
)
