package com.final_pj.voice

import android.content.Context
import android.util.Log
import org.pytorch.LiteModuleLoader
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// whisper 모델
// 수정하기 => 문자열변환이 안되고 있음
// mel 로 변환해야하는데 뭔가 복잡쓰
class WhisperTFLite(context: Context, modelPath: String) {

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context, modelPath))
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 음성 float 배열 입력, 문자열로 변환
    fun transcribe(audioFloat: FloatArray): String {
        // Whisper 모델 입력 크기에 맞게 reshape 필요
        // val input = arrayOf(audioFloat) // 1차원

        val numFeatures = 80
        val seqLen = 3000
        val batchSize = 1

        val input = Array(batchSize) { Array(numFeatures) { FloatArray(seqLen) } }
        val output = Array(1) { IntArray(224) }

        interpreter.run(input, output)

        // 토큰 -> 문자열 변환 (단순 예시)
        return output[0].joinToString(" ") { tokenId -> tokenId.toString() }
    }
}
