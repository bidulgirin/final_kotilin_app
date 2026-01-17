package com.final_pj.voice.feature.login.dto

// 백엔드 실제 응답 구조와 매칭
data class LoginResponse(
    val accessToken: String,
    val isNewUser: Boolean,
    val user: UserInfoDto
)

data class UserInfoDto(
    val id: String,
    val email: String,
    val name: String? = null,
    val nickname: String? = null
)
