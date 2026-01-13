package com.final_pj.voice.feature.report.repository
data class VoicePhishingLookup(
    val number: String,
    val description: String?,
    val reportCount: Int
)

interface VoicePhishingRepository {
    suspend fun lookupLocal(rawNumber: String): VoicePhishingLookup?
    suspend fun submitReport(rawNumber: String, description: String? = null): Boolean
    suspend fun syncSnapshot(): Result<Int> // 성공 시 upsert된 개수 반환
}