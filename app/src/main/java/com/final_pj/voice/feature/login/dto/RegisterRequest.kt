package com.final_pj.voice.feature.login.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null, // 닉네임
    val isAgree: Boolean // 통화 내용 제공 동의 여부
)
