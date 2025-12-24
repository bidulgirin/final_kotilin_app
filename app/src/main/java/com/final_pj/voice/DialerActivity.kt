package com.final_pj.voice

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import com.final_pj.voice.ui.main.DialerFragment

class DialerActivity : AppCompatActivity() {
//    기본 전화 앱으로 등록하기
    private val roleManager: RoleManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(ROLE_SERVICE) as RoleManager
        } else null
    }
    private val telecomManager: TelecomManager by lazy { getSystemService(TELECOM_SERVICE) as TelecomManager }
    // 내가 만든 앱이 기본으로 등록도 었나 확인하는 변수~~~
    private val isDefaultDialer get() = packageName.equals(telecomManager.defaultDialerPackage)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("test", "전화 액티비티 활성화!!!!")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        Log.d("test", "${isDefaultDialer}")
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, DialerFragment.newInstance())
                .commitNow()
        }

        Intent(Intent.ACTION_DIAL).let {
            startActivity(it)//1번
        }

    }
}