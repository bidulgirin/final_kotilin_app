package com.final_pj.voice.feature.login.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.login.data.RegisterRepository
import com.final_pj.voice.feature.login.data.Result
import com.final_pj.voice.feature.login.dto.RegisterRequest
import com.final_pj.voice.feature.login.dto.LoginResponse as ApiLoginResponse // DTO 이름 충돌 방지
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerRepository: RegisterRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _registerResult = MutableLiveData<LoginResult>()
    val registerResult: LiveData<LoginResult> = _registerResult
    
    // UI 로직에서 사용할 데이터 클래스 정의
    data class LoginResponse(val accessToken: String, val isNewUser: Boolean = false)

    fun register(email: String, password: String, nickname: String?) {
        viewModelScope.launch {
            val request = RegisterRequest(
                email = email,
                password = password,
                name = nickname
            )
            val result = registerRepository.register(request)

            if (result is Result.Success) {
                // 백엔드 응답(ApiLoginResponse)에서 정보를 꺼내옴
                val response = result.data as? ApiLoginResponse
                if (response != null) {
                    // response.user.email 과 같이 계층 구조 반영
                    tokenStore.saveAuthInfo(
                        token = response.accessToken,
                        name = nickname ?: response.user.id, // 입력한 닉네임이 없으면 id 사용
                        email = response.user.email
                    )
                    _registerResult.value = LoginResult(success = LoggedInUserView(displayName = nickname ?: response.user.email))
                } else {
                    _registerResult.value = LoginResult(error = R.string.login_failed)
                }
            } else {
                _registerResult.value = LoginResult(error = R.string.login_failed)
            }
        }
    }
}