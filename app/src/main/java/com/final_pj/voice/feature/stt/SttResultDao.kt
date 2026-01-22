package com.final_pj.voice.feature.stt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.final_pj.voice.feature.mypage.model.DailyPhishingStat
import kotlinx.coroutines.flow.Flow

@Dao
interface SttResultDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SttResultEntity): Long

    @Query("SELECT * FROM stt_result ORDER BY createdAt DESC")
    fun observeAllResults(): Flow<List<SttResultEntity>>

    @Query("SELECT * FROM stt_result WHERE callId = :callId ORDER BY createdAt DESC")
    fun observeByCallId(callId: String): Flow<List<SttResultEntity>>

    @Query("SELECT * FROM stt_result WHERE callId = :callId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByCallId(callId: String): SttResultEntity?
    
    // 관련된 stt 파일 보기
    @Query("SELECT * FROM stt_result WHERE callId = :callId LIMIT 1")
    suspend fun getById(callId: String): SttResultEntity?

    // createdAt 이거 기준으로 따지는거임??? 쉬부럴이게 맞나...
    // 맞긴한데 너무 복잡함 callId 로 하면 안되나...??
    @Query("""
        SELECT strftime('%Y-%m-%d', createdAt/1000, 'unixepoch', 'localtime') AS day,
               SUM(CASE WHEN isVoicephishing = 1 THEN 1 ELSE 0 END) AS suspiciousCount,
               AVG(voicephishingScore) AS avgScore
        FROM stt_result
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getDailyPhishingStats(): Flow<List<DailyPhishingStat>>


}