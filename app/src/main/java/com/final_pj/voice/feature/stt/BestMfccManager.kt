package com.final_pj.voice.feature.stt

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BestMfccManager(
    private val endpointUrl: String,   // 최종 endpoint 전체 URL
    private val crypto: AudioCrypto,
    private val okHttp: OkHttpClient = OkHttpClient(),
) {

    fun uploadPcmShortChunk(callId: String, chunk: ShortArray) {
        val pcmBytes = shortsToLittleEndianBytes(chunk)
        val enc = crypto.encrypt(pcmBytes)
        sendChunk(callId, enc.iv, enc.cipherBytes)
    }

    private fun shortsToLittleEndianBytes(samples: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        return bb.array()
    }

    private fun sendChunk(callId: String, iv: String, cipherBytes: ByteArray) {
        val audioBody = cipherBytes.toRequestBody("application/octet-stream".toMediaType())
        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("call_id", callId)
            .addFormDataPart("iv", iv)
            .addFormDataPart("audio", "chunk.pcm", audioBody)
            .build()

        val req = Request.Builder()
            .url(endpointUrl)
            .post(formBody)
            .build()

        okHttp.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("MFCC_UP", "send failed: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string()
                    if (!it.isSuccessful) {
                        Log.e("MFCC_UP", "send error: ${it.code} $bodyStr")
                    } else {
                        Log.d("MFCC_UP", "send ok: $bodyStr")
                    }
                }
            }
        })
    }
}
