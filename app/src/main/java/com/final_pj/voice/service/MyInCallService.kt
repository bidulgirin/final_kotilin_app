package com.final_pj.voice.service

import android.telecom.Call
import android.telecom.InCallService
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log

class MyInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        Log.d("CALL", "Call added: ${call.details.handle}")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("CALL", "Call removed")
    }
}