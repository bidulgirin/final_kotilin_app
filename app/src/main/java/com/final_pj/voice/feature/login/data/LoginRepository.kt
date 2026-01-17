package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.data.model.LoggedInUser
import com.final_pj.voice.feature.login.ui.login.LoginViewModel.LoginResponse 
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

    fun login(username: String, password: String): Result<LoggedInUser> {
        val result = dataSource.login(username, password)

        if (result is Result.Success) {
            setLoggedInUser(result.data)
        }

        return result
    }
    
    // 일반 로그인 함수 추가
    suspend fun normalLogin(email: String, password: String): Result<LoginResponse> {
        // 더미 응답 대신 실제 dataSource의 normalLogin을 호출합니다.
        return dataSource.normalLogin(email, password)
    }

    private fun setLoggedInUser(loggedInUser: LoggedInUser) {
        this.user = loggedInUser
    }
}
