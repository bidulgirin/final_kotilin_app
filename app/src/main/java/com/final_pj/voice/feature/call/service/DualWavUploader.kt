package com.final_pj.voice.feature.call.service

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DualWavUploader(
    private val endpointUrl: String,
    private val key32: ByteArray
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun uploadDualWav(
        callId: String,
        mfccCallId: String?,
        uplinkWav: File,
        downlinkWav: File,
        llm: Boolean,
        sttMode: String,
        analysisTarget: String,
        returnMode: String,
        onDone: (Boolean) -> Unit
    ) {
        try {
            val upPlain = uplinkWav.readBytes()
            val dnPlain = downlinkWav.readBytes()

            val (ivUpB64, upEnc) = encryptAesCbcBase64Iv(upPlain, key32)
            val (ivDnB64, dnEnc) = encryptAesCbcBase64Iv(dnPlain, key32)

            val binType = "application/octet-stream".toMediaType()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("call_id", callId)
                .addFormDataPart("llm", llm.toString())
                .addFormDataPart("stt_mode", sttMode)
                .addFormDataPart("analysis_target", analysisTarget)
                .addFormDataPart("return_mode", returnMode)
                .addFormDataPart("iv_uplink", ivUpB64)
                .addFormDataPart("iv_downlink", ivDnB64)
                .apply {
                    if (!mfccCallId.isNullOrBlank()) {
                        addFormDataPart("mfcc_call_id", mfccCallId)
                    }
                }
                .addFormDataPart(
                    "audio_uplink",
                    "uplink_enc.bin",
                    upEnc.toRequestBody(binType)
                )
                .addFormDataPart(
                    "audio_downlink",
                    "downlink_enc.bin",
                    dnEnc.toRequestBody(binType)
                )
                .build()

            val req = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e("DUAL_WAV", "upload failed: ${e.message}", e)
                    onDone(false)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use {
                        val ok = it.isSuccessful
                        if (!ok) Log.e("DUAL_WAV", "upload not ok code=${it.code}")
                        onDone(ok)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("DUAL_WAV", "upload exception: ${e.message}", e)
            onDone(false)
        }
    }

    private fun encryptAesCbcBase64Iv(plain: ByteArray, key: ByteArray): Pair<String, ByteArray> {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))

        val encrypted = cipher.doFinal(plain)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return ivB64 to encrypted
    }
}
