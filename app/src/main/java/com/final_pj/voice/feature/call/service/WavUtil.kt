package com.final_pj.voice.feature.call.service

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtil {
    fun pcm16leToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int = 1) {
        val pcmData = pcmFile.readBytes()
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        FileOutputStream(wavFile).use { fos ->
            fos.write("RIFF".toByteArray(Charsets.US_ASCII))
            fos.write(intLE(totalSize))
            fos.write("WAVE".toByteArray(Charsets.US_ASCII))

            fos.write("fmt ".toByteArray(Charsets.US_ASCII))
            fos.write(intLE(16))                 // PCM fmt chunk size
            fos.write(shortLE(1))                // audio format PCM=1
            fos.write(shortLE(channels.toShort()))
            fos.write(intLE(sampleRate))
            fos.write(intLE(byteRate))
            fos.write(shortLE((channels * 2).toShort())) // block align
            fos.write(shortLE(16))               // bits per sample

            fos.write("data".toByteArray(Charsets.US_ASCII))
            fos.write(intLE(dataSize))
            fos.write(pcmData)
        }
    }

    private fun intLE(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortLE(v: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
}
