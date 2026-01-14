package com.final_pj.voice

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.final_pj.voice.feature.login.TokenStore
import com.final_pj.voice.feature.call.service.CallDetectService
import com.final_pj.voice.feature.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // 기본 전화(Dialer) 역할(Role) 요청 결과를 받기 위한 런처
    // - Android 10(Q) 이상에서 RoleManager를 통해 기본 다이얼러 설정을 요청할 때 사용
    private lateinit var dialerRoleRequestLauncher: ActivityResultLauncher<Intent>
    val tokenStore: TokenStore by lazy { TokenStore(this) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireLogin() // 토큰 없으면 로그인으로 보냄

        // 화면 레이아웃 연결
        setContentView(R.layout.activity_main)




        // Q 이상에서 Role 요청 결과를 받기 위한 런처 등록
        // - registerForActivityResult는 onCreate에서 등록하는 것이 안전함
        registerDialerRoleLauncher()

        // 하단 네비게이션과 NavController 연결
        // - 화면 회전 등으로 Activity가 재생성될 때도 정상 동작하도록 매번 설정해줌
        setupBottomNavigation()

        // 앱을 기본 전화(Dialer) 앱으로 설정하도록 사용자에게 요청
        // - 사용자의 명시적인 동의가 필요하며 강제 변경은 불가능
        requestDefaultDialer()

        // 최초 생성일 때만 권한 확인/요청 및 포그라운드 서비스 시작 시도
        // - 회전 등으로 재생성될 때 중복 요청/실행을 줄이기 위해 유지
        if (savedInstanceState == null) {
            checkAndRequestPermissions()
        }
    }

    protected fun requireLogin() {
        if (!tokenStore.isLoggedInValid()) {
            tokenStore.clear()

            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * BottomNavigationView와 NavController를 연결해 네비게이션을 구성한다.
     */
    private fun setupBottomNavigation() {
        // NavHostFragment 찾기
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment

        // NavController 가져오기
        val navController = navHostFragment.navController

        // BottomNavigationView 가져오기
        val bottomNav = findViewById<BottomNavigationView>(R.id.menu_bottom_navigation)

        // BottomNavigationView와 NavController 연결
        bottomNav.setupWithNavController(navController)
    }

    /**
     * 통화 감지를 위한 포그라운드 서비스를 시작한다.
     * - Android O 이상부터는 startForegroundService를 사용해야 함
     */
    private fun startForegroundService() {
        val intent = Intent(this, CallDetectService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * 필요한 권한이 모두 있는지 확인하고, 없으면 요청한다.
     * - 권한이 모두 있으면 포그라운드 서비스를 바로 시작한다.
     */
    private fun checkAndRequestPermissions() {
        if (hasRequiredPermissions()) {
            // 필요한 권한이 모두 있으면 서비스 시작
            startForegroundService()
        } else {
            // 권한이 부족하면 권한 요청
            requestRequiredPermissions()
        }
    }

    /**
     * 앱에서 요구하는 권한 목록을 구성해 반환한다.
     * - Android 13(TIRAMISU) 이상/미만에 따라 요청 권한이 달라짐
     */
    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            // 전화 걸기
            Manifest.permission.CALL_PHONE,
            // 전화 상태 읽기
            Manifest.permission.READ_PHONE_STATE,
            // 마이크 녹음
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 알림 권한(13 이상)
            permissions += Manifest.permission.POST_NOTIFICATIONS
            // 미디어(오디오) 읽기(13 이상)
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            // 외부 저장소 읽기(12 이하)
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return permissions.toTypedArray()
    }

    /**
     * 필요한 권한이 모두 허용되어 있는지 검사한다.
     */
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 필요한 권한들을 한 번에 요청한다.
     */
    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions(),
            REQUEST_PERMISSION_CODE
        )
    }

    /**
     * 권한 요청 결과를 처리한다.
     * - 모든 권한이 허용되면 포그라운드 서비스를 시작한다.
     * - 하나라도 거부되면 앱을 종료한다(현재 앱 정책 유지).
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 우리가 요청한 권한 코드가 아니면 무시
        if (requestCode != REQUEST_PERMISSION_CODE) return

        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            // 모든 권한 허용 -> 서비스 시작
            startForegroundService()
        } else {
            // 일부라도 거부 -> 안내 후 앱 종료
            Toast.makeText(
                this,
                "모든 권한을 허용하지 않으면 앱을 사용할 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            // 앱의 모든 Activity 종료
            finishAffinity()
        }
    }

    /**
     * Android 10(Q) 이상: RoleManager로 기본 다이얼러 역할을 요청한다.
     * Android 9 이하: ACTION_CHANGE_DEFAULT_DIALER 인텐트로 기본 다이얼러 변경 화면을 연다.
     */
    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Q 이상에서는 RoleManager로 기본 다이얼러 역할 요청
            val roleManager = getSystemService(RoleManager::class.java)

            // 역할이 가능한지 + 이미 보유 중인지 체크
            val isAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
            val isHeld = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)

            // 가능한 역할이고 아직 보유하지 않았다면 요청 화면 띄움
            if (isAvailable && !isHeld) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                dialerRoleRequestLauncher.launch(intent)
            }
        } else {
            // Q 미만에서는 기본 다이얼러 변경 인텐트 사용
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                packageName
            )
            startActivity(intent)
        }
    }

    /**
     * 기본 다이얼러 Role 요청 결과를 받는 런처를 등록한다.
     * - registerForActivityResult는 lifecycle 안전을 위해 onCreate에서 한 번만 등록하는 것을 권장
     */
    private fun registerDialerRoleLauncher() {
        dialerRoleRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val granted = result.resultCode == Activity.RESULT_OK
                Log.d(TAG, "dialerRoleRequest succeeded: $granted")
            }
    }

    companion object {
        // 권한 요청 코드
        private const val REQUEST_PERMISSION_CODE = 1001

        // 로그 태그
        private const val TAG = "MainActivity"
    }
}
