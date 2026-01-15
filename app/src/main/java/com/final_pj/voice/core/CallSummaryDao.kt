package com.final_pj.voice.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.final_pj.voice.feature.stt.SttResultEntity

@Dao
interface CallSummaryDao {
    // 특정 callId 리스트에 해당하는 모든 결과를 가져옴
    @Query("SELECT * FROM stt_result WHERE callId IN (:callIds)")
    suspend fun getSummariesByCallIds(callIds: List<String>): List<SttResultEntity>

    // 기존 방식대로 단건 조회도 유지 가능
    @Query("SELECT summary FROM stt_result WHERE callId = :callId LIMIT 1")
    suspend fun getSummaryByCallId(callId: String): String?
}