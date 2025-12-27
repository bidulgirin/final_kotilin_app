package com.final_pj.voice

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.final_pj.voice.service.MyConnectionService
import com.final_pj.voice.service.MyInCallService
import com.final_pj.voice.ui.main.DialerFragment


class DialerActivity : AppCompatActivity() {

    private val telecomManager: TelecomManager by lazy {
        getSystemService(TELECOM_SERVICE) as TelecomManager
    }

    private val isDefaultDialer
        get() = packageName == telecomManager.defaultDialerPackage

    companion object {
        private const val REQUEST_CODE_ROLE_DIALER = 1001
    }
    private lateinit var requestRoleLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_dialer)

        // 기본 Dialer가 아니면 사용자에게 안내
        // Activity Result API 등록
        requestRoleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "이제 기본 전화 앱입니다!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "기본 전화 앱으로 설정해야 가짜 전화 가능", Toast.LENGTH_SHORT).show()
            }
        }

//        if (!isDefaultDialer) {
//            requestDefaultDialer()
//        }
        findViewById<Button>(R.id.btnSetDefaultDialer).setOnClickListener {
            requestDefaultDialer()
        }

        // 버튼 클릭 이벤트
        findViewById<Button>(R.id.btnFakeCall).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tryFakeIncomingCall()
            }
        }
    }

    // ==========================
    // 기본 Dialer 요청
    // ==========================
    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("Dialer", "di!!!!!!!!!!!!!!")
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                requestRoleLauncher.launch(intent) // ❌ startActivityForResult 대신
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent) // Q 미만에서는 여전히 사용 가능
        }
    }

    // ==========================
    // 가짜 전화 시도
    // ==========================
    @RequiresApi(Build.VERSION_CODES.O)
    private fun tryFakeIncomingCall() {
        val phoneAccountHandle = PhoneAccountHandle(
            ComponentName(this, MyConnectionService::class.java),
            "FAKE_CALL_ACCOUNT"
        )

        val phoneAccount = PhoneAccount.builder(
            phoneAccountHandle,
            "Fake Call Account" // UI에 표시될 이름
        )
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setShortDescription("연구용 가짜 전화")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_phone_call)) // 아이콘
            .build()

        // 등록
        telecomManager.registerPhoneAccount(phoneAccount)

        // 등록 후 활성화 체크
        val account = telecomManager.getPhoneAccount(phoneAccountHandle)
        if (account?.isEnabled == true) {
            // 활성화 되어 있으면 가짜 전화 발생
            val extras = Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts("tel", "01012345678", null)
                )
            }
            telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
        } else {
            // 활성화 안되어 있으면 사용자에게 설정 안내
            Toast.makeText(
                this,
                "전화 계정을 활성화해야 가짜 전화를 받을 수 있습니다.",
                Toast.LENGTH_LONG
            ).show()
            // ACTION_MANAGE_PHONE_ACCOUNTS
            val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
            startActivity(intent)
        }
    }

    // ==========================
    // RoleManager 결과 처리 (Q+)
    // ==========================
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE_ROLE_DIALER) {
//            if (isDefaultDialer) {
//                Toast.makeText(this, "이제 기본 전화 앱입니다!", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this, "기본 전화 앱으로 설정해야 가짜 전화 가능", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
}
