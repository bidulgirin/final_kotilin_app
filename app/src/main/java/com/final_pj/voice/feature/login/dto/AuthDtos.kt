package com.final_pj.voice.feature.login.dto

// AuthDtos.kt
data class GoogleLoginRequest(
    val idToken: String
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String?,
    val picture: String?,
    val nickname: String?
)

data class GoogleLoginResponse(
    val accessToken: String,
    val isNewUser: Boolean,
    val user: UserDto
)

data class UpdateProfileRequest(
    val nickname: String
)

