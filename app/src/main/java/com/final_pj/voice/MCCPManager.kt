package com.final_pj.voice

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream


// MCCP 매니저임 통화중에 발동/ 전화 끊기면 끝!

// 1. 변환된 pt 모델을 가지고 매니저를 등록할것이다

// 2. 통화를 하면 5초단위로 잘라서 모델에 입력시킬것임!

// 3. 결과물을 반환함 (Room) 을 이용해서 저장할것임


// 재미니가 짜준 예시 코드임 고쳐야함

// wav 파일을 들여보내야함

// 샘플링 데이터 기준 코드 (식별용)
//SR = 16000
//MAX_SAMPLES = SR * 5
//
//N_MFCC = 40
//HOP_LENGTH = 160
//N_FFT = 400
//MAX_LEN = 500
//
//BATCH_SIZE = 256        # A100
//NUM_WORKERS = 8         # Drive 최대 안정
//PREFETCH = 4

//loader = DataLoader(
//DriveWavDataset(files),
//batch_size=BATCH_SIZE,
//num_workers=NUM_WORKERS,
//pin_memory=True,
//persistent_workers=True,
//prefetch_factor=PREFETCH
//)

class MCCPManager(private val context: Context) {
    private var module: Module? = null

    init {
        // 1. 모델 로드 (assets 폴더에서 읽어오기)
        loadModel("MFCC/binary_cnn_mfcc.pt")
    }

    private fun loadModel(modelName: String) {
        val modelPath = assetFilePath(context, modelName)
        Log.d("modelPath", "${modelPath}")
        module = LiteModuleLoader.load(modelPath)
    }

    // 2. 5초 단위의 데이터를 입력받아 추론 수행
    fun processAudioSegment(audioData: FloatArray) {
        // MFCC 추출 로직 (별도 유틸리티 필요)
        val mfccFeatures = extractMFCC(audioData)

        // Tensor 변환 (모델 입력 Shape에 맞춰 조정 필요: 예 [1, 1, 40, 500])
        // sample, channel, length(second) shape
        val inputTensor = Tensor.fromBlob(mfccFeatures, longArrayOf(1, 1, 40, 500))

        // 모델 실행
        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        val scores = outputTensor?.dataAsFloatArray

        // 3. 결과 반환 및 Room 저장
        if (scores != null) {
            Log.d("결과!!!!!!!", "${scores[0]}")
           //saveToRoom(scores[0]) // 결과값 저장 로직 호출
        }
    }

    private fun extractMFCC(audioData: FloatArray): FloatArray {
        // 여기에서 Librosa 같은 라이브러리를 포팅하거나
        // 직접 MFCC 연산 로직을 구현하여 FloatArray로 반환해야 합니다.
        return FloatArray(40 * 500)
    }

    private fun saveToRoom(result: Float) {
        // Coroutine을 활용하여 Room DB에 비동기 저장
        // 예: database.analysisDao().insert(AnalysisEntity(result = result))
    }

    // Assets 파일을 실제 파일 경로로 변환하는 유틸리티
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        context.assets.open(assetName).use { isStream ->
            FileOutputStream(file).use { osStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (isStream.read(buffer).also { read = it } != -1) {
                    osStream.write(buffer, 0, read)
                }
                osStream.flush()
            }
        }
        return file.absolutePath
    }
}