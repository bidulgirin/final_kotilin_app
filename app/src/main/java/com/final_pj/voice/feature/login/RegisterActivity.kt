package com.final_pj.voice.feature.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.final_pj.voice.MainActivity
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.dto.RegisterRequest
import com.final_pj.voice.feature.login.ui.login.RegisterViewModel
import com.final_pj.voice.feature.login.ui.login.RegisterViewModelFactory
import kotlinx.coroutines.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var tokenStore: TokenStore
    private lateinit var scope: CoroutineScope
    private lateinit var registerViewModel: RegisterViewModel

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etNickname: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvBackToLogin: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tokenStore = TokenStore(this)
        scope = MainScope()
        // RegisterViewModelFactory를 사용하여 초기화
        registerViewModel = ViewModelProvider(this, RegisterViewModelFactory(tokenStore)).get(RegisterViewModel::class.java)

        // View 초기화
        etEmail = findViewById(R.id.etRegisterEmail)
        etPassword = findViewById(R.id.etRegisterPassword)
        etNickname = findViewById(R.id.etRegisterNickname)
        btnRegister = findViewById(R.id.btnRegister)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        progress = findViewById(R.id.progressRegister)
        tvError = findViewById(R.id.tvRegisterError)

        // Observer 설정
        registerViewModel.registerResult.observe(this) { loginResult ->
            loginResult?.error?.let {
                showError(getString(it))
                setLoading(false)
            }
            loginResult?.success?.let {
                // 회원가입 성공 시 MainActivity로 이동
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                finish()
            }
        }
        
        // 입력 유효성 검사 (간단히)
        etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(etEmail.text.toString()).matches()) {
                    showError("유효하지 않은 이메일 형식입니다.")
                } else { tvError.visibility = View.GONE }
            }
        }
        
        etPassword.setOnFocusChangeListener { _, hasFocus ->
             if (!hasFocus) {
                if (etPassword.text.toString().length < 6) {
                    showError("비밀번호는 6자 이상이어야 합니다.")
                } else { tvError.visibility = View.GONE }
            }
        }


        // 버튼 리스너
        btnRegister.setOnClickListener {
            tvError.visibility = View.GONE
            attemptRegistration()
        }

        tvBackToLogin.setOnClickListener {
            finish() // 로그인 화면으로 돌아가기
        }
    }
    
    private fun attemptRegistration() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val nickname = etNickname.text.toString().trim() 

        // 1차 검증: ViewModel의 검사를 사용하지 않고 Activity에서 간단히 처리
        if (email.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            showError("이메일, 비밀번호, 닉네임을 모두 입력해주세요.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("유효하지 않은 이메일 형식입니다.")
            return
        }
        if (password.length < 6) {
            showError("비밀번호는 6자 이상이어야 합니다.")
            return
        }

        setLoading(true)
        
        // ViewModel을 통해 등록 요청
        registerViewModel.register(email, password, nickname)
    }

    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
        etNickname.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
