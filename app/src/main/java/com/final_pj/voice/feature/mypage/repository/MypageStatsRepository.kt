package com.final_pj.voice.feature.mypage.repository
import com.final_pj.voice.feature.mypage.dao.SttResultStatsDao
import com.final_pj.voice.feature.mypage.model.CategoryCount
import com.final_pj.voice.feature.mypage.model.DailyCount
import com.final_pj.voice.feature.mypage.model.DailyPhishingStat
import kotlinx.coroutines.flow.Flow

class MypageStatsRepository(
    private val statsDao: SttResultStatsDao
) {
    fun dailyCallCounts(): Flow<List<DailyCount>> =
        statsDao.getDailyCallCounts()

    fun dailyPhishingStats(): Flow<List<DailyPhishingStat>> =
        statsDao.getDailyPhishingStats()

    fun categoryCounts(): Flow<List<CategoryCount>> =
        statsDao.getCategoryCounts()
}
