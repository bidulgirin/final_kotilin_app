package com.final_pj.voice.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object encryptAudioBuffer {

    // 일단 테스트 서버에서 진행할거니까 aes 방식으로
    fun encryptAudioBuffer(buffer: FloatArray, key: String): String {
        val byteBuffer = ByteArray(buffer.size * 4)
        var idx = 0
        buffer.forEach {
            val bits = java.lang.Float.floatToIntBits(it)
            byteBuffer[idx++] = (bits shr 24).toByte()
            byteBuffer[idx++] = (bits shr 16).toByte()
            byteBuffer[idx++] = (bits shr 8).toByte()
            byteBuffer[idx++] = bits.toByte()
        }

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(byteBuffer)

        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }


    fun sendAudioToServer(encryptedAudio: String, serverIp: String) {
        val client = OkHttpClient()
        val json = "{\"audio\":\"$encryptedAudio\"}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("http://192.168.3.10:8000/upload-audio")
            .post(body)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                println(response.body?.string())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    //서버용 키와 인증서 생성
    //openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout server.key -out server.crt
    // uvicorn main:app --host 0.0.0.0 --port 8443 --ssl-keyfile server.key --ssl-certfile server.crt

    // OkHttp: TLS, 자체 서명 인증서 무시 (테스트 전용)
    val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
        object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    )
//    val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
//    sslContext.init(null, trustAllCerts, java.security.SecureRandom()) // 공인 인증서 이용
//    val sslSocketFactory = sslContext.socketFactory
//
//    val client = OkHttpClient.Builder()
//        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
//        .hostnameVerifier { _, _ -> true }
//        .build()


}