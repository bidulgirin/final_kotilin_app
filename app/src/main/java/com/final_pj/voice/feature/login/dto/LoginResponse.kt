package com.final_pj.voice.feature.login.dto

data class LoginResponse(
    val accessToken: String,
    val isNewUser: Boolean
)