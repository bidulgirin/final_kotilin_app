package com.final_pj.voice.feature.login.data

import com.final_pj.voice.feature.login.dto.LoginResponse
import com.final_pj.voice.feature.login.dto.RegisterRequest
import java.io.IOException

class RegisterRepository(private val dataSource: RegisterDataSource) {

    suspend fun register(request: RegisterRequest): Result<LoginResponse> {
        return dataSource.register(request)
    }
}