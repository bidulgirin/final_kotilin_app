package com.final_pj.voice

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import com.final_pj.voice.service.MyInCallService

// 전화가 오면 나타나는 액티비티
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var motionLayout: MotionLayout
    private lateinit var tvNumber: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // MotionLayout / 번호 텍스트
        motionLayout = findViewById(R.id.callSlideLayout)
        tvNumber = findViewById(R.id.tvNumber)

        val number = intent.getStringExtra("phone_number").orEmpty()
        tvNumber.text = number

        // 슬라이드 완료 이벤트 처리
        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {

            override fun onTransitionCompleted(layout: MotionLayout, currentId: Int) {
                when (currentId) {

                    // 수락 슬라이드 완료
                    R.id.accept -> {
                        if (number.isBlank()) {
                            // 번호가 없으면 안전하게 초기화만
                            resetSlider()
                            return
                        }

                        // 전화 받기
                        MyInCallService.currentCall?.answer(0)

                        // 📱 통화 중 화면으로 이동 (수신)
                        val next = Intent(this@IncomingCallActivity, CallingControlActivity::class.java).apply {
                            putExtra("phone_number", number)
                            putExtra("is_outgoing", false)
                        }
                        startActivity(next)

                        finish()
                    }

                    // 거절 슬라이드 완료
                    R.id.reject -> {
                        MyInCallService.currentCall?.reject(false, null)
                        finish()
                    }
                }
            }

            override fun onTransitionStarted(layout: MotionLayout, startId: Int, endId: Int) {}
            override fun onTransitionChange(
                layout: MotionLayout,
                startId: Int,
                endId: Int,
                progress: Float
            ) {}

            override fun onTransitionTrigger(
                layout: MotionLayout,
                triggerId: Int,
                positive: Boolean,
                progress: Float
            ) {}
        })
    }

    /**
     * 슬라이더를 다시 중앙(시작 상태)으로 되돌림
     * - finish() 안 하고 화면 유지할 때(예: 번호 없음, 테스트 등) 안전장치
     */
    private fun resetSlider() {
        motionLayout.progress = 0f
        // 강제로 start로
        try {
            motionLayout.setTransition(R.id.start, R.id.accept) // 임시 transition 지정
            motionLayout.transitionToStart()
        } catch (_: Exception) {
            // scene 구성에 따라 예외가 날 수 있어 방어
        }
    }
}
