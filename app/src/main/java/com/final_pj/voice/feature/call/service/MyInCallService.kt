package com.final_pj.voice.feature.call.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.final_pj.voice.bus.CallEventBus
import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.call.activity.IncomingCallActivity
import com.final_pj.voice.feature.notify.NotificationHelper
import com.final_pj.voice.feature.stt.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import kotlin.String
import kotlin.math.log10
import kotlin.math.sqrt


class MyInCallService : InCallService() {



    // ====== config ======
    private val baseurl = Constants.BASE_URL
    private val serverUrl = "${baseurl}/api/v1/stt"
    private val bestMfccBaseUrl = "${baseurl}/api/v1/mfcc"
    private val key32 = "12345678901234567890123456789012".toByteArray()

    // ====== state ======
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false
    private var activeAtMs: Long = 0L

    private var recorder: MediaRecorder? = null
    private var monitoringJob: Job? = null

    //private lateinit var mfccManager: MFCCManager
    // 이건 sttuploader 로 쭉 진행 (요약, 점수계산)
    private lateinit var sttUploader: SttUploader
    private val sttBuffer = SttBuffer()

    private lateinit var mfccUploader: BestMfccManager

    private var lastKnownCallLogId: Long? = null

    companion object {
        var instance: MyInCallService? = null
            private set

        var currentCall: Call? = null
            private set
    }
    // 기능 on/off
    private object SettingKeys {
        const val PREF_NAME = "settings"
        const val RECORD_ENABLED = "record_enabled"
        const val SUMMARY_ENABLED = "summary_enabled"
    }
    private fun calcDbFs(pcm: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val v = pcm[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / count) / 32768.0
        return 20.0 * log10(rms + 1e-9) // -inf 방지
    }
    private fun isRecordEnabled(): Boolean {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(SettingKeys.RECORD_ENABLED, true)
    }

    private fun isSummaryEnabled(): Boolean {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(SettingKeys.SUMMARY_ENABLED, true)
    }
    // “통화로 생성된 녹음파일”을 추적하기 위한 멤버
    @Volatile private var deleteRecordingAfterUpload: Boolean = false
    private var currentRecordingFile: File? = null

    private fun deleteCurrentRecordingFileIfExists() {
        val f = currentRecordingFile ?: return
        if (f.exists()) {
            val ok = f.delete()
            Log.d("RECORD", "auto-delete=${ok}, file=${f.name}")
        }
        currentRecordingFile = null
    }


    override fun onCreate() {
        super.onCreate()
        instance = this

        //mfccManager = MFCCManager(this)

        sttUploader = SttUploader(
            this,
            serverUrl = serverUrl,
            key32 = key32,
            buffer = sttBuffer,
            gson = Gson()
        )
        sttUploader.start()

        val crypto: AudioCrypto = AesCbcCrypto(key32)

        // baseUrl이 이미 /mfcc 라면 endpoint는 그대로, 아니면 /mfcc 붙임
        val mfccEndpoint = ensureEndsWithPath(bestMfccBaseUrl, "mfcc")
        mfccUploader = BestMfccManager(
            endpointUrl = mfccEndpoint,
            crypto = crypto
        )

        Log.d("MyInCallService", "Service created")
    }

    override fun onDestroy() {
        Log.d("MyInCallService", "Service destroyed")

        instance = null

        stopMonitoring()
        stopRecordingSafely()

        serviceScope.cancel()


        super.onDestroy()
    }

    // =====================
    // 통화 상태 콜백
    // =====================
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call

        Log.d("CALL", "Call added: ${call.details.handle}")

        markCallLogBaseline()

        call.registerCallback(callCallback)

        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            showIncomingCallUI(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        call.unregisterCallback(callCallback)
        if (currentCall == call) currentCall = null

        // 방어적으로 종료
        started = false
        stopMonitoring()
        stopRecordingSafely()

        Log.d("CALL", "onCallRemoved")
    }

    fun endCall() {
        currentCall?.disconnect()
    }

    private val callCallback = object : Call.Callback() {

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> {
                    if (started) {
                        Log.d("CALL", "ACTIVE ignored (already started)")
                        return
                    }
                    started = true
                    activeAtMs = System.currentTimeMillis()

                    Log.d("CALL", "Call ACTIVE -> start")
                    CallEventBus.notifyCallStarted()

                    // 녹음은 무조건 시작
                    startRecording()

                    // “녹음 off”는 '파일 자동삭제 정책' 의미
                    deleteRecordingAfterUpload = (!isRecordEnabled() && isSummaryEnabled())

                    startMonitoring()
                }

                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    started = false

                    stopMonitoring()
                    stopRecordingSafely()

                    val recordEnabled = isRecordEnabled()
                    val summaryEnabled = isSummaryEnabled()

                    val duration = System.currentTimeMillis() - activeAtMs
                    if (duration < 5000) {
                        Log.d("CALL", "too short ($duration ms) -> skip upload")
                        if (!recordEnabled) deleteCurrentRecordingFileIfExists()
                        CallEventBus.notifyCallEnded()
                        return
                    }

                    // 요약 OFF면 onSaveStt를 호출하지 않음
                    if (!summaryEnabled) {
                        Log.d("SUMMARY", "summary_enabled=false -> skip onSaveStt()")
                        if (!recordEnabled) deleteCurrentRecordingFileIfExists()
                        CallEventBus.notifyCallEnded()
                        return
                    }

                    // 요약 ON이면 업로드 -> 완료 후 삭제
                    onSaveStt { success ->
                        Log.d("UPLOAD", "finished success=$success")
                        if (deleteRecordingAfterUpload) {
                            deleteCurrentRecordingFileIfExists()
                        }
                    }

                    CallEventBus.notifyCallEnded()
                }
            }
        }
    }

    // =====================
    // 모니터링(5초) : MFCC 추론 + 업로드
    // =====================
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startMonitoring() {
        stopMonitoring() // 중복 방지

        val cautionThreshold = 0.90
        var lastNotifyAt = 0L
        val cooldownMs = 30_000L

        monitoringJob = serviceScope.launch {
            val sampleRate = 16000
            val seconds = 5
            val maxSamples = sampleRate * seconds // 80,000 (5초 "소리" 누적 기준)

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize <= 0) {
                Log.e("AUDIO", "Invalid bufferSize: $minBufferSize")
                return@launch
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_DOWNLINK,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AUDIO", "AudioRecord init failed")
                audioRecord.release()
                return@launch
            }

            val pcmBuffer = ShortArray(minBufferSize)
            val audioFloat = FloatArray(maxSamples)
            val audioShort = ShortArray(maxSamples)
            var currentPos = 0

            // -----------------------------
            // VAD 파라미터 (여기 튜닝!)
            // -----------------------------
            val vadDbThreshold = -45.0 // 환경 따라 -50 ~ -35 사이 튜닝 권장

            // 발화 시작 직전 조금 붙이기(잘림 방지)
            val preRollMs = 200
            val preRollSamples = sampleRate * preRollMs / 1000
            val preRollRing = ShortArray(preRollSamples)
            var preIdx = 0
            var preFilled = 0

            // 발화 끝난 직후 꼬리 무음 조금 포함(자연스럽게)
            val hangoverMs = 300
            val hangoverSamples = sampleRate * hangoverMs / 1000

            var inVoice = false
            var silenceSamples = 0

            fun pushPreRoll(src: ShortArray, count: Int) {
                for (i in 0 until count) {
                    preRollRing[preIdx] = src[i]
                    preIdx = (preIdx + 1) % preRollSamples
                    if (preFilled < preRollSamples) preFilled++
                }
            }

            fun flushPreRollToMain() {
                val start = (preIdx - preFilled + preRollSamples) % preRollSamples
                for (k in 0 until preFilled) {
                    if (currentPos >= maxSamples) break
                    val s = preRollRing[(start + k) % preRollSamples]
                    audioShort[currentPos] = s
                    audioFloat[currentPos] = s / 32768f
                    currentPos++
                }
                preFilled = 0
            }

            fun appendToMain(src: ShortArray, count: Int) {
                var i = 0
                while (i < count && currentPos < maxSamples) {
                    val s = src[i]
                    audioShort[currentPos] = s
                    audioFloat[currentPos] = s / 32768f
                    currentPos++
                    i++
                }
            }

            try {
                audioRecord.startRecording()

                while (isActive && started) {
                    val readCount = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount <= 0) continue

                    // pre-roll 링버퍼는 항상 업데이트(발화 시작 직전 포함용)
                    if (preRollSamples > 0) pushPreRoll(pcmBuffer, readCount)

                    // 현재 프레임 dB 측정
                    val db = calcDbFs(pcmBuffer, readCount)
                    val isVoiceFrame = db > vadDbThreshold

                    // 필요하면 로그로 튜닝
                    // Log.d("VAD", "db=$db, voice=$isVoiceFrame, pos=$currentPos")

                    if (isVoiceFrame) {
                        if (!inVoice) {
                            // 발화 시작: 직전 pre-roll 붙이기
                            if (preRollSamples > 0) flushPreRollToMain()
                        }
                        inVoice = true
                        silenceSamples = 0

                        // 발화 프레임은 누적
                        appendToMain(pcmBuffer, readCount)

                    } else {
                        // 무음 프레임
                        if (inVoice) {
                            silenceSamples += readCount

                            // 발화 끝부분 자연스럽게: hangover 만큼만 무음 포함
                            if (silenceSamples < hangoverSamples) {
                                appendToMain(pcmBuffer, readCount)
                            } else {
                                inVoice = false
                                silenceSamples = 0
                                // 이후 무음은 main에 안 넣음(=무음 제거)
                            }
                        }
                        // inVoice == false 면 완전 무시(무음 제거)
                    }

                    // "소리 누적" 5초 채우면 업로드
                    if (currentPos >= maxSamples) {
                        try {
                            val chunkCopy = audioShort.clone() // enqueue 비동기라 clone 고정

                            mfccUploader.uploadPcmShortChunk(
                                callId = "1",
                                chunk = chunkCopy,
                                onResult = { res ->
                                    val now = System.currentTimeMillis()
                                    val should = res.shouldAlert || (res.phishingScore >= cautionThreshold)

                                    Log.d("VP", "server score=${res.phishingScore}, should_alert=${res.shouldAlert}")

                                    if (should && (now - lastNotifyAt) >= cooldownMs) {
                                        // Android 13+만 알림 런타임 권한 필요
                                        val hasPermission =
                                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                                    ContextCompat.checkSelfPermission(
                                                        applicationContext,
                                                        Manifest.permission.POST_NOTIFICATIONS
                                                    ) == PackageManager.PERMISSION_GRANTED

                                        if (!hasPermission) {
                                            Log.w("VP", "POST_NOTIFICATIONS not granted -> skip notify")
                                            return@uploadPcmShortChunk
                                        }

                                        lastNotifyAt = now

                                        NotificationHelper.showAlert(
                                            context = applicationContext,
                                            title = "주의",
                                            message = "딥 보이스 점수: ${"%.2f".format(res.phishingScore)}\n통화 내용을 주의하세요."
                                        )
                                    }
                                },
                                onError = { e ->
                                    Log.e("MFCC_UP", "upload/check failed: ${e.message}", e)
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("MFCC_UP", "upload failed: ${e.message}", e)
                        } finally {
                            currentPos = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AUDIO", "monitoring loop error: ${e.message}", e)
            } finally {
                try { audioRecord.stop() } catch (_: Exception) {}
                audioRecord.release()
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    // =====================
    // 녹음 처리 (저장용 m4a)
    // =====================
    private fun startRecording() {
        if (recorder != null) return

        try {
            val outputFile = File(
                getExternalFilesDir(null),
                "call_${System.currentTimeMillis()}.m4a"
            )
            currentRecordingFile = outputFile

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
            currentRecordingFile = null // 중요
        }
    }


    private fun stopRecordingSafely() {
        val r = recorder ?: return

        try {
            r.stop()
        } catch (e: Exception) {
            Log.e("RECORD", "Recorder stop failed", e)
        } finally {
            try {
                r.reset()
                r.release()
            } catch (e: Exception) {
                Log.e("RECORD", "Recorder release failed", e)
            } finally {
                recorder = null
            }
        }
    }

    // =====================
    // 통화 종료 후 STT 저장 업로드
    // =====================
    fun onSaveStt(onFinished: (success: Boolean) -> Unit) {
        if (!isSummaryEnabled()) {
            Log.d("SUMMARY", "summary_enabled=false -> skip onSaveStt()")
            if (!isRecordEnabled()) deleteCurrentRecordingFileIfExists()
            onFinished(false)
            return
        }

        val file = currentRecordingFile
        if (file == null || !file.exists()) {
            Log.e("STT", "No currentRecordingFile to upload")
            onFinished(false)
            return
        }

        val callLogId = resolveCurrentCallLogId(callEndTimeMs = System.currentTimeMillis())
        if (callLogId == null) {
            Log.w("CALLLOG", "Failed to resolve callLogId")

            // ✅ record off면 남기지 않는 게 자연스러움
            if (!isRecordEnabled()) deleteCurrentRecordingFileIfExists()

            onFinished(false)
            return
        }

        // ✅ 여기서 업로더는 "특정 파일" 업로드를 받는 메서드가 있으면 제일 좋음
        sttUploader.enqueueUploadFile(callLogId.toString(), file) { success ->
            onFinished(success)
        }
    }



    // =====================
    // UI
    // =====================
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

    fun sendDtmfTone(tone: Char) {
        val call = currentCall ?: return
        call.playDtmfTone(tone)
        mainHandler.postDelayed({ call.stopDtmfTone() }, 150L)
    }

    // =====================
    // CallLog baseline / resolve
    // =====================
    @SuppressLint("MissingPermission")
    fun markCallLogBaseline() {
        lastKnownCallLogId = getLatestCallLogId()
    }

    @SuppressLint("MissingPermission")
    fun resolveCurrentCallLogId(
        callEndTimeMs: Long,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 250
    ): Long? {
        val baseline = lastKnownCallLogId
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val latest = getLatestCallLogId()
            if (latest != null && latest != baseline) return latest

            val matched = findCallLogIdNear(
                phoneNumber = null,
                centerTimeMs = callEndTimeMs,
                windowMs = 60_000
            )
            if (matched != null && matched != baseline) return matched

            Thread.sleep(pollIntervalMs)
        }

        val finalLatest = getLatestCallLogId()
        if (finalLatest != null && finalLatest != baseline) return finalLatest

        return null
    }

    @SuppressLint("MissingPermission")
    private fun getLatestCallLogId(): Long? {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.DATE)
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"

        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun findCallLogIdNear(
        phoneNumber: String?,
        centerTimeMs: Long,
        windowMs: Long
    ): Long? {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE)

        val selection = buildString {
            append("${CallLog.Calls.DATE} BETWEEN ? AND ?")
            if (!phoneNumber.isNullOrBlank()) append(" AND ${CallLog.Calls.NUMBER} = ?")
        }

        val args = mutableListOf(
            (centerTimeMs - windowMs).toString(),
            (centerTimeMs + windowMs).toString()
        )
        if (!phoneNumber.isNullOrBlank()) args.add(phoneNumber)

        val sortOrder = "${CallLog.Calls.DATE} DESC"

        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            args.toTypedArray(),
            sortOrder
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    // =====================
    // URL helper
    // =====================
    private fun ensureEndsWithPath(base: String, leaf: String): String {
        val b = base.trimEnd('/')
        return if (b.endsWith("/$leaf")) b else "$b/$leaf"
    }
}


/*
* 현재 문제 20260108
* 1. 발화자 구분을 해서 수신자/발신자 중 발신자를 탐지하는 모델에 돌려야 하는 상황
* 2. 무조건 uplink 가 나라는 보장이 없음
* 3. 그러므로 발신일때 수신일때 라는 조건이 붙어야함
* 4. 애초에 백엔드와 통신이 안되고 있음 
* 5. 로직도 2번 돌아.
* 6. open 도 다시 해야함
* 7. 일단 faiss search 기능먼저 하자
* 백엔드 부분은 제쳐두고 이것을 기반으로 다시 작성해야함
* */
