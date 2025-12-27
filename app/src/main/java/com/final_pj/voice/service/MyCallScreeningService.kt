package com.final_pj.voice.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.CallScreeningService

// 전화 왔을때
class MyCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        if (!isIncoming) {
            allowCall(callDetails)
            return
        }

        // 예: 특정 번호 차단
        if (number == "01012345678") {
            blockCall(callDetails)
        } else {
            allowCall(callDetails)
        }
    }

    private fun allowCall(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(false)
                .build()
        )
    }

    private fun blockCall(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build()
        )
    }
}
