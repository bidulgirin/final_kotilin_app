package com.final_pj.voice.feature.stt.model

import com.google.gson.annotations.SerializedName

data class SttResponse(
    val text: String,
    val llm: LlmResult? = null,
    val voicephishing: VoicePhishingResult? = null,
    @SerializedName("phising_sign") val phishingSign: PhishingSignResult? = null
)

data class LlmResult(
    val isVoicephishing: Boolean,
    val voicephishingScore: Double,
    val category: String?,
    val summary: String,
    val keywords: List<String>?,
    val community: List<String>?
)

data class VoicePhishingResult(
    val flag: Boolean,
    val score: Double
)

data class PhishingSignResult(
    val status: String?,
    @SerializedName("risk_score") val riskScore: Double?,
    val keywords: List<String>?
)
