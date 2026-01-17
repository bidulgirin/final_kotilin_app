package com.final_pj.voice.feature.stt

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "stt_result")
@TypeConverters(Converters::class)
data class SttResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val callId: String,              // 통화에 대한 ID (UUID든 서버 ID든)
    val text: String,
    val isVoicephishing: Boolean? = null,
    val voicephishingScore: Double? = null,
    val category: String? = null,
    val summary: String? = null,
    val keywords: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
    // 추가로 오잉? 포린키로 연결되어있는데여...
)
