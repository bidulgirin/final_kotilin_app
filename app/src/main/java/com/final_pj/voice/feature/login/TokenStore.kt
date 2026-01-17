package com.final_pj.voice.feature.login
// TokenStore.kt
import android.content.Context
import android.util.Log

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveAuthInfo(token: String, name: String?, email: String?) {
        Log.d("saveAuthInfo", "${token}")
        Log.d("saveAuthInfo", "${name}")
        Log.d("saveAuthInfo", "${email}")
        prefs.edit().apply {
            putString("accessToken", token)
            putString("name", name)
            putString("email", email)
            apply()
        }
    }
    
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
    fun getUserName(): String? = prefs.getString("name", null)
    fun getUserEmail(): String? = prefs.getString("email", null)


    fun clear() {
        prefs.edit().clear().apply()
    }
}
