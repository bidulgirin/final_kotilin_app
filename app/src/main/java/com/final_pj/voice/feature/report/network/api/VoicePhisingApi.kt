package com.final_pj.voice.feature.report.network.api

import com.final_pj.voice.feature.report.network.dto.VoicePhisingCreateReq
import com.final_pj.voice.feature.report.network.dto.VoicePhisingOutRes
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface VoicePhisingApi {
    @POST("/api/v1/voice_phising_number_list")
    suspend fun insertNumber(
        @Body body: VoicePhisingCreateReq
    ): Response<VoicePhisingOutRes>
}
