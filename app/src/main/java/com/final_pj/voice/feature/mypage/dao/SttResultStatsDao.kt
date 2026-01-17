package com.final_pj.voice.feature.mypage.dao

import androidx.room.Dao
import androidx.room.Query
import com.final_pj.voice.feature.mypage.model.CategoryCount
import com.final_pj.voice.feature.mypage.model.DailyCount
import com.final_pj.voice.feature.mypage.model.DailyPhishingStat
import kotlinx.coroutines.flow.Flow

@Dao
interface SttResultStatsDao {

    @Query("""
        SELECT strftime('%Y-%m-%d', createdAt/1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS count
        FROM stt_result
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getDailyCallCounts(): Flow<List<DailyCount>>

    @Query("""
        SELECT strftime('%Y-%m-%d', createdAt/1000, 'unixepoch', 'localtime') AS day,
               SUM(CASE WHEN isVoicephishing = 1 THEN 1 ELSE 0 END) AS suspiciousCount,
               AVG(voicephishingScore) AS avgScore
        FROM stt_result
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getDailyPhishingStats(): Flow<List<DailyPhishingStat>>

    @Query("""
        SELECT category AS category,
               COUNT(*) AS count
        FROM stt_result
        GROUP BY category
        ORDER BY count DESC
    """)
    fun getCategoryCounts(): Flow<List<CategoryCount>>
}
