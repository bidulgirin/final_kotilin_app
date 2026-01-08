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

/**
 * callId는 MyInCallService.onCreate()에서 만들고
 * 통화 종료 시 enqueueUploadLatestFromDir(callId, dir)로 넘겨준다.
 */
class SttUploader(
    private val context: Context,
    private val serverUrl: String,
    private val key32: ByteArray,
    private val buffer: SttBuffer,      // 외부에서 주입 (또는 내부에서 생성해도 됨)
    private val gson: Gson = Gson()     // DI 하려면 외부 주입도 가능
) {

    // queue에는 File만이 아니라 callId도 같이 담아야 함
    private data class UploadTask(
        val callId: String,
        val file: File,
        val onFinished: (success: Boolean) -> Unit
    )

    private val queue = ArrayBlockingQueue<UploadTask>(3)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
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
                    uploadOnce(task)   // task 전체를 넘김
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    Log.e("STT", "worker err: ${e.message}", e)
                    task?.onFinished(false)   // 예외 시에도 콜백
                } finally {
                    currentCall = null
                }
            }
    }.apply { start() }
    }

    fun stop() {
        running = false
        currentCall?.cancel()
        worker?.interrupt()
        worker = null
        queue.clear()
    }

    /**
     * 통화 종료 시 호출
     * callId는 onCreate에서 만든 것을 그대로 넘겨줘야 함
     */
    fun enqueueUploadLatestFromDir(
        callId: String,
        dir: File,
        onFinished: (success: Boolean) -> Unit
    ) {
        val latest = findLatestCallM4aByName(dir)
        if (latest == null) {
            Log.e("STT", "No call_*.m4a found in ${dir.absolutePath}")
            onFinished(false) // 반드시 호출
            return
        }

        val task = UploadTask(callId, latest, onFinished)

        // 큐가 가득 찼으면 가장 오래된 작업을 버리고 새 작업을 넣음
        if (!queue.offer(task)) {
            val dropped = queue.poll()
            dropped?.onFinished(false) // 버려진 작업도 실패 처리로 콜백 호출(중요)
            val ok = queue.offer(task)
            if (!ok) {
                Log.e("STT", "Queue offer failed even after dropping one task")
                onFinished(false) // 새 작업도 못 넣었으면 실패 콜백
                return
            }
        }

        Log.d("STT", "Enqueued latest m4a: callId=$callId name=${latest.name} size=${latest.length()}")
    }

    private fun findLatestCallM4aByName(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null

        val re = Regex("""call_(\d+)\.m4a$""", RegexOption.IGNORE_CASE)
        val files = dir.listFiles()?.filter { it.isFile && re.containsMatchIn(it.name) } ?: return null
        if (files.isEmpty()) return null

        fun ts(f: File): Long {
            val m = re.find(f.name) ?: return -1L
            return m.groupValues[1].toLongOrNull() ?: -1L
        }

        return files.maxByOrNull { ts(it) }
    }

    private fun waitUntilFileStable(file: File): Boolean {
        val maxTries = 20
        val intervalMs = 200L

        var prevLen = -1L
        var sameCount = 0

        repeat(maxTries) {
            val len = file.length()
            if (len > 0 && len == prevLen) {
                sameCount++
                if (sameCount >= 2) return true
            } else {
                sameCount = 0
            }
            prevLen = len
            Thread.sleep(intervalMs)
        }
        return false
    }

    /**
     * Thread 기반이므로 suspend 제거하고 동기 처리로 깔끔하게
     */
//    private fun uploadOnce(callId: String, m4aFile: File) {
//        if (!m4aFile.exists() || !m4aFile.isFile) {
//            Log.e("STT", "File not found: ${m4aFile.absolutePath}")
//            return
//        }
//
//        if (!waitUntilFileStable(m4aFile)) {
//            Log.e("STT", "File not stable: ${m4aFile.name}")
//            return
//        }
//
//        val m4aBytes = m4aFile.readBytes()
//        if (m4aBytes.isEmpty()) {
//            Log.e("STT", "Empty m4a bytes: ${m4aFile.name}")
//            return
//        }
//
//        val (iv, encrypted) = encryptAES(m4aBytes, key32)
//
//        val body = MultipartBody.Builder()
//            .setType(MultipartBody.FORM)
//            .addFormDataPart("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
//            .addFormDataPart(
//                "audio",
//                "${m4aFile.name}.enc",
//                encrypted.toRequestBody("application/octet-stream".toMediaType())
//            )
//            .build()
//
//        val req = Request.Builder()
//            .url(serverUrl)
//            .post(body)
//            .build()
//
//        val call = client.newCall(req)
//        currentCall = call
//
//        call.execute().use { resp ->
//            val txt = resp.body?.string()
//            Log.d("STT", "Uploaded=${m4aFile.name} callId=$callId HTTP ${resp.code} / $txt")
//
//            if (!resp.isSuccessful || txt.isNullOrBlank()) return@use
//
//            runCatching {
//                val parsed = gson.fromJson(txt, SttResponse::class.java)
//                val llm = parsed.llm
//                // callId로 묶어서 임시 보관 (통화 종료 때 저장하려면 여기서 put)
//                buffer.put(callId, parsed)
//
//                // DB 저장
//                val app = context.applicationContext as App
//
//                CoroutineScope(Dispatchers.IO).launch {
//                    val id = app.db.SttResultDao().insert(
//                        SttResultEntity(
//                            callId = callId,
//                            text = parsed.text,
//                            // LLM 결과 저장
//                            isVoicephishing = llm?.isVoicephishing,
//                            voicephishingScore = llm?.voicephishingScore,
//                            category = llm?.category,
//                            summary = llm?.summary
//                        )
//                    )
//                    Log.d("STT", "DB 저장 완료 id=$id callId=$callId")
//
//                }
//
//            }.onFailure {
//                Log.e("STT", "Failed to parse response: $txt", it)
//            }
//
//            stop()
//        }
//    }

    private fun uploadOnce(task: UploadTask) {
        val callId = task.callId
        val m4aFile = task.file

        try {
            if (!m4aFile.exists() || !m4aFile.isFile) {
                Log.e("STT", "File not found: ${m4aFile.absolutePath}")
                task.onFinished(false)
                return
            }

            if (!waitUntilFileStable(m4aFile)) {
                Log.e("STT", "File not stable: ${m4aFile.name}")
                task.onFinished(false)
                return
            }

            val m4aBytes = m4aFile.readBytes()
            if (m4aBytes.isEmpty()) {
                Log.e("STT", "Empty m4a bytes: ${m4aFile.name}")
                task.onFinished(false)
                return
            }

            val (iv, encrypted) = encryptAES(m4aBytes, key32)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .addFormDataPart(
                    "audio",
                    "${m4aFile.name}.enc",
                    encrypted.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url(serverUrl)
                .post(body)
                .build()

            val call = client.newCall(req)
            currentCall = call

            call.execute().use { resp ->
                val txt = resp.body?.string()
                Log.d("STT", "Uploaded=${m4aFile.name} callId=$callId HTTP ${resp.code} / $txt")

                if (!resp.isSuccessful || txt.isNullOrBlank()) {
                    task.onFinished(false)
                    return
                }

                val parsed = runCatching { gson.fromJson(txt, SttResponse::class.java) }
                    .getOrElse {
                        Log.e("STT", "Failed to parse response: $txt", it)
                        task.onFinished(false)
                        return
                    }

                buffer.put(callId, parsed)

                val app = context.applicationContext as App
                CoroutineScope(Dispatchers.IO).launch {
                    val llm = parsed.llm
                    val id = app.db.SttResultDao().insert(
                        SttResultEntity(
                            callId = callId,
                            text = parsed.text,
                            isVoicephishing = llm?.isVoicephishing,
                            voicephishingScore = llm?.voicephishingScore,
                            category = llm?.category,
                            summary = llm?.summary
                        )
                    )
                    Log.d("STT", "DB 저장 완료 id=$id callId=$callId")
                }

                task.onFinished(true)
            }
        } catch (e: Exception) {
            Log.e("STT", "uploadOnce exception: ${e.message}", e)
            task.onFinished(false)
        } finally {
            currentCall = null
            // 여기서 stop() 호출하면 다음 작업이 영원히 안 돔 -> 제거 권장
            // stop()
        }
    }



    private fun encryptAES(data: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        return Pair(iv, cipher.doFinal(data))
    }

    fun enqueueUploadFile(callId: String, file: File, onFinished: (success: Boolean) -> Unit) {
        Log.e("enqueueUploadFile","enqueueUploadFile 들어옴")
        if (!file.exists()) {
            Log.e("STT", "file not exists: ${file.absolutePath}")
            onFinished(false)
            return
        }

        val task = UploadTask(callId, file, onFinished)
        Log.e("task","${task}")

        if (!queue.offer(task)) {
            val dropped = queue.poll()
            Log.e("dropped","${dropped}")
            dropped?.onFinished(false)
            val ok = queue.offer(task)
            if (!ok) {
                onFinished(false)
                return
            }
        }

        Log.d("STT", "Enqueued file: callId=$callId name=${file.name} size=${file.length()}")
    }

}
