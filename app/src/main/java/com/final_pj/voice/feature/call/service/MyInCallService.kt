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
import androidx.core.content.ContextCompat
import com.final_pj.voice.core.bus.CallEventBus
import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.call.activity.IncomingCallActivity
import com.final_pj.voice.feature.notify.NotificationHelper
import com.final_pj.voice.feature.stt.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class MyInCallService : InCallService() {

    private val baseurl = Constants.BASE_URL
    private val serverUrl = "${baseurl}/api/v1/stt"
    private val bestMfccBaseUrl = "${baseurl}/api/v1/real_time"
    private val key32 = "12345678901234567890123456789012".toByteArray()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private var recorder: MediaRecorder? = null
    private var monitoringJob: Job? = null
    private lateinit var sttUploader: SttUploader
    private val sttBuffer = SttBuffer()
    private lateinit var mfccUploader: BestMfccManager
    private var currentCallSessionId: String? = null
    private var currentRecordingFile: File? = null
    private var lastKnownCallLogId: Long? = null

    // 알림 유형별 개별 쿨다운 관리
    private var lastDVNotifyAt = 0L
    private var lastKobertNotifyAt = 0L
    private val cooldownMs = 25_000L

    companion object {
        var instance: MyInCallService? = null
            private set
        var currentCall: Call? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sttUploader = SttUploader(this, serverUrl, key32, sttBuffer, Gson())
        sttUploader.start()
        mfccUploader = BestMfccManager(ensureEndsWithPath(bestMfccBaseUrl, "real_time"), AesCbcCrypto(key32))
    }

    override fun onDestroy() {
        instance = null
        stopMonitoring()
        stopRecordingSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        
        // 통화 전 시점의 최신 CallLog ID 기록
        markCallLogBaseline()
        
        call.registerCallback(callCallback)
        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            showIncomingCallUI(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) currentCall = null
        started = false
        stopMonitoring()
        stopRecordingSafely()
    }

    fun endCall() { currentCall?.disconnect() }

    fun sendDtmfTone(tone: Char) {
        val call = currentCall ?: return
        call.playDtmfTone(tone)
        mainHandler.postDelayed({ call.stopDtmfTone() }, 150L)
    }

    private val callCallback = object : Call.Callback() {
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> {
                    if (started) return
                    started = true
                    currentCallSessionId = UUID.randomUUID().toString()
                    CallEventBus.notifyCallStarted()
                    startRecording()
                    startMonitoring()
                }
                Call.STATE_DISCONNECTED -> {
                    stopMonitoring()
                    stopRecordingSafely()
                    
                    val callEndTime = System.currentTimeMillis()
                    val fileToUpload = currentRecordingFile
                    val sessionId = currentCallSessionId
                    
                    if (fileToUpload != null && sessionId != null) {
                        // 별도 스코프에서 CallLog ID 매칭 시도 후 업로드
                        serviceScope.launch {
                            Log.d("STT_TRIGGER", "Resolving CallLog ID...")
                            val callLogId = resolveCurrentCallLogId(callEndTime)
                            
                            val finalKey = if (callLogId != null) {
                                Log.d("STT_TRIGGER", "Matched CallLog ID: $callLogId")
                                callLogId.toString()
                            } else {
                                Log.w("STT_TRIGGER", "Failed to match CallLog ID. Using Session ID as fallback.")
                                sessionId
                            }
                            
                            sttUploader.enqueueUploadFile(finalKey, fileToUpload) { success ->
                                Log.d("STT_TRIGGER", "STT upload finished. success=$success, key=$finalKey")
                            }
                        }
                    }
                    
                    CallEventBus.notifyCallEnded()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startMonitoring() {
        stopMonitoring()
        val dvThreshold = 0.85

        monitoringJob = serviceScope.launch {
            val callId = currentCallSessionId ?: return@launch
            val sampleRate = 16000
            val maxSamples = sampleRate * 5
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_DOWNLINK, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxSamples * 2)
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return@launch
            
            val audioShort = ShortArray(maxSamples)
            var currentPos = 0
            audioRecord.startRecording()

            while (isActive && started) {
                val read = audioRecord.read(audioShort, currentPos, audioShort.size - currentPos)
                if (read > 0) currentPos += read

                if (currentPos >= maxSamples) {
                    val chunkCopy = audioShort.clone()
                    currentPos = 0
                    
                    mfccUploader.uploadPcmShortChunk(callId, chunkCopy, onResult = { res ->
                        val now = System.currentTimeMillis()
                        
                        // 1. 딥보이스 알림 체크
                        if (res.deepvoiceScore >= dvThreshold && (now - lastDVNotifyAt >= cooldownMs)) {
                            lastDVNotifyAt = now
                            NotificationHelper.showAlert(
                                applicationContext, 
                                "보이스피싱 위험 감지", 
                                "[주의] 딥보이스가 의심됩니다.", 
                                NotificationHelper.ID_DEEPVOICE
                            )
                        }

                        // 2. 문맥 분석 알림 체크 (CRITICAL/WARNING)
                        if ((res.koberStatus == "CRITICAL" || res.koberStatus == "WARNING") && (now - lastKobertNotifyAt >= cooldownMs)) {
                            lastKobertNotifyAt = now
                            val msg = if (res.koberStatus == "CRITICAL") "[경고] 보이스피싱 의심 문맥 감지!" else "[주의] 보이스피싱 의심 문맥 주의!"
                            NotificationHelper.showAlert(
                                applicationContext, 
                                "보이스피싱 위험 감지", 
                                msg, 
                                NotificationHelper.ID_KOBERT
                            )
                        }
                    })
                }
            }
            audioRecord.release()
        }
    }

    private fun stopMonitoring() { monitoringJob?.cancel(); monitoringJob = null }

    private fun startRecording() {
        try {
            val file = File(getExternalFilesDir(null), "call_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) { 
            Log.e("REC", "Fail", e) 
            currentRecordingFile = null
        }
    }

    private fun stopRecordingSafely() {
        recorder?.run { try { stop(); release() } catch(e:Exception){} }
        recorder = null
    }

    private fun showIncomingCallUI(call: Call) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("phone_number", call.details.handle?.schemeSpecificPart)
        }
        startActivity(intent)
    }

    private fun ensureEndsWithPath(base: String, leaf: String): String {
        val b = base.trimEnd('/')
        return if (b.endsWith("/$leaf")) b else "$b/$leaf"
    }

    // --- CallLog ID 매칭 지원 메서드 ---

    @SuppressLint("MissingPermission")
    private fun markCallLogBaseline() {
        lastKnownCallLogId = getLatestCallLogId()
    }

    @SuppressLint("MissingPermission")
    private fun getLatestCallLogId(): Long? {
        val projection = arrayOf(CallLog.Calls._ID)
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"
        return try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, sortOrder)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (e: Exception) { null }
    }

    private suspend fun resolveCurrentCallLogId(callEndTimeMs: Long): Long? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 8000 // 최대 8초 대기
        val baseline = lastKnownCallLogId
        
        while (System.currentTimeMillis() < deadline) {
            val latest = getLatestCallLogId()
            if (latest != null && latest != baseline) return@withContext latest
            delay(500) // 0.5초 간격 폴링
        }
        null
    }
}
