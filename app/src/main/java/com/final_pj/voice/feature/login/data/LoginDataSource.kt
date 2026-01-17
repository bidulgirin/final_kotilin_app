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
                
                // 1. 백엔드 서버로부터 응답을 받음 (중첩된 user 객체 포함)
                val resp = ApiClient.api.normalLogin(request)
                
                // 2. ViewModel이 사용하기 편하도록 데이터 가공
                // resp.user.email 처럼 내부 객체에서 값을 꺼냅니다.
                val loginResponse = LoginResponse(
                    accessToken = resp.accessToken,
                    isNewUser = resp.isNewUser,
                    name = resp.user.id,  // 백엔드에 name이 없다면 id를 임시로 사용
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