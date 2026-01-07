package com.final_pj.voice.feature.call.service

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SpeakerUploader(
    private val endpointUrl: String
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun uploadTwoSpeakers(
        callId: String,
        uplinkWav: File,
        downlinkWav: File,
        onDone: (Boolean) -> Unit
    ) {
        try {
            val wavType = "audio/wav".toMediaType()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("call_id", callId)
                .addFormDataPart("speaker_uplink", uplinkWav.name, uplinkWav.asRequestBody(wavType))
                .addFormDataPart("speaker_downlink", downlinkWav.name, downlinkWav.asRequestBody(wavType))
                .build()

            val req = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e("SPK_UP", "upload failed: ${e.message}", e)
                    onDone(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val ok = it.isSuccessful
                        if (!ok) Log.e("SPK_UP", "upload not ok code=${it.code}")
                        onDone(ok)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("SPK_UP", "upload exception: ${e.message}", e)
            onDone(false)
        }
    }
}
