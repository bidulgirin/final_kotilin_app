package com.final_pj.voice.feature.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.final_pj.voice.MainActivity
import com.final_pj.voice.R
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
    private lateinit var cbConsent: CheckBox
    private lateinit var tvConsentDetails: TextView
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
        cbConsent = findViewById(R.id.cbConsent)
        tvConsentDetails = findViewById(R.id.tvConsentDetails)
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

        // 자세히 보기 클릭 리스너
        tvConsentDetails.setOnClickListener {
            showConsentDetailsDialog()
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
    
    private fun showConsentDetailsDialog() {
        AlertDialog.Builder(this)
            .setTitle("통화 내용 제공 동의")
            .setMessage("본 서비스는 AI 분석을 위해 통화 내용을 수집 및 분석합니다.\n\n" +
                    "*모든 개인정보는 비식별화 되어 저장됩니다.\n"+
                    "1. 수집 목적: 통화 내용 텍스트 변환 및 요약\n" +
                    "2. 수집 항목: 통화 음성 파일\n" +
                    "3. 보유 기간: 회원 탈퇴 시까지 또는 법령에 따른 보유 기간\n\n" +
                    "체크박스를 선택하시면 위 내용에 동의하는 것으로 간주됩니다.")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun attemptRegistration() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val nickname = etNickname.text.toString().trim() 
        val isAgree = cbConsent.isChecked

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
        if (!isAgree) {
            showError("통화 내용 제공 동의는 필수입니다.")
            return
        }

        setLoading(true)
        
        // ViewModel을 통해 등록 요청
        registerViewModel.register(email, password, nickname, isAgree)
    }

    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
        etNickname.isEnabled = !isLoading
        cbConsent.isEnabled = !isLoading
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
