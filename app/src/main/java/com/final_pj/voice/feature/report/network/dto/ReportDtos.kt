package com.final_pj.voice.feature.report.network.dto

import com.google.gson.annotations.SerializedName

data class VoicePhisingCreateReq(
    @SerializedName("number") val number: String,
    @SerializedName("description") val description: String? = null
)

data class VoicePhisingOutRes(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: String,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String
)

