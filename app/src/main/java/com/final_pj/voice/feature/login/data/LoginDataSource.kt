package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.data.model.LoggedInUser
import com.final_pj.voice.feature.login.dto.NormalLoginRequest
import com.final_pj.voice.feature.login.ui.login.LoginViewModel.LoginResponse // ViewModel용 응답 구조
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        try {
            val fakeUser = LoggedInUser(java.util.UUID.randomUUID().toString(), username)
            return Result.Success(fakeUser)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    // 일반 로그인 API 호출
    suspend fun normalLogin(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = NormalLoginRequest(email = email, password = password)
                
                // 1. 백엔드 서버로부터 응답을 받음
                val resp = ApiClient.api.normalLogin(request)
                
                // 2. ViewModel이 사용하기 편하도록 데이터 가공
                // nickname -> name -> id 순서로 표시 이름을 결정합니다.
                val displayName = resp.user.nickname ?: resp.user.name ?: resp.user.id
                
                val loginResponse = LoginResponse(
                    accessToken = resp.accessToken,
                    isNewUser = resp.isNewUser,
                    name = displayName,
                    email = resp.user.email
                )
                Result.Success(loginResponse)
                
            } catch (e: Exception) {
                Result.Error(IOException("Normal login failed: ${e.message}", e))
            }
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}
