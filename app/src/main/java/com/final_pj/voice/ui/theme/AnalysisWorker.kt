package com.final_pj.voice.ui.theme

import android.content.Context
import android.content.Intent
import android.util.Log
import com.final_pj.voice.ResultActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AnalysisWorker {

    fun start(context: Context, filePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("AI", "녹음 파일 분석 시작: $filePath")

            delay(2000) // 딥러닝 분석 시뮬레이션

            val score = (70..95).random()

            showResult(context, score)
        }
    }

    private fun showResult(context: Context, score: Int) {
        val intent = Intent(context, ResultActivity::class.java).apply {
            putExtra("score", score)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
