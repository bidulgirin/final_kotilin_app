package com.final_pj.voice.feature.stt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SttResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SttResultEntity): Long

    @Query("SELECT * FROM stt_result WHERE callId = :callId ORDER BY createdAt DESC")
    fun observeByCallId(callId: String): Flow<List<SttResultEntity>>

    @Query("SELECT * FROM stt_result WHERE callId = :callId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByCallId(callId: String): SttResultEntity?
    
    // 관련된 stt 파일 보기
    @Query("SELECT * FROM stt_result WHERE callId = :callId LIMIT 1")
    suspend fun getById(callId: String): SttResultEntity?
}