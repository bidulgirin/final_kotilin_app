package com.final_pj.voice.feature.call.service

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeakerSplitRecorder(
    private val scope: CoroutineScope,
    private val sampleRate: Int = 16000,
    private val chunkSeconds: Int = 5,
    private val onDownlinkChunk: (shorts: ShortArray, floats: FloatArray) -> Unit
) {
    private var uplinkRecord: AudioRecord? = null
    private var downlinkRecord: AudioRecord? = null

    private var uplinkJob: Job? = null
    private var downlinkJob: Job? = null

    private var uplinkPcmFile: File? = null
    private var downlinkPcmFile: File? = null

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(uplinkOutPcm: File, downlinkOutPcm: File) {
        stop()

        uplinkPcmFile = uplinkOutPcm
        downlinkPcmFile = downlinkOutPcm

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
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

        if (uplinkRecord?.state != AudioRecord.STATE_INITIALIZED ||
            downlinkRecord?.state != AudioRecord.STATE_INITIALIZED
        ) {
            Log.e("SPLIT_REC", "AudioRecord init failed uplink=${uplinkRecord?.state} downlink=${downlinkRecord?.state}")
            safeRelease()
            return
        }

        uplinkJob = scope.launch(Dispatchers.IO) {
            recordLoop(
                tag = "UPLINK",
                record = uplinkRecord!!,
                outFile = uplinkOutPcm,
                emitChunks = false
            )
        }

        downlinkJob = scope.launch(Dispatchers.IO) {
            recordLoop(
                tag = "DOWNLINK",
                record = downlinkRecord!!,
                outFile = downlinkOutPcm,
                emitChunks = true
            )
        }
    }

    fun stop() {
        uplinkJob?.cancel()
        downlinkJob?.cancel()
        uplinkJob = null
        downlinkJob = null

        try { uplinkRecord?.stop() } catch (_: Exception) {}
        try { downlinkRecord?.stop() } catch (_: Exception) {}

        safeRelease()
    }

    fun getUplinkPcmFile(): File? = uplinkPcmFile
    fun getDownlinkPcmFile(): File? = downlinkPcmFile

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
            val bos = BufferedOutputStream(fos)

            try {
                record.startRecording()

                while (isActive) {
                    val n = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (n <= 0) continue

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
                Log.e("SPLIT_REC", "recordLoop($tag) error: ${e.message}", e)
            } finally {
                try { bos.flush() } catch (_: Exception) {}
            }
        }
    }

    private fun writeShortsLE(out: OutputStream, buf: ShortArray, len: Int) {
        val bb = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until len) bb.putShort(buf[i])
        out.write(bb.array())
    }
}
