package com.final_pj.voice.feature.stt

import android.content.Context
import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.mfcc.MFCC
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class MFCCManager(private val context: Context) {
    private var module: Module? = null

    init {
        // 1. 모델 로드 (assets 폴더에서 읽어오기)
        // 모바일용으로 변환함
        //loadModel("MFCC/binary_cnn_mfcc_lite.pt") // 이제 안씀
        // 모델 안에 있는 padding 그게 문제 인듯
        // 왜 문제가 되는지 알아보고 설명해야함
    }

    private fun loadModel(modelName: String) {
        val modelPath = assetFilePath(context, modelName)
        module = LiteModuleLoader.load(modelPath)
    }

    // 2. 5초 단위의 데이터를 입력받아 추론 수행
    fun processAudioSegment(audioData: FloatArray) {
        // MFCC 추출 로직 (별도 유틸리티 필요)
        val mfccFeatures = extractMFCC(audioData)

        val inputTensor = Tensor.fromBlob(
            mfccFeatures,
            longArrayOf(1, 1, 40, 500)   //  여기중요 (lite 모델에서는 4차원만 받음)
        )


        // 모델 실행
        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        val scores = outputTensor?.dataAsFloatArray

        // 3. 결과 반환 및 Room 저장
        if (scores != null) {
            val logit = outputTensor.dataAsFloatArray[0]
            val prob = sigmoid(logit)

            //Log.d("logit", "$logit")
            Log.d("prob", "$prob")
        }
    }
    fun sigmoid(x: Float): Float {
        return (1f / (1f + exp(-x)))
    }

    // 실시간 통화 전처리
    private fun extractMFCC(audioData: FloatArray): FloatArray {

        // =========================
        // 1. 기본 MFCC 파라미터 정의
        // =========================
        val sampleRate = 16000f   // 오디오 샘플링 레이트 (Hz)
        val bufferSize = 400      // FFT 윈도우 크기 (N_FFT)
        val hopSize = 160         // 프레임 간 이동 거리 (HOP_LENGTH)
        val nMFCC = 40            // 추출할 MFCC 계수 개수
        val maxFrames = 500       // 모델 입력에 맞춘 최대 프레임 길이

        // =========================================
        // 2. FloatArray -> PCM16 바이트 배열 변환
        // =========================================
        // TarsosDSP는 float[]가 아니라 PCM16 입력을 받기 때문에 변환
        val pcmBytes = ByteBuffer
            .allocate(audioData.size * 2) // short = 2 bytes
            .order(ByteOrder.LITTLE_ENDIAN) // 리틀 엔디안
            .apply {
                for (f in audioData) {
                    // -1..1 범위의 float를 short로 변환 (PCM16)
                    putShort((f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
                }
            }
            .array() // 최종 ByteArray

        // =========================================
        // 3. TarsosDSP 오디오 포맷 정의
        // =========================================
        val audioFormat = TarsosDSPAudioFormat(
            sampleRate, // 샘플링 레이트
            16,         // 비트 깊이 (16bit)
            1,          // 모노 채널
            true,       // signed
            false       // bigEndian (리틀 엔디안)
        )

        // =========================================
        // 4. ByteArray -> AudioInputStream
        // =========================================
        val audioStream = UniversalAudioInputStream(
            ByteArrayInputStream(pcmBytes), // PCM 데이터를 스트림으로 감쌈
            audioFormat
        )

        // =========================================
        // 5. Dispatcher 설정
        // =========================================
        // AudioDispatcher: 오디오를 프레임 단위로 잘라서 AudioProcessor에게 전달
        val dispatcher = AudioDispatcher(
            audioStream,
            bufferSize,          // FFT 윈도우 크기
            bufferSize - hopSize // hop length
        )

        // =========================================
        // 6. MFCC 객체 생성
        // =========================================
        val mfcc = MFCC(
            bufferSize,    // FFT 윈도우 크기
            sampleRate,    // 샘플링 레이트
            nMFCC,         // 추출할 MFCC 계수 개수
            40,            // n_mels, 멜 필터뱅크 개수
            20f,           // 최소 주파수 (Hz)
            sampleRate / 2 // 최대 주파수 (Hz)
        )

        // MFCC 결과를 저장할 리스트
        val mfccList = mutableListOf<FloatArray>()

        // =========================================
        // 7. AudioProcessor 등록
        // =========================================
        dispatcher.addAudioProcessor(mfcc) // MFCC 계산
        dispatcher.addAudioProcessor(object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean {
                // 각 프레임마다 MFCC 계수 복사 후 리스트에 저장
                mfccList.add(mfcc.mfcc.copyOf())
                return true
            }
            override fun processingFinished() {}
        })

        // =========================================
        // 8. Dispatcher 실행
        // =========================================
        // 오디오 스트림을 순회하면서 MFCC를 계산
        dispatcher.run()

        // =========================================
        // 9.  결과 배열로 정리 (모델 입력용)
        // =========================================
        // 2D MFCC (nMFCC x frames) -> 1D FloatArray (nMFCC * maxFrames)
        val result = FloatArray(nMFCC * maxFrames)
        for (t in 0 until minOf(maxFrames, mfccList.size)) { // 프레임 수 제한
            for (c in 0 until nMFCC) {                        // MFCC 계수
                // column-major 방식으로 flatten
                result[c * maxFrames + t] = mfccList[t][c]
            }
        }

        // 최종 1D FloatArray 반환 (shape: [40*500])
        return result
    }



    private fun saveToRoom(result: Float) {
        // Coroutine을 활용하여 Room DB에 비동기 저장
        // 예: database.analysisDao().insert(AnalysisEntity(result = result))
    }

    // Assets 파일을 실제 파일 경로로 변환하는 유틸리티
    fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)

        // 부모 디렉토리 생성 (핵심)
        file.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }

        Log.d("ASSET_COPY", "copied to ${file.absolutePath}, size=${file.length()}")
        return file.absolutePath
    }


}