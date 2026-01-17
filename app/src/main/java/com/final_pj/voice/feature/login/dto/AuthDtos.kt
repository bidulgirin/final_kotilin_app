package com.final_pj.voice.feature.login.dto

import java.time.Instant
import java.util.UUID

data class GoogleLoginRequest(
    val idToken: String,
)

data class UserDto(
    val id: String, // 백엔드가 알아서 uuid 로 넣어주고있음
    val email: String,
    val name: String?,
    val picture: String?,
    val nickname: String?,

    val isVerified: Boolean,   // is_verified 반영
)

data class GoogleLoginResponse(
    val accessToken: String,
    val isNewUser: Boolean,
    val user: UserDto
)

data class UpdateProfileRequest(
    val nickname: String
)
