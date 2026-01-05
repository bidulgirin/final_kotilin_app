package com.final_pj.voice.feature.call.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.MainActivity
import com.final_pj.voice.R
import com.final_pj.voice.bus.CallEventBus
import com.final_pj.voice.core.util.CallUtils
import com.final_pj.voice.feature.call.service.MyInCallService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class CallingActivity : AppCompatActivity() {

    private var isMuted = false
    private var isSpeakerOn = false
    private var isKeypadOpen = false

    private val handler = Handler(Looper.getMainLooper())
    private var timerSeconds = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            val minutes = timerSeconds / 60
            val secs = timerSeconds % 60
            findViewById<TextView>(R.id.tvCallTimer).text = String.format("%02d:%02d", minutes, secs)
            timerSeconds++
            handler.postDelayed(this, 1000)
        }
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_call)

        val tvNumber = findViewById<TextView>(R.id.tvCallingNumber)
        val tvState = findViewById<TextView>(R.id.tvCallState)

        val btnMute = findViewById<MaterialButton>(R.id.btnMute)
        val btnSpeaker = findViewById<MaterialButton>(R.id.btnSpeaker)
        val btnKeypad = findViewById<MaterialButton>(R.id.btnKeypad)
        val btnEndCall = findViewById<MaterialButton>(R.id.btnEndCall)

        val keypadContainer = findViewById<View>(R.id.keypadContainer)

        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val isOutgoing = intent.getBooleanExtra("is_outgoing", false)

        tvNumber.text = phoneNumber

        if (isOutgoing) {
            CallUtils.placeCall(this, phoneNumber)
            tvState.text = "연결 중..."
        }

        // 통화 시간 타이머 시작
        handler.post(timerRunnable)

        // 통화 종료 이벤트
        lifecycleScope.launch {
            CallEventBus.callEnded.collect {
                finish()
            }
        }

        // 뮤트 토글
        btnMute.setOnClickListener {
            val service = MyInCallService.instance ?: return@setOnClickListener
            isMuted = !isMuted
            service.setMuted(isMuted)
            renderMute(btnMute, isMuted)
        }
        renderMute(btnMute, isMuted)

        // 스피커 토글
        btnSpeaker.setOnClickListener {
            val service = MyInCallService.instance ?: return@setOnClickListener

            val route = if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
            isSpeakerOn = !isSpeakerOn
            service.setAudioRoute(route)

            renderSpeaker(btnSpeaker, isSpeakerOn)
        }
        renderSpeaker(btnSpeaker, isSpeakerOn)

        // 키패드 토글
        btnKeypad.setOnClickListener {
            isKeypadOpen = !isKeypadOpen
            keypadContainer.visibility = if (isKeypadOpen) View.VISIBLE else View.GONE
        }

        // 키패드 숫자 -> DTMF 전송
        fun bindDtmf(buttonId: Int, tone: Char) {
            findViewById<View>(buttonId).setOnClickListener {
                MyInCallService.instance?.sendDtmfTone(tone)
            }
        }

        bindDtmf(R.id.btn1, '1')
        bindDtmf(R.id.btn2, '2')
        bindDtmf(R.id.btn3, '3')
        bindDtmf(R.id.btn4, '4')
        bindDtmf(R.id.btn5, '5')
        bindDtmf(R.id.btn6, '6')
        bindDtmf(R.id.btn7, '7')
        bindDtmf(R.id.btn8, '8')
        bindDtmf(R.id.btn9, '9')
        bindDtmf(R.id.btn0, '0')
        bindDtmf(R.id.btnStar, '*')
        bindDtmf(R.id.btnHash, '#')

        // 통화 종료
        btnEndCall.setOnClickListener {
            MyInCallService.instance?.endCall()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }

    private fun renderMute(btn: MaterialButton, muted: Boolean) {
        if (muted) {
            btn.text = "음소거 해제"
            btn.setIconResource(R.drawable.ic_mic_off)
        } else {
            btn.text = "음소거"
            btn.setIconResource(R.drawable.ic_mic_on)
        }
    }

    private fun renderSpeaker(btn: MaterialButton, speakerOn: Boolean) {
        if (speakerOn) {
            btn.text = "스피커 끄기"
            btn.setIconResource(R.drawable.ic_speaker_on)
        } else {
            btn.text = "스피커"
            btn.setIconResource(R.drawable.ic_speaker_off)
        }
    }
}
