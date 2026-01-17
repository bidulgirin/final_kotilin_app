package com.final_pj.voice.feature.login.dto

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null // 닉네임
)
