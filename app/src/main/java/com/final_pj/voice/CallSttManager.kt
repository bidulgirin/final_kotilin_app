package com.final_pj.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.vosk.android.StorageService
import org.vosk.Recognizer

class CallSttManager__origin(
    private val context: Context,
    private val onResult: (String) -> Unit
) {

    private var recorder: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var sttThread: Thread? = null
    private var isRunning = false


    fun start() {
        if (isRunning) return

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // 1. 모델을 안전하게 로드하기 위해 StorageService 사용
        // "model"은 assets 폴더 안에 있는 모델 폴더 이름입니다.
        StorageService.unpack(context, "model", "model",
            @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) { loadedModel ->
                try {
                    // 2. 모델 로드 성공 시 Recognizer 생성
                    recognizer = Recognizer(loadedModel, sampleRate.toFloat())

                    // 3. 리코더 설정
                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )

                    if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e("VOSK", "AudioRecord 초기화 실패")
                        return@unpack
                    }

                    recorder?.startRecording()
                    isRunning = true
                    Log.d("VOSK", "녹음 및 인식 시작")

                    // 4. 스레드 시작
                    sttThread = Thread {
                        val buffer = ByteArray(bufferSize)
                        try {
                            // interrupt 상태가 아니고 isRunning이 true일 때만 루프 수행
                            while (isRunning && !Thread.currentThread().isInterrupted) {
                                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                                if (read > 0 && isRunning) {
                                    val currentRec = recognizer
                                    if (currentRec != null) {
                                        if (currentRec.acceptWaveForm(buffer, read)) {
                                            // 문장이 완성됨 (Enter 친 효과)
                                            onResult(currentRec.result)
                                        } else {
                                            // (실시간 타이핑 효과)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("STT", "Thread loop error: ${e.message}")
                        } finally {
                            Log.d("STT", "Thread loop finished")
                        }
                    }.also { it.start() }

                } catch (e: Exception) {
                    Log.e("VOSK", "인식기 초기화 중 오류: ${e.message}")
                }
            },
            { exception ->
                Log.e("VOSK", "모델 압축 해제 실패 (경로 확인 필요): ${exception.message}")
            }
        )
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        // 1. 스레드에 중단 신호를 보내고 종료될 때까지 대기
        sttThread?.apply {
            interrupt()
            try {
                join(500) // 최대 0.5초 동안 스레드가 종료되길 기다림
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        sttThread = null

        // 2. 오디오 리소스 해제
        try {
            recorder?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("STT", "Recorder 해제 중 에러: ${e.message}")
        }
        recorder = null

        // 3. 인식기 해제 (반드시 리코더 다음에)
        recognizer?.close()
        recognizer = null

        Log.d("STT", "STT 매니저가 완전히 정지됨")
    }
}
