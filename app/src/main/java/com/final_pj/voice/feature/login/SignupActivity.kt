package com.final_pj.voice.feature.login
// SignupActivity.kt
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.final_pj.voice.MainActivity
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.dto.UpdateProfileRequest
import kotlinx.coroutines.*

class SignupActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        tokenStore = TokenStore(this)

        val etNickname = findViewById<EditText>(R.id.etNickname)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        btnSubmit.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            if (nickname.isEmpty()) {
                etNickname.error = "닉네임을 입력하세요"
                return@setOnClickListener
            }

            scope.launch {
                val token = tokenStore.getAccessToken()
                if (token == null) {
                    // 토큰이 없으면 로그인부터 다시
                    startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    ApiClient.api.updateMe(
                        authorization = "Bearer $token",
                        body = UpdateProfileRequest(nickname)
                    )
                }

                startActivity(Intent(this@SignupActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
