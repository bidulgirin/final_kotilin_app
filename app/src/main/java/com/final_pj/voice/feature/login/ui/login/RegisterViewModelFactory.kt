package com.final_pj.voice.feature.login.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.login.data.RegisterDataSource
import com.final_pj.voice.feature.login.data.RegisterRepository

class RegisterViewModelFactory(
    private val tokenStore: TokenStore
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            return RegisterViewModel(
                registerRepository = RegisterRepository(
                    dataSource = RegisterDataSource()
                ),
                tokenStore = tokenStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}