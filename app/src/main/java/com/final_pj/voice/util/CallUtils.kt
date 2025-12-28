package com.final_pj.voice.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

object CallUtils {

    fun placeCall(
        context: Context,
        number: String,
        speakerOn: Boolean = false
    ) {
        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        val uri = Uri.fromParts("tel", number, null)

        val extras = Bundle().apply {
            putBoolean(
                TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE,
                speakerOn
            )
        }

        telecomManager.placeCall(uri, extras)
    }
}
