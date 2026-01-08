package com.final_pj.voice.feature.stt

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 서버 응답(JSON) 예시:
 * {
 *   "call_id": "1",
 *   "phishing_score": 0.92,
 *   "should_alert": true
 * }
 */
class BestMfccManager(
    private val endpointUrl: String,   // 최종 endpoint 전체 URL
    private val crypto: AudioCrypto,
    private val okHttp: OkHttpClient = OkHttpClient(),
) {

    data class UploadResult(
        val callId: String,
        val phishingScore: Double,
        val shouldAlert: Boolean,
        val rawBody: String
    )

    /**
     * Android가 할 일:
     * - 5초 오디오 버퍼만 업로드
     * - 서버 결과(should_alert / score)만 받아서 알림 판단
     */
    fun uploadPcmShortChunk(
        callId: String,
        chunk: ShortArray,
        onResult: ((UploadResult) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        val pcmBytes = shortsToLittleEndianBytes(chunk)
        val enc = crypto.encrypt(pcmBytes)   // enc.iv: String, enc.cipherBytes: ByteArray 가정
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

        val req = Request.Builder()
            .url(endpointUrl)
            .post(formBody)
            .build()

        okHttp.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MFCC_UP", "send failed: ${e.message}", e)
                onError?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        val err = RuntimeException("send error: ${it.code} $bodyStr")
                        Log.e("MFCC_UP", err.message ?: "send error")
                        onError?.invoke(err)
                        return
                    }

                    try {
                        val json = JSONObject(bodyStr)

                        val returnedCallId = json.optString("call_id", callId)
                        val score = json.optDouble("phishing_score", 0.0)
                        val shouldAlert = json.optBoolean("should_alert", false)

                        // ---- stt ----
                        val sttObj = json.optJSONObject("stt")
                        val sttText = sttObj?.optString("text", "") ?: ""
                        val bufferedN = sttObj?.optInt("buffered_n", 0) ?: 0

                        // ---- audio ----
                        val audioObj = json.optJSONObject("audio")
                        val audioFused = audioObj?.optDouble("phishing_score", 0.0) ?: 0.0
                        val mfccScore = audioObj?.optDouble("mfcc_score", 0.0) ?: 0.0
                        val melScore = audioObj?.optDouble("mel_score", 0.0) ?: 0.0

                        // ---- text (AE+KoBERT 결과) ----
                        val textObj = json.optJSONObject("text")
                        val textStatus = textObj?.optString("status", "null")
                        val textLoss = textObj?.optDouble("loss", 0.0)
                        val textRisk = textObj?.optDouble("risk_score", 0.0)

                        // details는 배열일 수도 있고 null일 수도 있음
                        val detailsArr = textObj?.optJSONArray("details")

                        // ---- raw dumps (mfcc/mel) ----
                        val mfccRaw = json.optJSONObject("mfcc")?.optJSONObject("raw")
                        val melRaw = json.optJSONObject("mel")?.optJSONObject("raw")

                        val result = UploadResult(
                            callId = returnedCallId,
                            phishingScore = score,
                            shouldAlert = shouldAlert,
                            rawBody = bodyStr
                        )

                        // 보기 좋은 로그 출력
                        val sb = StringBuilder()
                        sb.appendLine("===== VP RESULT =====")
                        sb.appendLine("call_id: $returnedCallId")
                        sb.appendLine("final phishing_score: ${"%.6f".format(score)}")
                        sb.appendLine("should_alert: $shouldAlert")
                        sb.appendLine("")
                        sb.appendLine("[STT]")
                        sb.appendLine("buffered_n: $bufferedN")
                        sb.appendLine("text: ${if (sttText.length > 200) sttText.take(200) + "…" else sttText}")
                        sb.appendLine("")
                        sb.appendLine("[AUDIO]")
                        sb.appendLine("audio_fused: ${"%.6f".format(audioFused)}")
                        sb.appendLine("mfcc_score:  ${"%.6f".format(mfccScore)}")
                        sb.appendLine("mel_score:   ${"%.6f".format(melScore)}")
                        sb.appendLine("")
                        sb.appendLine("[TEXT(AE+KoBERT)]")
                        sb.appendLine("status: $textStatus")
                        sb.appendLine("loss: ${textLoss ?: "null"}")
                        sb.appendLine("risk_score: ${textRisk ?: "null"}")

                        if (detailsArr != null) {
                            sb.appendLine("details_count: ${detailsArr.length()}")
                            for (i in 0 until detailsArr.length()) {
                                val d = detailsArr.optJSONObject(i) ?: continue
                                val r = d.optString("result", "")
                                val p = d.optDouble("prob", 0.0)
                                val m = d.optString("msg", "")
                                sb.appendLine(" - [$i] $r / prob=$p / msg=$m")
                            }
                        } else {
                            sb.appendLine("details: null")
                        }

                        sb.appendLine("")
                        sb.appendLine("[RAW]")
                        sb.appendLine("mfcc.raw: ${mfccRaw?.toString() ?: "null"}")
                        sb.appendLine("mel.raw:  ${melRaw?.toString() ?: "null"}")
                        sb.appendLine("=====================")

                        Log.d("MFCC_UP", sb.toString())

                        // 필요하면 onResult로 result 넘기기
                        onResult?.invoke(result)

                    } catch (e: Exception) {
                        Log.e("MFCC_UP", "JSON parse failed: ${e.message}. body=$bodyStr", e)
                        onError?.invoke(e)
                    }
                }
            }
        })
    }
}
