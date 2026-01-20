package com.final_pj.voice.feature.stt

import android.content.Context
import android.util.Base64
import android.util.Log
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.stt.model.SttResponse
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SttUploader(
    private val context: Context,
    private val serverUrl: String,
    private val key32: ByteArray,
    private val buffer: SttBuffer,
    private val gson: Gson = Gson()
) {

    private data class UploadTask(
        val callId: String,
        val file: File,
        val onFinished: (success: Boolean) -> Unit
    )

    private val queue = ArrayBlockingQueue<UploadTask>(3)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var running = false
    private var worker: Thread? = null
    @Volatile private var currentCall: Call? = null

    fun start() {
        if (running) return
        running = true
        worker = Thread {
            while (running) {
                var task: UploadTask? = null
                try {
                    task = queue.take()
                    uploadOnce(task)
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    Log.e("STT", "worker err: ${e.message}", e)
                    task?.onFinished(false)
                } finally {
                    currentCall = null
                }
            }
        }.apply { start() }
    }

    private fun uploadOnce(task: UploadTask) {
        val callId = task.callId
        val m4aFile = task.file

        try {
            if (!m4aFile.exists()) {
                task.onFinished(false)
                return
            }

            val m4aBytes = m4aFile.readBytes()
            val (iv, encrypted) = encryptAES(m4aBytes, key32)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("call_id", callId)
                .addFormDataPart("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .addFormDataPart(
                    "audio",
                    "${m4aFile.name}.enc",
                    encrypted.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val req = Request.Builder().url(serverUrl).post(body).build()
            val call = client.newCall(req)
            currentCall = call

            call.execute().use { resp ->
                val txt = resp.body?.string()
                Log.d("STT", "Uploaded callId=$callId HTTP ${resp.code} / $txt")

                if (!resp.isSuccessful || txt.isNullOrBlank()) {
                    task.onFinished(false)
                    return
                }

                val parsed = runCatching { gson.fromJson(txt, SttResponse::class.java) }.getOrNull()
                if (parsed == null) {
                    task.onFinished(false)
                    return
                }

                buffer.put(callId, parsed)

                val app = context.applicationContext as App
                CoroutineScope(Dispatchers.IO).launch {
                    val llm = parsed.llm
                    val vp = parsed.voicephishing
                    val sign = parsed.phishingSign

                    val id = app.db.sttSummaryDao().insert(
                        SttResultEntity(
                            callId = callId,
                            text = parsed.text,
                            isVoicephishing = llm?.isVoicephishing ?: vp?.flag,
                            deepvoiceScore = vp?.score,      // voicephishing.score에서 추출
                            koberScore = sign?.riskScore,    // phising_sign.risk_score에서 추출
                            voicephishingScore = llm?.voicephishingScore,
                            category = llm?.category,
                            summary = llm?.summary,
                            keywords = llm?.keywords,
                            conversation = llm?.community
                        )
                    )
                    Log.d("STT", "DB 저장 완료 id=$id callId=$callId")
                }
                task.onFinished(true)
            }
        } catch (e: Exception) {
            Log.e("STT", "uploadOnce exception: ${e.message}", e)
            task.onFinished(false)
        }
    }

    private fun encryptAES(data: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Pair(iv, cipher.doFinal(data))
    }

    fun enqueueUploadFile(callId: String, file: File, onFinished: (success: Boolean) -> Unit) {
        if (!file.exists()) {
            onFinished(false)
            return
        }
        if (!queue.offer(UploadTask(callId, file, onFinished))) {
            queue.poll()?.onFinished(false)
            queue.offer(UploadTask(callId, file, onFinished))
        }
    }
}
