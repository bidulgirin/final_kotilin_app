package com.final_pj.voice.feature.call.service

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeakerSplitRecorder(
    private val scope: CoroutineScope,
    private val sampleRate: Int = 16000,
    private val chunkSeconds: Int = 5,
    private val onDownlinkChunk: (shorts: ShortArray, floats: FloatArray) -> Unit
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var uplinkRecord: AudioRecord? = null
    private var downlinkRecord: AudioRecord? = null

    private var uplinkJob: Job? = null
    private var downlinkJob: Job? = null

    private var uplinkPcmFile: File? = null
    private var downlinkPcmFile: File? = null

    @Volatile private var running: Boolean = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(uplinkOutPcm: File, downlinkOutPcm: File) {
        stop()

        uplinkPcmFile = uplinkOutPcm
        downlinkPcmFile = downlinkOutPcm

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        Log.d("minBuf", "${minBuf}")
        if (minBuf <= 0) {
            Log.e("SPLIT_REC", "Invalid minBufferSize=$minBuf")
            return
        }
        val bufSize = minBuf * 2

        uplinkRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_UPLINK,
            sampleRate,
            channelConfig,
            audioFormat,
            bufSize
        )

        downlinkRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            sampleRate,
            channelConfig,
            audioFormat,
            bufSize
        )

        val up = uplinkRecord
        val dn = downlinkRecord
        if (up?.state != AudioRecord.STATE_INITIALIZED || dn?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("SPLIT_REC", "AudioRecord init failed uplink=${up?.state} downlink=${dn?.state}")
            safeRelease()
            return
        }

        running = true

        uplinkJob = scope.launch(Dispatchers.IO) {
            recordLoop(
                tag = "UPLINK",
                record = up,
                outFile = uplinkOutPcm,
                emitChunks = false
            )
        }

        downlinkJob = scope.launch(Dispatchers.IO) {
            recordLoop(
                tag = "DOWNLINK",
                record = dn,
                outFile = downlinkOutPcm,
                emitChunks = true
            )
        }
    }

    fun stop() {
        running = false

        // 2) read()를 깨우기 위해 먼저 stop 시도
        tryStop(uplinkRecord, "uplink")
        tryStop(downlinkRecord, "downlink")

        // 3) 루프가 완전히 끝날 때까지 기다린 뒤 release 해야 안전함
        runBlocking {
            try { uplinkJob?.cancelAndJoin() } catch (_: Exception) {}
            try { downlinkJob?.cancelAndJoin() } catch (_: Exception) {}
        }

        uplinkJob = null
        downlinkJob = null

        // 4) 이제 release (read가 끝난 다음)
        safeRelease()
    }

    fun getUplinkPcmFile(): File? = uplinkPcmFile
    fun getDownlinkPcmFile(): File? = downlinkPcmFile

    private fun tryStop(record: AudioRecord?, name: String) {
        try {
            record?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED &&
                    it.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.e("SPLIT_REC", "$name stop failed: ${e.message}", e)
        }
    }

    private fun safeRelease() {
        try { uplinkRecord?.release() } catch (_: Exception) {}
        try { downlinkRecord?.release() } catch (_: Exception) {}
        uplinkRecord = null
        downlinkRecord = null
    }

    private suspend fun recordLoop(
        tag: String,
        record: AudioRecord,
        outFile: File,
        emitChunks: Boolean
    ) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val pcmBuffer = ShortArray(minBuf.coerceAtLeast(1024))

        val chunkMaxSamples = sampleRate * chunkSeconds
        val chunkShorts = ShortArray(chunkMaxSamples)
        val chunkFloats = FloatArray(chunkMaxSamples)
        var chunkPos = 0

        FileOutputStream(outFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                try {
                    record.startRecording()

                    while (running && currentCoroutineContext().isActive) {
                        val n = record.read(pcmBuffer, 0, pcmBuffer.size)

                        // read()가 0/에러를 주는 구간에서는 루프가 헛돌지 않게 살짝 양보
                        if (n <= 0) {
                            delay(5)
                            continue
                        }

                        writeShortsLE(bos, pcmBuffer, n)

                        if (emitChunks) {
                            var i = 0
                            while (i < n) {
                                val s = pcmBuffer[i]
                                if (chunkPos < chunkMaxSamples) {
                                    chunkShorts[chunkPos] = s
                                    chunkFloats[chunkPos] = s / 32768f
                                    chunkPos++
                                }
                                if (chunkPos >= chunkMaxSamples) {
                                    try {
                                        onDownlinkChunk(chunkShorts.clone(), chunkFloats.clone())
                                    } catch (e: Exception) {
                                        Log.e("SPLIT_REC", "onDownlinkChunk failed: ${e.message}", e)
                                    }
                                    chunkPos = 0
                                }
                                i++
                            }
                        }
                    }
                } catch (e: Exception) {
                    // stop() 시점에는 Cancellation/IllegalState가 섞여 들어올 수 있음
                    Log.e("SPLIT_REC", "recordLoop($tag) error: ${e.message}", e)
                } finally {
                    try { bos.flush() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun writeShortsLE(out: OutputStream, buf: ShortArray, len: Int) {
        val bb = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until len) bb.putShort(buf[i])
        out.write(bb.array())
    }
}
