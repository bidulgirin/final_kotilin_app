// 통화중 상태 감지 코드
package com.final_pj.voice.service
import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

class CallStateObserver(
    private val context: Context,
    private val onCallStateChanged: (Int) -> Unit
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val callback = object : TelephonyCallback(),
        TelephonyCallback.CallStateListener {

        override fun onCallStateChanged(state: Int) {
            onCallStateChanged(state)
        }
    }

    fun start() {
        telephonyManager.registerTelephonyCallback(
            context.mainExecutor,
            callback
        )
    }

    fun stop() {
        telephonyManager.unregisterTelephonyCallback(callback)
    }
}