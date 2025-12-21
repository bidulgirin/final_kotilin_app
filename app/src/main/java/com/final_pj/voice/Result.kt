package com.final_pj.voice

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val score = intent.getIntExtra("score", 0)
        Toast.makeText(
            this,
            "보이스피싱 가능성: $score%",
            Toast.LENGTH_LONG
        ).show()
    }
}