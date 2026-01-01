package com.final_pj.voice.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.telecom.Call
import android.telecom.InCallService
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
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

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null



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
        // m4a 용 멈추기
        stopRecordingSafely()
        // wav 용 멈추기
        stopRecording()
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

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {

                Call.STATE_ACTIVE -> {
                    // 실제 통화 시작
                    Log.d("CALL", "Call ACTIVE")
                    startRecording()
                    startRecordingWav()
                    CallEventBus.notifyCallStarted()

                    // 오디오 캡쳐 시작 (mccp + whisper)
                    startCallMonitoring()


                }

                Call.STATE_DISCONNECTED -> {
                    // ☎ 통화 종료
                    Log.d("CALL", "통화종료!!!!!")
                    // m4a 용
                    stopRecordingSafely()
                    // wav 용
                    stopRecording()
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
    // ------------------
    // wav 로 바로 저장
    // ------------------
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecordingWav() {
        if (isRecording) return

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val outputFile = File(getExternalFilesDir(null), "call_${System.currentTimeMillis()}.wav")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeWavDirect(outputFile, bufferSize, sampleRate, audioRecord!!)
        }.apply { start() }

        Log.d("RECORD", "WAV Recording started: ${outputFile.name}")
    }

    private fun writeWavDirect(file: File, bufferSize: Int, sampleRate: Int, recorder: AudioRecord) {
        val pcmBuffer = ByteArray(bufferSize)
        val outputStream = FileOutputStream(file)

        // WAV 헤더 임시 공간 작성 (44바이트)
        val header = ByteArray(44)
        outputStream.write(header)

        var totalAudioLen = 0L

        while (isRecording) {
            val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
            if (read > 0) {
                outputStream.write(pcmBuffer, 0, read)
                totalAudioLen += read
            }
        }

        recorder.stop()
        recorder.release()

        // WAV 헤더 작성
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        val wavHeader = ByteArray(44)
        wavHeader[0] = 'R'.code.toByte()
        wavHeader[1] = 'I'.code.toByte()
        wavHeader[2] = 'F'.code.toByte()
        wavHeader[3] = 'F'.code.toByte()
        wavHeader[4] = (totalDataLen and 0xff).toByte()
        wavHeader[5] = ((totalDataLen shr 8) and 0xff).toByte()
        wavHeader[6] = ((totalDataLen shr 16) and 0xff).toByte()
        wavHeader[7] = ((totalDataLen shr 24) and 0xff).toByte()
        wavHeader[8] = 'W'.code.toByte()
        wavHeader[9] = 'A'.code.toByte()
        wavHeader[10] = 'V'.code.toByte()
        wavHeader[11] = 'E'.code.toByte()
        wavHeader[12] = 'f'.code.toByte()
        wavHeader[13] = 'm'.code.toByte()
        wavHeader[14] = 't'.code.toByte()
        wavHeader[15] = ' '.code.toByte()
        wavHeader[16] = 16
        wavHeader[20] = 1
        wavHeader[22] = channels.toByte()
        wavHeader[24] = (sampleRate and 0xff).toByte()
        wavHeader[25] = ((sampleRate shr 8) and 0xff).toByte()
        wavHeader[26] = ((sampleRate shr 16) and 0xff).toByte()
        wavHeader[27] = ((sampleRate shr 24) and 0xff).toByte()
        wavHeader[28] = (byteRate and 0xff).toByte()
        wavHeader[29] = ((byteRate shr 8) and 0xff).toByte()
        wavHeader[30] = ((byteRate shr 16) and 0xff).toByte()
        wavHeader[31] = ((byteRate shr 24) and 0xff).toByte()
        wavHeader[32] = ((channels * 16 / 8).toByte())
        wavHeader[34] = 16
        wavHeader[36] = 'd'.code.toByte()
        wavHeader[37] = 'a'.code.toByte()
        wavHeader[38] = 't'.code.toByte()
        wavHeader[39] = 'a'.code.toByte()
        wavHeader[40] = (totalAudioLen and 0xff).toByte()
        wavHeader[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        wavHeader[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        wavHeader[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(wavHeader)
        raf.close()
    }

    private fun stopRecording() {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord = null
        Log.d("RECORD", "WAV Recording stopped")
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
