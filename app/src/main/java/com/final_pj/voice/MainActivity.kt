package com.final_pj.voice

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController

import com.final_pj.voice.databinding.ActivityMainBinding
import com.final_pj.voice.service.CallDetectService
import com.final_pj.voice.util.VoskModelHolder
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    // ----------------------------
    // vosk 인디바이스 예제
    // ----------------------------

    private lateinit var resultTextView: TextView


    // ----------------------------
    // 화면 + 네비게이션 구성 관련
    // ----------------------------
    private fun setupBottomNavigation() {
        // NavHostFragment 가져오기
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment

        // NavController
        val navController = navHostFragment.navController

        // BottomNavigationView 부르기
        val bottomNav = findViewById<BottomNavigationView>(R.id.menu_bottom_navigation)

        // 연결
        bottomNav.setupWithNavController(navController)
    }

    // ----------------------------
    // 포그라운드서비스 관련
    // ----------------------------
    private fun startForegroundService() {
        val intent = Intent(this, CallDetectService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }



    // ----------------------------
    // 권한 관련
    // ----------------------------


    private fun checkAndRequestPermissions() {
        // 모든 권한이 있다면
        if (hasRequiredPermissions()) {
            // 모델 불러오기
            VoskModelHolder.init(this)
            // 포그라운드 서비스 시작
            startForegroundService()
        } else {
            // 권한 없으면 권한 요청을 해라
            requestRequiredPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean { // 어떤 권한이 필요한지
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO,

        )

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE, // 통화상태감지
                Manifest.permission.RECORD_AUDIO, // 오디오 녹음 권한
                Manifest.permission.POST_NOTIFICATIONS, // 알림권한
                Manifest.permission.READ_MEDIA_AUDIO, // 외부저장소  READ_EXTERNAL_STORAGE 1ㅡ3이하
            ),
            REQUEST_PERMISSION_CODE
        )
    }

    
    override fun onRequestPermissionsResult( // 퍼미션 권한 결과
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 코드가 다르면 돌려보내라
        if (requestCode != REQUEST_PERMISSION_CODE) return

        // 모든 권한을 잘 받았다면
        if (grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            // 포그라운드 서비스 시작
            startForegroundService()
        } else {
            // 모든 권한을 받지 못했으므로 쫓아내기
            Toast.makeText(
                this,
                "모든 권한을 허용하지 않으면 앱을 사용할 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            finishAffinity() // 앱의 모든 Activity 종료
        }
    }

    // ----------------------------
    // 전화 관련(전화 기본 앱)
    // ----------------------------

    // 내 앱이 기본 연결 프로그램으로 사용할 수 있는지 체크
    val mRoleManager = getSystemService(RoleManager::class.java)
    val isRoleAvailable = mRoleManager.isRoleAvailable((RoleManager.ROLE_CALL_SCREENING))
    // 앱을 실행했을때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // fragment 부르기 (프레그먼트 중복 소환 방지)
        if(savedInstanceState == null){
            // 권한 요청
            checkAndRequestPermissions()
            supportFragmentManager.commit{ // 프래그먼트 매니저
                setupBottomNavigation()
            }
        }
    }
    companion object {
        private const val REQUEST_PERMISSION_CODE = 1001
    }
}


