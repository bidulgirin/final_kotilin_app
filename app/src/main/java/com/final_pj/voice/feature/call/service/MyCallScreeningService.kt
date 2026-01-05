package com.final_pj.voice.feature.call.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.final_pj.voice.feature.blocklist.BlocklistCache

class MyCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        if (!isIncoming) {
            allowCall(callDetails)
            return
        }

        if (BlocklistCache.contains(number)) {
            blockCall(callDetails)
        } else {
            allowCall(callDetails)
        }
    }

    private fun allowCall(details: Call.Details) {
        respondToCall(details, CallResponse.Builder()
            .setDisallowCall(false)
            .build()
        )
    }

    private fun blockCall(details: Call.Details) {
        respondToCall(details, CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(true)
            .setSkipNotification(true)
            .build()
        )
    }
}
