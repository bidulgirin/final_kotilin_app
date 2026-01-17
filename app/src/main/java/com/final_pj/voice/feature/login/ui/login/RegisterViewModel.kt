package com.final_pj.voice.feature.login.ui.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.login.data.RegisterRepository
import com.final_pj.voice.feature.login.data.Result
import com.final_pj.voice.feature.login.dto.RegisterRequest
import com.final_pj.voice.feature.login.dto.LoginResponse as ApiLoginResponse
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerRepository: RegisterRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _registerResult = MutableLiveData<LoginResult>()
    val registerResult: LiveData<LoginResult> = _registerResult
    
    fun register(email: String, password: String, nickname: String?) {
        viewModelScope.launch {
            val request = RegisterRequest(
                email = email,
                password = password,
                name = nickname
            )
            val result = registerRepository.register(request)

            if (result is Result.Success) {
                val response = result.data as? ApiLoginResponse
                if (response != null) {
                    Log.d("RegisterViewModel", "Registration Success: $response")
                    
                    // 백엔드 응답에서 정보를 가져오거나, 입력받은 정보를 사용
                    // 백엔드 구조: {"accessToken": "...", "user": {"id": "...", "email": "..."}}
                    tokenStore.saveAuthInfo(
                        token = response.accessToken,
                        name = nickname ?: response.user.id,
                        email = response.user.email
                    )
                    
                    _registerResult.value = LoginResult(success = LoggedInUserView(displayName = nickname ?: response.user.email))
                } else {
                    Log.e("RegisterViewModel", "Cast to ApiLoginResponse failed")
                    _registerResult.value = LoginResult(error = R.string.login_failed)
                }
            } else {
                _registerResult.value = LoginResult(error = R.string.login_failed)
            }
        }
    }

    // **주의**: LoginViewModel에서 사용되던 유효성 검사 로직이 여기서는 사용되지 않으므로,
    // RegisterActivity에서 직접 이메일/비밀번호 길이 검사를 해야 합니다.
}