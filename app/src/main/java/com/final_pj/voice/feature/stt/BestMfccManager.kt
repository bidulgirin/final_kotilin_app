package com.final_pj.voice.feature.stt

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BestMfccManager(
    private val endpointUrl: String,
    private val crypto: AudioCrypto,
    private val okHttp: OkHttpClient = OkHttpClient(),
) {
    data class UploadResult(
        val callId: String,
        val deepvoiceScore: Double,
        val koberStatus: String,
        val koberRiskScore: Double, // 추가: 위험도 점수
        val category: String?,      // 추가: 탐지된 카테고리 (details[0].result)
        val detectedKeyword: String?,
        val shouldAlert: Boolean,
        val rawBody: String
    )

    fun uploadPcmShortChunk(
        callId: String,
        chunk: ShortArray,
        onResult: ((UploadResult) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        val pcmBytes = shortsToLittleEndianBytes(chunk)
        val enc = crypto.encrypt(pcmBytes)
        sendChunk(callId, enc.iv, enc.cipherBytes, onResult, onError)
    }

    private fun shortsToLittleEndianBytes(samples: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        return bb.array()
    }

    private fun sendChunk(
        callId: String,
        iv: String,
        cipherBytes: ByteArray,
        onResult: ((UploadResult) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
    ) {
        val audioBody = cipherBytes.toRequestBody("application/octet-stream".toMediaType())
        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("call_id", callId)
            .addFormDataPart("iv", iv)
            .addFormDataPart("audio", "chunk.pcm", audioBody)
            .build()

        val req = Request.Builder().url(endpointUrl).post(formBody).build()

        okHttp.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onError?.invoke(RuntimeException("Server Error: ${it.code}"))
                        return
                    }

                    try {
                        val json = JSONObject(bodyStr)
                        
                        val dvScore = json.optDouble("deepvoiceScore", json.optDouble("deepvoice_score", 0.0))
                        
                        var kStatus = "NORMAL"
                        var kKeyword: String? = null
                        var kRiskScore = 0.0
                        var kCategory: String? = null
                        
                        val koberObj = json.optJSONObject("koberScore")
                        if (koberObj != null) {
                            kStatus = koberObj.optString("status", "NORMAL").uppercase()
                            kRiskScore = koberObj.optDouble("risk_score", 0.0)
                            
                            val detailsArr = koberObj.optJSONArray("details")
                            if (detailsArr != null && detailsArr.length() > 0) {
                                kCategory = detailsArr.optJSONObject(0)?.optString("result")
                            }

                            val faissHits = koberObj.optJSONArray("faiss_hits")
                            if (faissHits != null && faissHits.length() > 0) {
                                kKeyword = faissHits.optJSONObject(0)?.optString("keyword")
                            }
                        }

                        val should = json.optBoolean("should_alert", false)

                        Log.d("VP_DEBUG", "Parsed -> DV: $dvScore, Kober: $kStatus, Risk: $kRiskScore, Cat: $kCategory")

                        onResult?.invoke(UploadResult(
                            callId = json.optString("call_id", callId),
                            deepvoiceScore = dvScore,
                            koberStatus = kStatus,
                            koberRiskScore = kRiskScore,
                            category = kCategory,
                            detectedKeyword = kKeyword,
                            shouldAlert = should,
                            rawBody = bodyStr
                        ))
                    } catch (e: Exception) {
                        Log.e("VP_DEBUG", "Parse Error", e)
                        onError?.invoke(e)
                    }
                }
            }
        })
    }
}
