package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.TokenStore // TokenStore가 필요할 수도 있지만, Repository에서는 주지 않음
import com.final_pj.voice.feature.login.data.model.LoggedInUser
import com.final_pj.voice.feature.login.ui.login.LoginViewModel.LoginResponse // ViewModel에서 가정한 응답 구조
import java.io.IOException

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository(val dataSource: LoginDataSource) {

    // in-memory cache of the loggedInUser object
    var user: LoggedInUser? = null
        private set

    val isLoggedIn: Boolean
        get() = user != null

    init {
        user = null
    }

    fun logout() {
        user = null
        dataSource.logout()
    }

    // 기존 로그인 함수 (ID/PW 기반이 아닌 경우, 혹은 초기 상태 확인용으로 남겨둡니다)
    fun login(username: String, password: String): Result<LoggedInUser> {
        // 기존 로직 유지. 실제로는 이 로직이 서버를 호출해야 합니다.
        val result = dataSource.login(username, password)

        if (result is Result.Success) {
            setLoggedInUser(result.data)
        }

        return result
    }
    
    // 일반 로그인 함수 추가 (ViewModel에서 호출할 함수)
    // 이 함수는 서버로부터 JWT 토큰을 포함한 응답을 받아야 합니다.
    suspend fun normalLogin(email: String, password: String): Result<LoginResponse> {
        // 실제로는 dataSource.normalLogin(email, password) 호출로 대체되어야 합니다.
        
        return try {
            // 임시 더미 응답: LoginResponse 생성 시 변경된 파라미터(name, email)를 추가합니다.
            val dummyResponse = LoginResponse(
                accessToken = "DUMMY_NORMAL_JWT_TOKEN_${System.currentTimeMillis()}",
                isNewUser = false,
                name = "사용자", // 임시 이름
                email = email  // 입력받은 이메일
            )
            Result.Success(dummyResponse)
            
        } catch (e: Exception) {
            Result.Error(IOException(e))
        }
    }

    private fun setLoggedInUser(loggedInUser: LoggedInUser) {
        this.user = loggedInUser
    }
}