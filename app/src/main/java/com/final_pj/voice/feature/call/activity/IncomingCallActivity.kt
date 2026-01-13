package com.final_pj.voice.feature.call.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.call.service.MyInCallService
import com.final_pj.voice.feature.report.VoicePhishingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// 전화가 오면 나타나는 액티비티
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var motionLayout: MotionLayout
    private lateinit var tvNumber: TextView

    // 신고된 전화번호 관련 변수들
    private lateinit var phishingBox: View
    private lateinit var tvPhishingDesc: TextView
    private lateinit var tvReportCount: TextView
    private lateinit var btnBlockNow: View

    // Activity 컨텍스트가 아니라 applicationContext로 repo를 만든다 (DB/Repo 싱글톤 안전)
    private lateinit var phishingRepo: VoicePhishingRepository

    private val mainHandler = Handler(Looper.getMainLooper())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)
        // repo 초기화 (applicationContext 사용)
        val phishingRepo = (application as App).phishingNumber
        // MotionLayout / 번호 텍스트
        motionLayout = findViewById(R.id.callSlideLayout)
        tvNumber = findViewById(R.id.tvNumber)

        phishingBox = findViewById(R.id.phishingBox)
        btnBlockNow = findViewById(R.id.btnBlockNow)
        tvPhishingDesc = findViewById(R.id.tvPhishingDesc)
        tvReportCount = findViewById(R.id.tvReportCount)

        val number = intent.getStringExtra("phone_number").orEmpty()
        tvNumber.text = number

        // 로컬DB 조회 (타임아웃)
        checkPhishing(number)

        // 버튼누르면 바로 차단
        btnBlockNow.setOnClickListener {
            blockNow(number)
        }

        // 슬라이드 완료 이벤트 처리
        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {

            override fun onTransitionCompleted(layout: MotionLayout, currentId: Int) {
                when (currentId) {

                    // 수락 슬라이드 완료
                    R.id.accept -> {
                        if (number.isBlank()) {
                            resetSlider()
                            return
                        }

                        // 전화 받기
                        MyInCallService.currentCall?.answer(0)

                        // 통화 중 화면으로 이동 (수신)
                        val next = Intent(
                            this@IncomingCallActivity,
                            CallingActivity::class.java
                        ).apply {
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

    override fun onStart() {
        super.onStart()
        MyInCallService.currentCall?.registerCallback(callCallback)
    }

    override fun onStop() {
        MyInCallService.currentCall?.unregisterCallback(callCallback)
        super.onStop()
    }

    // 슬라이더를 다시 중앙(시작 상태)으로 되돌림
    private fun resetSlider() {
        motionLayout.progress = 0f
        try {
            motionLayout.setTransition(R.id.start, R.id.accept)
            motionLayout.transitionToStart()
        } catch (_: Exception) {
        }
    }

    // 신고당한 번호 조회
    private fun checkPhishing(rawNumber: String) {
        if (rawNumber.isBlank()) {
            phishingBox.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val hit = try {
                withTimeout(2500L) { phishingRepo.lookupLocal(rawNumber) }
            } catch (_: Exception) {
                null
            }

            if (hit != null) {
                phishingBox.visibility = View.VISIBLE
                tvPhishingDesc.text = hit.description?.takeIf { it.isNotBlank() } ?: "설명 정보 없음"
                tvReportCount.text = "신고 ${hit.reportCount}건"
            } else {
                phishingBox.visibility = View.GONE
            }
        }
    }

    // 바로 차단 이벤트
    private fun blockNow(rawNumber: String) {
        MyInCallService.currentCall?.reject(false, null)
        finish()
        // 시스템 차단목록 추가는 권한/기본다이얼러/버전에 따라 달라서 옵션으로만
        // addToSystemBlockedList(rawNumber)
    }
}
