package com.final_pj.voice.core

import com.final_pj.voice.feature.mypage.dao.SttResultStatsDao
import com.final_pj.voice.feature.mypage.repository.MypageStatsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideSttResultStatsDao(db: AppDatabase): SttResultStatsDao =
        db.sttResultStatsDao()

    @Provides
    fun provideMypageStatsRepository(statsDao: SttResultStatsDao): MypageStatsRepository =
        MypageStatsRepository(statsDao)
}