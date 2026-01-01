package com.final_pj.voice

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController

import com.final_pj.voice.service.CallDetectService
import com.final_pj.voice.util.VoskModelHolder
import com.final_pj.voice.util.encryptAudioBuffer.encryptAudioBuffer
import com.final_pj.voice.util.encryptAudioBuffer.sendAudioToServer
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
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
    // 포그라운드서비스
    // ----------------------------
    private fun startForegroundService() {
        val intent = Intent(this, CallDetectService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    // ----------------------------
    // 권한
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
    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            //Manifest.permission.READ_PHONE_NUMBERS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return list.toTypedArray()
    }
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions(),
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
    /**
     * 현재 앱을 "기본 전화(Dialer) 앱"으로 설정하도록 사용자에게 요청하는 메소드
     *
     * - Telecom 기능(가짜 수신 전화, ConnectionService 등)을 제대로 쓰려면
     *   앱이 기본 Dialer로 설정되어 있어야 함
     * - 강제로 변경할 수는 없고, 반드시 시스템 설정 화면을 통해
     *   사용자의 명시적인 동의가 필요함
     */
    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            val dialerRoleRequest = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                Log.d("!!", "dialerRoleRequest succeeded: ${it.resultCode == Activity.RESULT_OK}")
            }

            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER))
                dialerRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent) // Q 미만에서는 여전히 사용 가능
        }
    }

    // 앱을 실행했을때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 내 앱을 기본 통화 앱 설정하는 함수
        requestDefaultDialer()

        // fragment 부르기 (프레그먼트 중복 소환 방지)
        if(savedInstanceState == null){
            // 권한 요청
            checkAndRequestPermissions()
            supportFragmentManager.commit{ // 프래그먼트 매니저
                setupBottomNavigation()
            }
        }
//
//        // 백엔드 테스트
//        val dummyAudio = FloatArray(16000) { i -> Math.sin(2.0 * Math.PI * 440 * i / 16000).toFloat() }
//        val encrypted = encryptAudioBuffer(dummyAudio, "1234567890abcdef")
//        sendAudioToServer(encrypted, "192.168.3.10") // PC IP

    }
    companion object {
        private const val REQUEST_PERMISSION_CODE = 1001
    }
}


