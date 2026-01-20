package com.final_pj.voice.core

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.final_pj.voice.feature.blocklist.network.dao.BlockedNumberDao
import com.final_pj.voice.feature.blocklist.network.entity.BlockedNumberEntity
import com.final_pj.voice.feature.mypage.dao.SttResultStatsDao
import com.final_pj.voice.feature.report.entity.PhishingNumberEntity
import com.final_pj.voice.feature.report.network.dto.PhishingDao
import com.final_pj.voice.feature.stt.SttResultDao
import com.final_pj.voice.feature.stt.SttResultEntity

@Database(
    entities = [
        BlockedNumberEntity::class,
        SttResultEntity::class,
        PhishingNumberEntity::class,
        CallSummaryEntity::class 
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun sttSummaryDao(): SttResultDao
    abstract fun phishingDao(): PhishingDao
    abstract fun callSummaryDao(): CallSummaryDao
    abstract fun sttResultStatsDao(): SttResultStatsDao

    companion object {
        // 버전 8 -> 9 마이그레이션: 신규 점수 컬럼 2개 추가
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stt_result ADD COLUMN deepvoiceScore REAL")
                database.execSQL("ALTER TABLE stt_result ADD COLUMN koberScore REAL")
            }
        }
    }
}
