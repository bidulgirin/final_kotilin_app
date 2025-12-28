package com.final_pj.voice.service

import android.telecom.Call
import android.telecom.InCallService
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.telecom.TelecomManager
import android.util.Log
import com.final_pj.voice.IncomingCallActivity
import java.io.File
import com.final_pj.voice.bus.CallEventBus

/**
 * 시스템 통화 상태를 관리하는 InCallService
 * - 통화 상태 감지
 * - 통화 녹음 시작 / 종료
 * - Activity에 통화 종료 알림 전달
 */
class MyInCallService : InCallService() {

    companion object {
        /** 현재 실행 중인 InCallService 인스턴스 */
        var instance: MyInCallService? = null
            private set

        /** 현재 통화 Call 객체 */
        var currentCall: Call? = null
            private set

        /** 통화 종료 브로드캐스트 액션 */
        const val ACTION_CALL_ENDED = "com.final_pj.voice.CALL_ENDED"
    }


    private fun sendCallEndedBroadcast() {
        val intent = Intent(ACTION_CALL_ENDED)
        sendBroadcast(intent)
    }
    /** 통화 녹음용 MediaRecorder */
    private var recorder: MediaRecorder? = null

    // =====================
    // Service 생명주기
    // =====================

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("MyInCallService", "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRecordingSafely()
        Log.d("MyInCallService", "Service destroyed")
    }

    // =====================
    // 통화 상태 콜백
    // =====================

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        currentCall = call
        call.registerCallback(callCallback)

        Log.d("CALL", "Call added: ${call.details.handle}")

        val direction = call.details.callDirection
        if (direction == Call.Details.DIRECTION_INCOMING) {
            showIncomingCallUI(call)
        }

    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        call.unregisterCallback(callCallback)
        currentCall = null

        Log.d("CALL", "Call removed")
    }

    /**
     * Activity에서 호출하는 통화 종료 요청
     */
    fun endCall() {
        currentCall?.disconnect()
    }

    // =====================
    // Call 상태 변경 감지
    // =====================

    private val callCallback = object : Call.Callback() {

        override fun onStateChanged(call: Call, state: Int) {
            when (state) {

                Call.STATE_ACTIVE -> {
                    // 실제 통화 시작
                    Log.d("CALL", "Call ACTIVE")
                    startRecording()
                }

                Call.STATE_DISCONNECTED -> {
                    // ☎ 통화 종료
                    Log.d("CALL", "통화종료!!!!!")
                    CallEventBus.callEnded.tryEmit(Unit)
                    stopRecordingSafely()
                }
            }
        }

    }
    // -----------------
    // 수신 UI 띄우는 함수
    // -----------------
    private fun showIncomingCallUI(call: Call) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("phone_number", call.details.handle?.schemeSpecificPart)
        }

        startActivity(intent)
    }

    // =====================
    // 녹음 처리
    // =====================

    /**
     * 통화 녹음 시작
     * - 이미 녹음 중이면 무시
     */
    private fun startRecording() {
        if (recorder != null) return

        try {
            val outputFile = File(
                getExternalFilesDir(null),
                "call_${System.currentTimeMillis()}.m4a"
            )

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(outputFile.absolutePath)

                prepare()
                start()
            }

            Log.d("RECORD", "Recording started: ${outputFile.name}")

        } catch (e: Exception) {
            Log.e("RECORD", "Recording start failed", e)
            recorder = null
        }
    }

    /**
     * 통화 녹음 안전 종료
     * - 예외 상황에서도 크래시 방지
     */
    private fun stopRecordingSafely() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("RECORD", "Recording stop failed", e)
        } finally {
            recorder = null
        }
    }

}
