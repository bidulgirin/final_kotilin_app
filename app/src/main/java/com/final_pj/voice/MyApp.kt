package com.final_pj.voice

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 포그라운드 백그라운드 상태 확인
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {

                override fun onStart(owner: LifecycleOwner) {
                    Log.d("AppState", "앱이 포그라운드로 진입")
                }

                override fun onStop(owner: LifecycleOwner) {
                    Log.d("AppState", "앱이 백그라운드로 이동")
                }
            }
        )
    }
}
