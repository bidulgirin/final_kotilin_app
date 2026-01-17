package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.dto.LoginResponse
import com.final_pj.voice.feature.login.dto.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterDataSource {

    suspend fun register(request: RegisterRequest): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = ApiClient.api.register(request)
                Result.Success(resp)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}
