package com.final_pj.voice.feature.login.api

// ApiService.kt
import com.final_pj.voice.feature.login.dto.GoogleLoginRequest
import com.final_pj.voice.feature.login.dto.GoogleLoginResponse
import com.final_pj.voice.feature.login.dto.LoginResponse // 일반 로그인/가입 응답으로 사용
import com.final_pj.voice.feature.login.dto.NormalLoginRequest
import com.final_pj.voice.feature.login.dto.RegisterRequest // 새로 추가
import com.final_pj.voice.feature.login.dto.UpdateProfileRequest
import com.final_pj.voice.feature.login.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface ApiService {
    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): GoogleLoginResponse

    @POST("api/v1/auth/register") // 일반 회원가입 엔드포인트 가정
    suspend fun register(@Body body: RegisterRequest): LoginResponse // JWT를 포함한 응답 가정

    @POST("api/v1/auth/login")
    suspend fun normalLogin(@Body body: NormalLoginRequest): LoginResponse

    @PUT("api/v1/users/me")
    suspend fun updateMe(
        @Header("Authorization") authorization: String,
        @Body body: UpdateProfileRequest
    ): UserDto
}
