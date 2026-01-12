package com.final_pj.voice.feature.call.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit
import android.media.MediaRecorder
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder.AudioSource
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import okio.ByteString.Companion.toByteString



class MyConnectionService : ConnectionService() {
    class SafeCallRecorder(private val context: Context) {

        private var mediaRecorder: MediaRecorder? = null
        private var audioRecord: AudioRecord? = null
        private var isRecording = false
        private var recordFile: File? = null
        private var streamingJob: Job? = null
        private var ws: WebSocket? = null

        // 권한 체크
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun hasAudioPermission(): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) == PackageManager.PERMISSION_GRANTED
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun startRecording(isRooted: Boolean, sttUrl: String? = null) {

            if (!hasAudioPermission()) {
                Log.e("SafeRecorder", "권한 없음")
                return
            }

            try {
                // 1️⃣ 녹음 파일 준비
                val fileName = "call_record_${System.currentTimeMillis()}.mp4"
                recordFile = File(context.filesDir, fileName)

                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(AudioSource.VOICE_RECOGNITION)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(recordFile?.absolutePath)
                    prepare()
                    start()
                }
                Log.d("SafeRecorder", "MediaRecorder 녹음 시작: ${recordFile?.absolutePath}")
                Log.d("isRooted", "isRooted: ${isRooted}")

                // 2️⃣ AudioRecord 초기화 (루팅 여부에 따라 AudioSource 선택)
                val source = if (isRooted) AudioSource.VOICE_RECOGNITION else AudioSource.VOICE_COMMUNICATION
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

                audioRecord = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("SafeRecorder", "AudioRecord 초기화 실패, source=$source")
                    return
                }

                audioRecord?.startRecording()
                isRecording = true
                Log.d("SafeRecorder", "AudioRecord 녹음 시작")

                // 3️⃣ WebSocket STT 전송 (선택)
                sttUrl?.let { url ->
                    val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
                    val request = Request.Builder().url(url).build()
                    ws = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Log.d("SafeRecorder", "STT WebSocket 연결")
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Log.e("SafeRecorder", "STT WebSocket 실패", t)
                        }
                    })

                    // Coroutine으로 실시간 전송
                    streamingJob = CoroutineScope(Dispatchers.IO).launch {
                        val buffer = ByteArray(bufferSize)
                        while (isRecording) {
                            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                            if (read > 0) {
                                ws?.send(buffer.toByteString(0, read))
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("SafeRecorder", "녹음 시작 실패", e)
            }
        }

        fun stopRecording() {
            try {
                isRecording = false
                streamingJob?.cancel()
                ws?.close(1000, "통화 종료")
                ws = null

                // MediaRecorder 종료
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                Log.d("SafeRecorder", "MediaRecorder 종료")

                // AudioRecord 종료
                audioRecord?.apply {
                    stop()
                    release()
                }
                audioRecord = null
                Log.d("SafeRecorder", "AudioRecord 종료")
            } catch (e: Exception) {
                Log.e("SafeRecorder", "녹음 종료 실패", e)
            }
        }

        fun getRecordedFile(): File? = recordFile
    }
    class MyConnection(private val context: Context) : Connection() {
        private val recorder = SafeCallRecorder(context)

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun onAnswer() {
            setActive()
            val isRooted = checkRoot() // 루팅 체크 함수
            recorder.startRecording(isRooted, sttUrl = "wss://your-stt-server.com/stream")
        }

        override fun onDisconnect() {
            recorder.stopRecording()
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        private fun checkRoot(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val exitCode = process.waitFor()
                //exitCode.exists()
                if (exitCode == 0) true else false
            } catch (e: Exception) {
                false
            }
        }
    }

    // -----------------------------
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = MyConnection(this)
        connection.setInitializing()
        connection.setRinging() // 수신 상태
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = MyConnection(this)
        connection.setInitializing()
        connection.setActive() // 발신은 바로 통화 활성화
        return connection
    }
}
