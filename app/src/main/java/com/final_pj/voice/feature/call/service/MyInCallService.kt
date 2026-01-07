package com.final_pj.voice.feature.call.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
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

class MyInCallService : InCallService() {

    // config
    private val baseurl = Constants.BASE_URL

    // 기존 단일(mixed) STT 업로드
    private val serverUrl = "${baseurl}api/v1/stt"

    // MFCC(5초) 업로드
    private val bestMfccBaseUrl = "${baseurl}api/v1/mfcc"

    // 새로 추가: uplink/downlink 분리 트랙 업로드 + 처리 엔드포인트(권장: wav로 받는 별도 엔드포인트)
    private val dualWavUrl = "${baseurl}api/v1/stt/dual_wav"

    private val key32 = "12345678901234567890123456789012".toByteArray()

    // state
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false
    private var activeAtMs: Long = 0L

    // 통화마다 유니크한 세션키 (MFCC 5초 업로드 식별용)
    private var sessionId: String? = null

    // 폰에 저장될 1개 파일(mixed)
    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    // 5초 단위 처리 담당 (명칭 변경 반영)
    private lateinit var realtime5sMfccManager: MFCCManager

    // mixed 파일 기반 STT/요약 업로더 (기존 유지)
    private lateinit var sttUploader: SttUploader
    private val sttBuffer = SttBuffer()

    // 5초 PCM 업로더 (기존 유지)
    private lateinit var mfccUploader: BestMfccManager

    // uplink/downlink 분리 레코더 + 업로더
    private var speakerSplitRecorder: SpeakerSplitRecorder? = null
    private lateinit var dualWavUploader: DualWavUploader

    // 분리 임시 파일들 (통화 종료 후 업로드하고 삭제)
    private var uplinkPcmFile: File? = null
    private var downlinkPcmFile: File? = null
    private var uplinkWavFile: File? = null
    private var downlinkWavFile: File? = null

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

        // 새로 추가: STT/분석 정책
        // stt_mode: both|uplink|downlink
        const val DUAL_STT_MODE = "dual_stt_mode"
        // analysis_target: downlink|uplink|both|none
        const val DUAL_ANALYSIS_TARGET = "dual_analysis_target"
        // return_mode: compat|full
        const val DUAL_RETURN_MODE = "dual_return_mode"
    }

    private fun isRecordEnabled(): Boolean {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(SettingKeys.RECORD_ENABLED, true)
    }

    private fun isSummaryEnabled(): Boolean {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(SettingKeys.SUMMARY_ENABLED, true)
    }

    private fun dualSttMode(): String {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getString(SettingKeys.DUAL_STT_MODE, "both") ?: "both"
    }

    private fun dualAnalysisTarget(): String {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getString(SettingKeys.DUAL_ANALYSIS_TARGET, "downlink") ?: "downlink"
    }

    private fun dualReturnMode(): String {
        val prefs = getSharedPreferences(SettingKeys.PREF_NAME, MODE_PRIVATE)
        return prefs.getString(SettingKeys.DUAL_RETURN_MODE, "compat") ?: "compat"
    }

    // record off 일 때 mixed 파일 자동 삭제 정책
    @Volatile private var deleteRecordingAfterUpload: Boolean = false

    private fun deleteCurrentRecordingFileIfExists() {
        val f = currentRecordingFile ?: return
        try {
            if (f.exists()) {
                val ok = f.delete()
                Log.d("RECORD", "auto-delete=$ok, file=${f.name}")
            }
        } catch (_: Exception) {
        }
        currentRecordingFile = null
    }

    private fun deleteSpeakerTempFiles() {
        listOf(uplinkPcmFile, downlinkPcmFile, uplinkWavFile, downlinkWavFile).forEach { f ->
            try { if (f != null && f.exists()) f.delete() } catch (_: Exception) {}
        }
        uplinkPcmFile = null
        downlinkPcmFile = null
        uplinkWavFile = null
        downlinkWavFile = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 5초 단위 MFCC 로컬 처리
        realtime5sMfccManager = MFCCManager(this)

        // mixed m4a 업로더 (기존 유지)
        sttUploader = SttUploader(
            this,
            serverUrl = serverUrl,
            key32 = key32,
            buffer = sttBuffer,
            gson = Gson()
        )
        sttUploader.start()

        // MFCC 업로더 (기존 유지)
        val crypto: AudioCrypto = AesCbcCrypto(key32)
        val mfccEndpoint = ensureEndsWithPath(bestMfccBaseUrl, "mfcc")
        mfccUploader = BestMfccManager(endpointUrl = mfccEndpoint, crypto = crypto)

        // uplink/downlink wav 업로더
        dualWavUploader = DualWavUploader(endpointUrl = dualWavUrl, key32 = key32)

        Log.d("MyInCallService", "Service created")
    }

    override fun onDestroy() {
        instance = null

        stopSpeakerSplit()
        stopRecordingSafely()
        deleteSpeakerTempFiles()

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call

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

        started = false
        stopSpeakerSplit()
        stopRecordingSafely()
        deleteSpeakerTempFiles()

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
                    if (started) return
                    started = true
                    activeAtMs = System.currentTimeMillis()
                    sessionId = activeAtMs.toString()

                    CallEventBus.notifyCallStarted()

                    // 1) 폰 저장용 1파일(mixed) 녹음 시작
                    startRecordingMixed()

                    // record off는 mixed 파일 자동삭제 정책 의미
                    deleteRecordingAfterUpload = (!isRecordEnabled() && isSummaryEnabled())

                    // 2) uplink/downlink 분리 녹음 + downlink 5초 MFCC 처리/업로드 시작
                    startSpeakerSplit()
                }

                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    started = false

                    // 먼저 레코더를 정지해서 파일 핸들을 닫음
                    stopSpeakerSplit()
                    stopRecordingSafely()

                    val recordEnabled = isRecordEnabled()
                    val summaryEnabled = isSummaryEnabled()

                    val duration = System.currentTimeMillis() - activeAtMs
                    if (duration < 5000) {
                        if (!recordEnabled) deleteCurrentRecordingFileIfExists()
                        deleteSpeakerTempFiles()
                        CallEventBus.notifyCallEnded()
                        return
                    }

                    if (!summaryEnabled) {
                        if (!recordEnabled) deleteCurrentRecordingFileIfExists()
                        deleteSpeakerTempFiles()
                        CallEventBus.notifyCallEnded()
                        return
                    }

                    // callLogId 확보 (통화 단위 식별)
                    val callLogId = resolveCurrentCallLogId(callEndTimeMs = System.currentTimeMillis())
                    if (callLogId == null) {
                        if (!recordEnabled) deleteCurrentRecordingFileIfExists()
                        deleteSpeakerTempFiles()
                        CallEventBus.notifyCallEnded()
                        return
                    }

                    // 1) mixed 파일 업로드 (기존 STT 파이프라인 유지)
                    onSaveStt(callLogId.toString()) { sttOk ->
                        Log.d("UPLOAD", "mixed stt ok=$sttOk")

                        // 2) uplink/downlink 분리 wav 업로드 + 서버에서 STT/분석 수행(정책 파라미터 전송)
                        uploadTwoSpeakersDual(
                            callId = callLogId.toString(),
                            onFinished = { dualOk ->
                                Log.d("UPLOAD", "dual wav ok=$dualOk")

                                // 분리 임시 파일은 무조건 삭제
                                deleteSpeakerTempFiles()

                                // 저장 정책에 따라 mixed 파일 삭제
                                if (deleteRecordingAfterUpload) deleteCurrentRecordingFileIfExists()
                            }
                        )
                    }

                    CallEventBus.notifyCallEnded()
                }
            }
        }
    }

    // 폰 저장용 1파일(mixed)
    private fun startRecordingMixed() {
        if (recorder != null) return

        try {
            val outputFile = File(getExternalFilesDir(null), "call_${System.currentTimeMillis()}.m4a")
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
        } catch (e: Exception) {
            Log.e("RECORD", "Recording start failed", e)
            recorder = null
            currentRecordingFile = null
        }
    }

    private fun stopRecordingSafely() {
        val r = recorder ?: return
        try { r.stop() } catch (_: Exception) {
        } finally {
            try { r.reset(); r.release() } catch (_: Exception) {}
            recorder = null
        }
    }

    // uplink/downlink 분리 시작
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startSpeakerSplit() {
        stopSpeakerSplit()

        val dir = File(getExternalFilesDir(null), "tmp_split").apply { mkdirs() }
        uplinkPcmFile = File(dir, "uplink_${System.currentTimeMillis()}.pcm")
        downlinkPcmFile = File(dir, "downlink_${System.currentTimeMillis()}.pcm")

        speakerSplitRecorder = SpeakerSplitRecorder(
            scope = serviceScope,
            sampleRate = 16000,
            chunkSeconds = 5,
            onDownlinkChunk = { shorts, floats ->
                if (!started) return@SpeakerSplitRecorder

                // 1) MFCC 로컬 처리
                try {
                    realtime5sMfccManager.processAudioSegment(floats)
                } catch (e: Exception) {
                    Log.e("MFCC", "processAudioSegment failed: ${e.message}", e)
                }

                // 2) 5초 PCM 업로드 (세션키 사용)
                val sid = sessionId ?: "session"
                try {
                    mfccUploader.uploadPcmShortChunk(callId = sid, shorts)
                } catch (e: Exception) {
                    Log.e("MFCC_UP", "upload failed: ${e.message}", e)
                }
            }
        )

        speakerSplitRecorder?.start(
            uplinkOutPcm = uplinkPcmFile!!,
            downlinkOutPcm = downlinkPcmFile!!
        )
    }

    private fun stopSpeakerSplit() {
        speakerSplitRecorder?.stop()
        speakerSplitRecorder = null
    }

    // 통화 종료 후: uplink/downlink pcm -> wav 변환 -> dual_wav 업로드
    private fun uploadTwoSpeakersDual(callId: String, onFinished: (Boolean) -> Unit) {
        val upPcm = uplinkPcmFile
        val dnPcm = downlinkPcmFile
        if (upPcm == null || dnPcm == null || !upPcm.exists() || !dnPcm.exists()) {
            onFinished(false)
            return
        }

        try {
            val dir = upPcm.parentFile ?: getExternalFilesDir(null)
            uplinkWavFile = File(dir, "uplink_${callId}.wav")
            downlinkWavFile = File(dir, "downlink_${callId}.wav")

            WavUtil.pcm16leToWav(upPcm, uplinkWavFile!!, sampleRate = 16000, channels = 1)
            WavUtil.pcm16leToWav(dnPcm, downlinkWavFile!!, sampleRate = 16000, channels = 1)

            val sttMode = dualSttMode()
            val analysisTarget = dualAnalysisTarget()
            val returnMode = dualReturnMode()

            val sid = sessionId
            dualWavUploader.uploadDualWav(
                callId = callId,
                mfccCallId = sid,
                uplinkWav = uplinkWavFile!!,
                downlinkWav = downlinkWavFile!!,
                llm = true,
                sttMode = sttMode,
                analysisTarget = analysisTarget,
                returnMode = returnMode,
                onDone = onFinished
            )
        } catch (e: Exception) {
            Log.e("DUAL_UP", "dual upload failed: ${e.message}", e)
            onFinished(false)
        }
    }

    // mixed 파일 STT 업로드 (callLogId를 외부에서 받아서 중복 resolve 방지)
    fun onSaveStt(callId: String, onFinished: (success: Boolean) -> Unit) {
        if (!isSummaryEnabled()) {
            if (!isRecordEnabled()) deleteCurrentRecordingFileIfExists()
            onFinished(false)
            return
        }

        val file = currentRecordingFile
        if (file == null || !file.exists()) {
            onFinished(false)
            return
        }

        sttUploader.enqueueUploadFile(callId, file) { success ->
            onFinished(success)
        }
    }

    // UI
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

    // CallLog baseline / resolve
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

    // URL helper
    private fun ensureEndsWithPath(base: String, leaf: String): String {
        val b = base.trimEnd('/')
        return if (b.endsWith("/$leaf")) b else "$b/$leaf"
    }
}
