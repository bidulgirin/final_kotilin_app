package com.final_pj.voice.feature.login
import android.util.Base64
import org.json.JSONObject

object JwtUtil {
    fun isExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true

            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            )
            val payload = JSONObject(payloadJson)
            val exp = payload.optLong("exp", 0L)

            // exp는 초 단위(Unix timestamp)
            val nowSec = System.currentTimeMillis() / 1000
            exp <= nowSec
        } catch (e: Exception) {
            true
        }
    }
}
