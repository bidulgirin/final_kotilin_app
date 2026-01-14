package com.final_pj.voice.feature.login
// TokenStore.kt
import android.content.Context

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveAccessToken(token: String) {
        prefs.edit().putString("accessToken", token).apply()
    }

    fun isLoggedIn(): Boolean {
        // 토큰이 있는지만 체크(최소)
        return !getAccessToken().isNullOrBlank()
    }

    fun isLoggedInValid(): Boolean {
        val token = getAccessToken()
        if (token.isNullOrBlank()) return false
        return !JwtUtil.isExpired(token)
    }



    fun getAccessToken(): String? = prefs.getString("accessToken", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
