package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.data.model.LoggedInUser
import com.final_pj.voice.feature.login.dto.NormalLoginRequest
import com.final_pj.voice.feature.login.ui.login.LoginViewModel.LoginResponse // ViewModel에서 정의한 응답 구조 재사용 가정
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    // 기존 로그인 함수 (ID/PW 기반이 아닌 경우, 혹은 초기 상태 확인용으로 남겨둡니다)
    fun login(username: String, password: String): Result<LoggedInUser> {
        // TODO: handle loggedInUser authentication for existing cache logic
        try {
            // 실제로는 서버 통신이 필요하며, 이 코드는 더미 사용자만 반환합니다.
            val fakeUser = LoggedInUser(java.util.UUID.randomUUID().toString(), username)
            return Result.Success(fakeUser)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    // 일반 로그인 API 호출을 처리하는 함수 (JWT 발급 요청)
    suspend fun normalLogin(email: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = NormalLoginRequest(email = email, password = password)
                
                // ApiClient.api.normalLogin을 호출하여 서버 응답을 받아옵니다.
                // resp는 { accessToken: String, isNewUser: Boolean } 형태라고 가정합니다.
                val resp = ApiClient.api.normalLogin(request) 
                
                // 성공 시, ViewModel에서 예상하는 응답 구조로 변환하여 반환
                val loginResponse = LoginResponse(accessToken = resp.accessToken, isNewUser = resp.isNewUser)
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