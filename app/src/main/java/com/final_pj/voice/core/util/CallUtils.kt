package com.final_pj.voice.core.util

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.annotation.RequiresPermission

object CallUtils {
    @RequiresPermission(Manifest.permission.CALL_PHONE)
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
