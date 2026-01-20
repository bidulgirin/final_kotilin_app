package com.final_pj.voice.feature.stt.model

data class SttResponse(
    val text: String,
    val llm: LlmResult? = null
)

data class LlmResult(
    val isVoicephishing: Boolean,
    val deepvoiceScore: Double? = null,
    val koberScore: Double? = null,
    val voicephishingScore: Double? = null, 
    val category: String?,   
    val summary: String,
    val keywords: List<String>?, 
    val community : List<String> ?,
)
