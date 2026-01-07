package com.final_pj.voice.feature.call.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresPermission
import com.final_pj.voice.bus.CallEventBus
import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.call.activity.IncomingCallActivity
import com.final_pj.voice.feature.stt.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import kotlin.String

class MyInCallService____copy : InCallService() {

    // ====== config ======
    private val baseurl = Constants.BASE_URL
    private val serverUrl = "${baseurl}api/v1/stt"
    private val bestMfccBaseUrl = "${baseurl}api/v1/mfcc"
    private val key32 = "12345678901234567890123456789012".toByteArray()

    // ====== state ======
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false
    private var activeAtMs: Long = 0L

    private var recorder: MediaRecorder? = null
    private var monitoringJob: Job? = null
    
    // 명칭을 실시간 5초 매니저로 바꾸고 싶음
    private lateinit var mfccManager: MFCCManager
    // 이건 sttuploader 로 쭉 진행 (요약, 점수계산)
    private lateinit var sttUploader: SttUploader
    private val sttBuffer = SttBuffer()

    private lateinit var mfccUploader: BestMfccManager

    private var lastKnownCallLogId: Long? = null

    companion object {
        var instance: MyInCallService____copy? = null
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

        mfccManager = MFCCManager(this)

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
                    // 실제 전화가 걸려왔음
                    activeAtMs = System.currentTimeMillis()

                    Log.d("CALL", "Call ACTIVE -> start")
                    CallEventBus.notifyCallStarted()

                    // 녹음은 무조건 시작 (발화자)
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

        monitoringJob = serviceScope.launch {
            val sampleRate = 16000
            val seconds = 5
            val maxSamples = sampleRate * seconds

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize <= 0) {
                Log.e("AUDIO", "Invalid bufferSize: $minBufferSize")
                return@launch
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_DOWNLINK, // 발신 발화자만 딥보이스 체크
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

            try {
                audioRecord.startRecording()

                while (isActive && started) {
                    val readCount = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount <= 0) continue

                    var i = 0
                    while (i < readCount && currentPos < maxSamples) {
                        val s = pcmBuffer[i]
                        audioShort[currentPos] = s
                        audioFloat[currentPos] = s / 32768f
                        currentPos++
                        i++
                    }

                    if (currentPos >= maxSamples) {
                        // 1) MFCC 추론 (로컬)
                        try {
                            mfccManager.processAudioSegment(audioFloat.clone())
                        } catch (e: Exception) {
                            Log.e("MFCC", "processAudioSegment failed: ${e.message}", e)
                        }
                        // 2) 5초 PCM 업로드
                        try {
                            // 이 callId 는 ...한번에 한통화라는 전제로 백엔드 통화 식별용으로 쓰임
                            mfccUploader.uploadPcmShortChunk(callId="1", audioShort.clone())
                        } catch (e: Exception) {
                            Log.e("MFCC_UP", "upload failed: ${e.message}", e)
                        }

                        currentPos = 0
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
    // 녹음 처리 (발화자구분용) => 서버에 보낸후에 삭제할것임
    // =====================
    private fun startRecordingSeparateTalk(){

    }
    // =====================
    // 발화자 구분 녹음 stop
    // =====================

    private fun StopRecordingSeparateTalk(){

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

            // record off면 남기지 않는 게 자연스러움
            if (!isRecordEnabled()) deleteCurrentRecordingFileIfExists()

            onFinished(false)
            return
        }

        // 여기서 업로더는 "특정 파일" 업로드를 받는 메서드가 있으면 제일 좋음
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
