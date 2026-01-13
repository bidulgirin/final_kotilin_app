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

/**
 * 스냅샷 응답(권장)
 * 백엔드에서 report_count, updated_at(또는 created_at) 같은 값 포함시키는 걸 추천
 */
data class VoicePhisingSnapshotItemRes(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: String,
    @SerializedName("description") val description: String?,
    @SerializedName("report_count") val reportCount: Int,
    @SerializedName("updated_at") val updatedAt: String? // ISO 문자열이면 파싱 or 무시해도 됨
)

