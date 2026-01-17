package com.final_pj.voice.feature.login.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Patterns
import androidx.lifecycle.viewModelScope
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.login.data.LoginRepository
import com.final_pj.voice.feature.login.data.Result
import kotlinx.coroutines.launch


class LoginViewModel(private val loginRepository: LoginRepository, private val tokenStore: TokenStore) : ViewModel() {

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    // Google 로그인 이후 API 호출 응답 구조(accessToken, isNewUser 등)를 가정하고 일반 로그인에도 적용
    data class LoginResponse(val accessToken: String, val isNewUser: Boolean = false)


    fun login(username: String, password: String) {
        viewModelScope.launch {
            val result = loginRepository.login(username, password)

            if (result is Result.Success) {
                // Repository에서 반환된 응답을 사용한다고 가정
                val loginResponse = result.data as? LoginResponse
                if (loginResponse != null) {
                    tokenStore.saveAccessToken(loginResponse.accessToken)
                    _loginResult.value = LoginResult(success = LoggedInUserView(displayName = username))
                } else {
                    _loginResult.value = LoginResult(error = R.string.login_failed)
                }
            } else {
                _loginResult.value = LoginResult(error = R.string.login_failed)
            }
        }
    }

    // 일반 로그인을 위한 별도 함수 (ViewModel-Repository 패턴을 위해)
    fun normalLogin(email: String, password: String) {
        viewModelScope.launch {
            // Repository 호출: 실제 API 응답 구조에 맞춰 이 부분이 수정되어야 함
            val result = loginRepository.normalLogin(email, password)

            if (result is Result.Success) {
                // Repository에서 받은 응답이 JWT 토큰과 사용자 정보를 포함하고 있다고 가정
                val response = result.data as? LoginResponse
                if (response != null) {
                    tokenStore.saveAccessToken(response.accessToken)
                    // 성공 처리 (isNewUser는 false로 가정하거나, Repository에서 받아와야 함)
                    _loginResult.value = LoginResult(success = LoggedInUserView(displayName = email))
                } else {
                    _loginResult.value = LoginResult(error = R.string.login_failed)
                }
            } else {
                _loginResult.value = LoginResult(error = R.string.login_failed)
            }
        }
    }


    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder username validation check
    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    // A placeholder password validation check
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}