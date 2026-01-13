package com.final_pj.voice.feature.report.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phishing_number_list",
    indices = [Index(value = ["numberNormalized"], unique = true)]
)
data class PhishingNumberEntity(
    @PrimaryKey val id: String, // 서버 UUID 있으면 그대로
    val number: String,
    val numberNormalized: String,
    val description: String?,
    val reportCount: Int,       // 신고 건수 표시용
    val updatedAt: Long         // 스냅샷 갱신 시간(밀리초)
)
