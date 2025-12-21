package com.final_pj.voice

import android.content.Context
import android.util.Log

class RecordManager(private val context: Context) {

    fun start() {
        Log.d("Record", "녹음 시작")
        // 실제론 MediaRecorder 등 사용
    }

    fun stop(onFinish: (String) -> Unit) {
        Log.d("Record", "녹음 종료")
        val fakeFilePath = "/storage/emulated/0/call_record.wav"
        onFinish(fakeFilePath)
    }
}
