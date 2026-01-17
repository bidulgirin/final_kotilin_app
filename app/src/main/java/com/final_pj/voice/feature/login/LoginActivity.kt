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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModelProvider
import com.final_pj.voice.MainActivity
import com.final_pj.voice.R
import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.login.SignupActivity
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.login.api.ApiClient
import com.final_pj.voice.feature.login.dto.GoogleLoginRequest
import com.final_pj.voice.feature.login.ui.login.LoginViewModel
import com.final_pj.voice.feature.login.ui.login.LoginViewModelFactory
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val WEB_CLIENT_ID = Constants.WEB_CLIENT_ID
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var credentialManager: CredentialManager
    private lateinit var tokenStore: TokenStore
    private lateinit var loginViewModel: LoginViewModel

    private lateinit var btnGoogle: Button
    private lateinit var btnLogin: Button // 일반 로그인 버튼
    private lateinit var btnRegister: Button // 회원가입 버튼 추가
    private lateinit var etEmail: EditText // 이메일/ID 입력 필드
    private lateinit var etPassword: EditText // 비밀번호 입력 필드
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        credentialManager = CredentialManager.create(this)
        tokenStore = TokenStore(this)
        
        // ViewModel 초기화 (TokenStore를 팩토리에 주입)
        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(tokenStore)).get(LoginViewModel::class.java)

        // View 초기화
        btnGoogle = findViewById(R.id.btnGoogle)
        btnLogin = findViewById(R.id.btnLogin) 
        btnRegister = findViewById(R.id.btnRegister) // ★★★ 회원가입 버튼 초기화 추가 ★★★
        etEmail = findViewById(R.id.etEmail) 
        etPassword = findViewById(R.id.etPassword) 
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)

        // Observer 설정
        loginViewModel.loginResult.observe(this) { loginResult ->
            loginResult?.error?.let {
                showError(getString(it))
                setLoading(false)
            }
            loginResult?.success?.let {
                // 성공 시, Google 로그인과 마찬가지로 MainActivity로 이동
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }

        // UI 이벤트 리스너 설정
        btnGoogle.setOnClickListener {
            tvError.visibility = View.GONE
            startGoogleSignIn()
        }

        btnLogin.setOnClickListener {
            tvError.visibility = View.GONE
            // 1. 입력 유효성 검사 및 ViewModel 호출
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            
            loginViewModel.loginDataChanged(email, password)
            
            if (loginViewModel.loginFormState.value?.isDataValid == true) {
                setLoading(true)
                loginViewModel.normalLogin(email, password)
            }
        }
        
        // ★★★ 회원가입 버튼 리스너 추가 ★★★
        btnRegister.setOnClickListener {
            tvError.visibility = View.GONE
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
        }
        
        // 입력 필드 변경 시 유효성 검사 Observer 설정
        loginViewModel.loginFormState.observe(this) { state ->
            etEmail.error = if (state.usernameError != null) getString(state.usernameError) else null
            etPassword.error = if (state.passwordError != null) getString(state.passwordError) else null
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnGoogle.isEnabled = !isLoading
        btnLogin.isEnabled = !isLoading 
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
    }

    // *** Google 로그인 로직은 네트워크 호출이 Activity 내에 직접 남아있으므로, 
    // *** setLoading/에러 처리 부분을 기존 Google 로직과 동기화해야 함 (아래에서 일부 수정)

    private fun startGoogleSignIn() {
        scope.launch {
            setLoading(true) // <-- 이 시점에서 Google 로그인도 Loading 상태로 들어감

            try {
                Log.e(TAG, "request built. WEB_CLIENT_ID=$WEB_CLIENT_ID")

                // 버튼 플로우용 옵션
                val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()

                Log.e(TAG, "before getCredential()")
                val result = withTimeout(15_000) { // 15초 안에 응답 없으면 실패 처리
                    credentialManager.getCredential(
                        request = request,
                        context = this@LoginActivity
                    )
                }

                Log.e(TAG, "after getCredential() result=$result")

                val credential = result.credential

                // GetSignInWithGoogleOption 결과는 CustomCredential로 오는 경우가 많음
                val idToken = if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    throw IllegalStateException("Unexpected credential type: ${credential.type}")
                }

                // 서버 호출: 등록 여부 판단(isNewUser)
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.api.googleLogin(GoogleLoginRequest(idToken = idToken))
                }

                // 토큰 저장
                tokenStore.saveAccessToken(resp.accessToken)

                // “등록된게 없다면(신규)” → 회원가입 화면
                if (resp.isNewUser) {
                    startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
                } else {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                }
                finish()

            } catch (e: NoCredentialException) {
                // 기기에서 사용 가능한 Google 자격증명이 없음(계정 없거나, 제공자 문제 등)
                showError("이 기기에 사용 가능한 Google 계정이 없습니다. (Google 계정 추가/Play 서비스 확인)")
                Log.e(TAG, "NoCredentialException: ${e.message}", e)

            } catch (e: GetCredentialCancellationException) {
                // 사용자가 취소했거나, 재인증(reauth) 실패([16])도 여기로 들어올 수 있음
                showError("구글 로그인 재인증에 실패했어요. (SHA-1/Play services/계정 상태 확인)\n${e.message}")
                Log.e(TAG, "GetCredentialCancellationException: ${e.message}", e)

            } catch (e: GetCredentialException) {
                showError("구글 로그인 실패: ${e.message}")
                Log.e(TAG, "GetCredentialException: ${e.message}", e)

            } catch (e: Exception) {
                showError("오류: ${e.message}")
                Log.e(TAG, "Exception: ${e.message}", e)

            } finally {
                // Google 로그인 성공/실패 시점에 setLoading(false) 호출
                // 단, ViewModel Observer를 통해 처리되는 일반 로그인 성공 건과 분리해야 함.
                // 여기서는 일단 setLoading(false)를 유지합니다.
                setLoading(false)
            }
        }
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
