package com.final_pj.voice.feature.stt

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stt_result")
data class SttResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val callId: String,              // 통화에 대한 ID (UUID든 서버 ID든)
    val text: String,
    val isVoicephishing: Boolean? = null,
    val voicephishingScore: Double? = null,
    val category: String? = null,   // raw string 저장 추천 ("기관사칭" 등)
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)