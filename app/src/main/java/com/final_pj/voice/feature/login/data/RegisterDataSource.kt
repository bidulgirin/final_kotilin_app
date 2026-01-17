package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.dto.LoginResponse // 회원가입 응답으로 사용
import com.final_pj.voice.feature.login.dto.RegisterRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterDataSource {

    suspend fun register(request: RegisterRequest): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // ApiClient.api.register 호출 (ApiService.kt에 정의 완료)
                val resp = ApiClient.api.register(request)
                
                // 성공 응답 반환 (LoginResponse는 accessToken과 isNewUser를 포함한다고 가정)
                Result.Success(resp)
                
            } catch (e: Exception) {
                Result.Error(IOException("Registration failed: ${e.message}", e))
            }
        }
    }
}