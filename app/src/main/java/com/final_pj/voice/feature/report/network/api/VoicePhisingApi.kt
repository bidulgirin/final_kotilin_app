package com.final_pj.voice.feature.report.network.api

import com.final_pj.voice.feature.report.network.dto.VoicePhisingCreateReq
import com.final_pj.voice.feature.report.network.dto.VoicePhisingOutRes
import com.final_pj.voice.feature.report.network.dto.VoicePhisingSnapshotItemRes
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface VoicePhisingApi {
    @POST("/api/v1/voice_phising_number_list")
    suspend fun insertNumber(
        @Body body: VoicePhisingCreateReq
    ): Response<VoicePhisingOutRes>

    // 하루 1회 로컬 업데이트용
    @GET("/api/v1/voice_phising_number_list/snapshot")
    suspend fun getSnapshot(): Response<List<VoicePhisingSnapshotItemRes>>


}
