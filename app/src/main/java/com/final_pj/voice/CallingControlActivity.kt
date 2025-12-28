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
import android.widget.ScrollView
import com.final_pj.voice.util.CallUtils
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.bus.CallEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 전화 중 화면
class CallingControlActivity : AppCompatActivity() {
    private var isMuted = false
    private var isSpeakerOn = false

    // 기존 텍스트들을 저장할 리스트
    private val sttList = mutableListOf<String>()

    // Vosk JSON 결과에서 "text" 필드만 뽑아내는 함수
    private fun extractText(json: String): String {
        return try {
            org.json.JSONObject(json).getString("text")
        } catch (e: Exception) {
            ""
        }
    }

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

        val tvStt = findViewById<TextView>(R.id.tvStt)
        val svSttContainer: ScrollView = findViewById(R.id.svSttContainer)

        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val isOutgoing = intent.getBooleanExtra("is_outgoing", false)

        tvNumber.text = phoneNumber

        if (isOutgoing) {
            // 발신일 때만 전화 걸기
            CallUtils.placeCall(this, phoneNumber)
        }





        // 뮤트 처리
        btnMute.setOnClickListener {
            val service = MyInCallService.instance ?: return@setOnClickListener
            isMuted = !isMuted
            service.setMuted(isMuted)
        }


        // 전화 끊기는 이벤트
        lifecycleScope.launch {
            CallEventBus.callEnded.collect {
                //  UI 업데이트
                tvStt.text = "통화 종료"
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


        lifecycleScope.launch {
            CallEventBus.sttResult.collect { jsonText ->
                // 1. Vosk 결과는 JSON 형태( {"text": "안녕하세요"} )이므로 순수 텍스트만 추출
                // 간단하게 처리하기 위해 정규식이나 JSONObject를 사용합니다.
                val cleanText = extractText(jsonText)

                if (cleanText.isNotBlank()) {
                    // 2. 리스트에 추가
                    sttList.add(cleanText)

                    // 3. 리스트의 내용을 한 줄씩 합쳐서 TextView에 표시
                    // 최신 내용 50개만 유지하고 싶다면: if(sttList.size > 50) sttList.removeAt(0)
                    val fullText = sttList.joinToString("\n")

                    tvStt.text = fullText

                    // 4. 새 메시지가 오면 자동으로 하단 스크롤
                    svSttContainer.post {
                        svSttContainer.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            }
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
