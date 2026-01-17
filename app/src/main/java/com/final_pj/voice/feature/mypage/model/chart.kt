package com.final_pj.voice.feature.mypage.model

// 날짜(일)별 전체 통화 수
data class DailyCount(
    val day: String,   // "2026-01-17" 같은 형태
    val count: Int
)

// 날짜별 의심(보이스피싱) 건수 / 평균 스코어
data class DailyPhishingStat(
    val day: String,
    val suspiciousCount: Int,
    val avgScore: Double?
)

// 카테고리별 건수
data class CategoryCount(
    val category: String?,
    val count: Int
)
