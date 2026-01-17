package com.final_pj.voice.feature.stt.model

data class SttResponse(
    val text: String,
    val llm: LlmResult? = null
)

data class LlmResult(
    val isVoicephishing: Boolean,
    val voicephishingScore: Double, // 0.0 ~ 1.0 같은 점수라고 가정
    val category: String?,   // "기관사칭" 같은 카테고리 표기 할것임
    val summary: String,
    val keywords: List<String>?, // 키워드 목록
    val community : List<String> ?,// 대화
)