package com.final_pj.voice.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_summaries")
data class CallSummaryEntity(
    @PrimaryKey val callId: Long,
    val summaryText: String,
    val isProcessed: Boolean = false
)