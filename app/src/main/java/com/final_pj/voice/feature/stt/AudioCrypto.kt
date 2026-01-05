package com.final_pj.voice.feature.stt

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedChunk(val iv: String, val cipherBytes: ByteArray)

interface AudioCrypto {
    fun encrypt(pcmBytes: ByteArray): EncryptedChunk
}

class AesCbcCrypto(private val key32: ByteArray) : AudioCrypto {
    init {
        require(key32.size == 32) { "AES-256 key must be 32 bytes. current=${key32.size}" }
    }

    override fun encrypt(pcmBytes: ByteArray): EncryptedChunk {
        val ivBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key32, "AES"),
            IvParameterSpec(ivBytes)
        )

        val cipherBytes = cipher.doFinal(pcmBytes)
        val ivB64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)

        return EncryptedChunk(iv = ivB64, cipherBytes = cipherBytes)
    }
}
