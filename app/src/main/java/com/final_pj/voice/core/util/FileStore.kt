package com.final_pj.voice.core.util

import android.content.Context
import java.io.File

object FileStore {
    /**
     * 서버 응답(txt)을 내부저장소 filesDir/ 아래 파일로 저장
     * @return 저장된 파일 절대 경로 (Room에 저장)
     */
    fun saveResponseText(context: Context, callSessionId: String, text: String): String {
        val dir = File(context.filesDir, "stt")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "$callSessionId.response.json")
        file.writeText(text, Charsets.UTF_8)
        return file.absolutePath
    }

    /**
     * 암호화된 오디오 자체도 남기고 싶다면(선택)
     */
    fun saveEncryptedAudio(context: Context, callSessionId: String, encrypted: ByteArray): String {
        val dir = File(context.filesDir, "stt")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "$callSessionId.audio.enc")
        file.writeBytes(encrypted)
        return file.absolutePath
    }
}
