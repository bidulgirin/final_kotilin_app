package com.final_pj.voice.feature.stt

import com.final_pj.voice.feature.stt.model.SttResponse
import java.util.concurrent.ConcurrentHashMap

class SttBuffer {
    private val map = ConcurrentHashMap<String, SttResponse>()

    fun put(callId: String, resp: SttResponse) {
        map[callId] = resp
    }

    fun pop(callId: String): SttResponse? {
        return map.remove(callId)
    }
}