package com.final_pj.voice.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService

class MyInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        // 통화 상태 콜백
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                // RINGING / ACTIVE / DISCONNECTED
            }
        })

        // 통화 UI 띄우기
//        val intent = Intent(this, CallActivity::class.java)
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
    }
}