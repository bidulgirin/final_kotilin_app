package com.final_pj.voice.service

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.InCallService
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.final_pj.voice.IncomingCallActivity
import com.final_pj.voice.MCCPManager
import java.io.File
import com.final_pj.voice.bus.CallEventBus
import com.final_pj.voice.util.encryptAudioBuffer.encryptAudioBuffer
import com.final_pj.voice.util.encryptAudioBuffer.sendAudioToServer

/**
 * 시스템 통화 상태를 관리하는 InCallService
 * - 통화 상태 감지
 * - 통화 녹음 시작 / 종료
 * - Activity에 통화 종료 알림 전달
 */
class MyInCallService : InCallService() {

    // mccp 모델
    private lateinit var mccpManager: MCCPManager
    private var isRunning = false


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

        mccpManager = MCCPManager(this) // 매니저 초기화 (모델 로드 포함)
        Log.d("MyInCallService", "Service created")

    }



    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRecordingSafely()
        isRunning = false // 종료 시 mccp 루프 정지
        Log.d("MyInCallService", "Service destroyed")
    }

    // =====================
    // 통화 상태 콜백
    // =====================

    // 전화 신호 왔져염
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
        // 통화가 종료되면 STT 정지
        //sttManager.stop()
        isRunning = false // 종료 시 mccp 루프 정지
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
                    CallEventBus.notifyCallStarted()

                    // 오디오 캡쳐 시작 (mccp)
                    startCallMonitoring()

                }

                Call.STATE_DISCONNECTED -> {
                    // ☎ 통화 종료
                    Log.d("CALL", "통화종료!!!!!")
                    stopRecordingSafely()
                    // 통화 종료 했다고 알려주는 함수
                    CallEventBus.notifyCallEnded()
                }
            }
        }

    }

    // -----------------
    // 수신 UI 띄우는 함수
    // -----------------

    @SuppressLint("MissingPermission") // 퍼미션있는지 확인하는 코드 필요함
    private fun startCallMonitoring() {
        isRunning = true

        // 별도 스레드에서 오디오 캡처 시작
        Thread {
            val sampleRate = 16000
            val seconds = 5
            val maxSamples = sampleRate * seconds

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            )

            require(minBufferSize > 0) { "Invalid bufferSize: $minBufferSize" }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL, // 유지 가능
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AUDIO", "AudioRecord init failed")
                return@Thread
            }

            audioRecord.startRecording()

            val pcmBuffer = ShortArray(minBufferSize)
            val audioBuffer = FloatArray(maxSamples)
            var currentPos = 0

            while (isRunning) {
                val readCount = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)

                if (readCount > 0) {
                    for (i in 0 until readCount) {
                        if (currentPos < maxSamples) {
                            audioBuffer[currentPos++] = pcmBuffer[i] / 32768f
                        }
                    }

                    // 백엔드 테스트
                    val segmentToSend = audioBuffer.clone()
                    val encrypted = encryptAudioBuffer(segmentToSend, "1234567890abcdef")
                    sendAudioToServer(encrypted, "192.168.3.10")
                }

                if (currentPos >= maxSamples) {
                    mccpManager.processAudioSegment(audioBuffer.clone())
                    currentPos = 0
                }
            }

            audioRecord.stop()
            audioRecord.release()
        }.start()
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
                "call_${System.currentTimeMillis()}.m4a" // wav 파일로 모두 통일 (되는지 테스트해야함)
            )

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
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




    // 필요한것

    // 1. 전화가 시작되면 모델 로드

    // 2. 통화중에 상대방/나 의 통화 음성 모델 데이터로 들어감

    // 3. stt 변환

    // 4. 결과 출력 (혹은 모델 저장)

    // 5. 통화가 끝나면 ondestory 에서 모두 없애기



}
