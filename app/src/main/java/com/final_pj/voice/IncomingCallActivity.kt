package com.final_pj.voice

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.final_pj.voice.service.MyInCallService

// 전화가 오면 나타나는 액티비티
class IncomingCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        val btnAccept = findViewById<Button>(R.id.btnAccept)
        val btnReject = findViewById<Button>(R.id.btnReject)
        val tvNumber = findViewById<TextView>(R.id.tvNumber)

        tvNumber.text = intent.getStringExtra("phone_number")

        // 📞 수락
        btnAccept.setOnClickListener {
            val number = intent.getStringExtra("phone_number") ?: return@setOnClickListener

            MyInCallService.currentCall?.answer(0)

            // 📱 통화 중 화면으로 이동 (수신)
            val intent = Intent(this, CallingControlActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", false)  //
            }
            startActivity(intent)
            // 액티비티 닫음
            finish()
        }

        // ❌ 거절
        btnReject.setOnClickListener {
            MyInCallService.currentCall?.reject(false, null)
            finish()
        }
    }


}


