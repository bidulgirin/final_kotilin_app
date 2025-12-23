package com.final_pj.voice.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.final_pj.voice.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

//m4a → wav 변환 함수
fun convertM4aToWav(
    inputM4a: File,
    outputWav: File,
    onComplete: (Boolean) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputM4a.absolutePath)

            // 오디오 트랙 찾기
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                onComplete(false)
                return@launch
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOutput = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()

            var isEOS = false

            while (true) {
                if (!isEOS) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)

                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    outputBuffer.clear()

                    pcmOutput.write(chunk)
                    codec.releaseOutputBuffer(outputIndex, false)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            // 🔽 리샘플링 (44.1kHz → 16kHz 등)
            val pcm16k = resampleTo16kMono(
                pcmOutput.toByteArray(),
                format
            )

            writeWavFile(
                pcm16k as ByteArray,
                outputWav,
                sampleRate = 16000,
                channels = 1
            )

            withContext(Dispatchers.Main) {
                onComplete(true)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(false)
            }
        }
    }
}

fun writeWavFile(
    pcmData: ByteArray,
    outputFile: File,
    sampleRate: Int,
    channels: Int
) {
    val byteRate = sampleRate * channels * 2

    val header = ByteArray(44)

    fun writeInt(value: Int, offset: Int) {
        header[offset] = value.toByte()
        header[offset + 1] = (value shr 8).toByte()
        header[offset + 2] = (value shr 16).toByte()
        header[offset + 3] = (value shr 24).toByte()
    }

    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeInt(36 + pcmData.size, 4)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeInt(16, 16)
    header[20] = 1
    header[22] = channels.toByte()
    writeInt(sampleRate, 24)
    writeInt(byteRate, 28)
    header[32] = (channels * 2).toByte()
    header[34] = 16
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeInt(pcmData.size, 40)

    outputFile.outputStream().use {
        it.write(header)
        it.write(pcmData)
    }
}

fun resampleTo16kMono(
    pcm: ByteArray,
    format: MediaFormat
): Any? {
    val inputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    if (inputRate == 16000 && channels == 1) return pcm

    val samples = pcm.size / 2
    val shortBuffer = ShortArray(samples)
    ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)

    val ratio = inputRate / 16000.0
    val outputSamples = (samples / ratio).toInt()
    val output = ShortArray(outputSamples)

    for (i in 0 until outputSamples) {
        output[i] = shortBuffer[(i * ratio).toInt()]
    }

//    return ByteBuffer.allocate(output.size * 2)
//        .order(ByteOrder.LITTLE_ENDIAN)
//        .asShortBuffer()
//        .put(output)
//        .array()

    val buffer = ByteBuffer.allocate(output.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN)

    buffer.asShortBuffer().put(output)

    return buffer.array() // 이건 ByteBuffer라서 가능
}


// wav → PCM 데이터 추출 함수
fun extractPcmFromWav(wavFile: File): ByteArray {
    val bytes = wavFile.readBytes()
    // WAV header = 44 bytes (PCM 기준)
    return bytes.copyOfRange(44, bytes.size)
}

// PCM → 텍스트 (Vosk) 함수
fun recognizeSpeechFromPcm(
    pcmData: ByteArray,
    model: Model,
    sampleRate: Float = 16000f
): String {

//    val recognizer = Recognizer(model, sampleRate)
//    val chunkSize = 4096
//
//    var offset = 0
//    while (offset < pcmData.size) {
//        val size = minOf(chunkSize, pcmData.size - offset)
//        val chunk = pcmData.copyOfRange(offset, offset + size)
//
//        recognizer.acceptWaveForm(chunk, chunk.size)
//
//        offset += size
//    }
//
//    val finalResult = recognizer.finalResult
//    recognizer.close()
//
//    return JSONObject(finalResult).optString("text", "")
    
    // 위 코드 너무 오래 걸려서 한번에 처리하는 것으로
    val recognizer = Recognizer(model, sampleRate)
    recognizer.acceptWaveForm(pcmData, pcmData.size) // 한 번에 처리
    val result = JSONObject(recognizer.finalResult).optString("text", "")
    recognizer.close()
    return result
}
// 전체 파이프라인 함수 (m4a → text)
fun transcribeM4aOffline(
    context: Context,
    inputM4a: File,
    onResult: (String?) -> Unit
) {
    val outputWav = File(context.cacheDir, "temp.wav")

    convertM4aToWav(inputM4a, outputWav) { success ->
        if (!success) {
            Log.d("!!!!!!!!", "변환실패")
            onResult(null)
            return@convertM4aToWav
        }

        // lifecycle-aware scope 권장
            try {
                val pcm = extractPcmFromWav(outputWav)

                val model = VoskModelHolder.get()
                Log.d("pcm to text", "변환중임")
                Log.d("pcm to text", "${pcm}")
                val text = recognizeSpeechFromPcm(pcm, model)
                Log.d("!!!!!!!!", "${model}")
                Log.d("!!!!!!!!", "${text}")
                Log.d("!!!!!!!!", "${pcm}")
                outputWav.delete()

                onResult(text)

//                withContext(Dispatchers.Main) {
//                }
            } catch (e: Exception) {
                Log.d("Error", "${e}")
            }
    }
}

// assets → filesDir 복사
fun copyAssetFolder(context: Context, assetFolderName: String, targetDir: File) {
    val assets = context.assets.list(assetFolderName) ?: return
    targetDir.mkdirs()
    for (file in assets) {
        val srcPath = "$assetFolderName/$file"
        val dstFile = File(targetDir, file)
        if (context.assets.list(srcPath)?.isNotEmpty() == true) {
            // 폴더면 재귀 복사
            copyAssetFolder(context, srcPath, dstFile)
        } else {
            context.assets.open(srcPath).use { input ->
                dstFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

// 모델 싱글톤 필수

object VoskModelHolder {
    @Volatile
    private var model: Model? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Model {
        if (model == null) {
            synchronized(this) {
                if (model == null) {
                    val modelDir = File(appContext.filesDir, "model")
                    copyAssetFolder(appContext, "model", modelDir)
                    model = Model(modelDir.absolutePath)
                }
            }
        }
        return model!!
    }

    fun release() {
        model?.close()
        model = null
    }
}



// 30초 잘라서 옮기기
fun trimAudioTo30Seconds(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    maxDurationUs: Long = 30_000_000L
) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, inputUri, null)

    var audioTrackIndex = -1
    var format: MediaFormat? = null

    for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            format = f
            break
        }
    }

    require(audioTrackIndex >= 0) { "Audio track not found" }

    extractor.selectTrack(audioTrackIndex)

    val muxer = MediaMuxer(
        outputFile.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )

    val muxerTrackIndex = muxer.addTrack(format!!)
    muxer.start()

    val buffer = ByteBuffer.allocate(1024 * 1024)
    val info = MediaCodec.BufferInfo()

    while (true) {
        info.offset = 0
        info.size = extractor.readSampleData(buffer, 0)

        if (info.size < 0) break

        info.presentationTimeUs = extractor.sampleTime
        if (info.presentationTimeUs > maxDurationUs) break

        info.flags = extractor.sampleFlags
        muxer.writeSampleData(muxerTrackIndex, buffer, info)

        extractor.advance()
    }

    muxer.stop()
    muxer.release()
    extractor.release()
}

fun transcribeLatestCall30s(
    context: Context,
    onResult: (String?) -> Unit
) {
    val repo = AudioRepository(context.contentResolver)

    // 맨 마지막에 입력된 파일
    val latestAudio = repo.loadAudioFiles().firstOrNull()
    if (latestAudio == null) {
        onResult(null)
        return
    }
    // 메인스레드 막지 안도록 함
        try {
            // 30초로 변환해서 cache 에 넣어버림
            val trimmedFile = File(context.cacheDir, "1_30s.m4a")
            trimAudioTo30Seconds(
                context = context,
                inputUri = latestAudio.uri,
                outputFile = trimmedFile
            )
            // text 로변환하려고함
            transcribeM4aOffline(context, trimmedFile) { text ->
                onResult(text)
            }

        } catch (e: Exception) {
            Log.d("에러ㅏ!", "${e}")
        }
}





