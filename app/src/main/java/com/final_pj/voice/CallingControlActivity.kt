package com.final_pj.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import com.final_pj.voice.service.MyInCallService
import android.telecom.CallAudioState
import android.util.Log
import com.final_pj.voice.util.CallUtils
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.bus.CallEventBus
import kotlinx.coroutines.launch

// 전화 중일때 나타나는 화면
class CallingControlActivity : AppCompatActivity() {
    private var isMuted = false
    private var isSpeakerOn = false

//    private val callEndReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            Log.d("callEndReceiver", "통화종료!!!!!")
//            finish() // 통화 종료 → 화면 닫기
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        registerReceiver(
//            callEndReceiver,
//            IntentFilter(MyInCallService.ACTION_CALL_ENDED),
//            RECEIVER_NOT_EXPORTED
//        )
//    }
//
//    override fun onPause() {
//        super.onPause()
//        unregisterReceiver(callEndReceiver)
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_call)

        val tvNumber = findViewById<TextView>(R.id.tvCallingNumber)
        val tvState = findViewById<TextView>(R.id.tvCallState)
        val tvTimer = findViewById<TextView>(R.id.tvCallTimer)

        val btnMute = findViewById<Button>(R.id.btnMute)
        val btnSpeaker = findViewById<Button>(R.id.btnSpeaker)
        val btnKeypad = findViewById<Button>(R.id.btnKeypad)
        val btnEndCall = findViewById<Button>(R.id.btnEndCall)

        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val isOutgoing = intent.getBooleanExtra("is_outgoing", false)

        tvNumber.text = phoneNumber

        if (isOutgoing) {
            // 발신일 때만 전화 걸기
            CallUtils.placeCall(this, phoneNumber)
        }

        // 버튼 클릭 이벤트
        btnMute.setOnClickListener {
            val service = MyInCallService.instance ?: return@setOnClickListener

            isMuted = !isMuted
            service.setMuted(isMuted)
        }
        // 전화 끊기는 이벤트
        lifecycleScope.launch {
            CallEventBus.callEnded.collect {
                finish()
            }
        }

        // 스피커
        btnSpeaker.setOnClickListener {
            val service = MyInCallService.instance ?: return@setOnClickListener

            val route = if (isSpeakerOn) {
                CallAudioState.ROUTE_EARPIECE
            } else {
                CallAudioState.ROUTE_SPEAKER
            }

            isSpeakerOn = !isSpeakerOn
            service.setAudioRoute(route)
        }

        btnKeypad.setOnClickListener {
            // 키패드 토글 (DialerFragment 호출 가능)
        }


        // 통화 종료 버튼
        btnEndCall.setOnClickListener {

            // 통화 종료
            MyInCallService.instance?.endCall()

            // MainActivity로 이동
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)

            // 현재 UI 종료
            finish()
        }


        // 통화 시간 타이머
        val handler = Handler(Looper.getMainLooper())
        var seconds = 0
        handler.post(object : Runnable {
            override fun run() {
                val minutes = seconds / 60
                val secs = seconds % 60
                tvTimer.text = String.format("%02d:%02d", minutes, secs)
                seconds++
                handler.postDelayed(this, 1000)
            }
        })


    }




}
