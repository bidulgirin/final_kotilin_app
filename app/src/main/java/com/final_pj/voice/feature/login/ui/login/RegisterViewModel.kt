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
import retrofit2.HttpException

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
                    
                    // 우선순위: 입력한 nickname -> 서버의 nickname -> 서버의 name -> 서버의 id
                    val displayName = nickname ?: response.user.nickname ?: response.user.name ?: response.user.id
                    
                    tokenStore.saveAuthInfo(
                        token = response.accessToken,
                        name = displayName,
                        email = response.user.email
                    )
                    
                    _registerResult.value = LoginResult(success = LoggedInUserView(displayName = displayName))
                } else {
                    Log.e("RegisterViewModel", "Cast to ApiLoginResponse failed")
                    _registerResult.value = LoginResult(error = R.string.login_failed)
                }
            } else if (result is Result.Error) {
                val exception = result.exception
                Log.e("RegisterViewModel", "Registration Error: ${exception.message}")
                
                if (exception is HttpException && exception.code() == 400) {
                    _registerResult.value = LoginResult(error = R.string.error_duplicate_email)
                } else {
                    _registerResult.value = LoginResult(error = R.string.login_failed)
                }
            }
        }
    }
}
