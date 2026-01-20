package com.final_pj.voice.feature.stt

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "stt_result")
@TypeConverters(Converters::class)
data class SttResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val callId: String,              // 통화에 대한 ID
    val text: String,
    val isVoicephishing: Boolean? = null,
    val deepvoiceScore: Double? = null, // 변경됨
    val koberScore: Double? = null,     // 추가됨
    val voicephishingScore: Double? = null, // 하위 호환성 위해 유지 (필요시)
    val category: String? = null,
    val summary: String? = null,
    val keywords: List<String>? = null,
    val conversation: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
)
