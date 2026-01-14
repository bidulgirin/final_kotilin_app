package com.final_pj.voice.feature.login.api

// ApiService.kt
import com.final_pj.voice.feature.login.dto.GoogleLoginRequest
import com.final_pj.voice.feature.login.dto.GoogleLoginResponse
import com.final_pj.voice.feature.login.dto.UpdateProfileRequest
import com.final_pj.voice.feature.login.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface ApiService {
    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): GoogleLoginResponse

    @PUT("api/v1/users/me")
    suspend fun updateMe(
        @Header("Authorization") authorization: String,
        @Body body: UpdateProfileRequest
    ): UserDto
}
