package com.final_pj.voice.core

import androidx.room.Database
import androidx.room.RoomDatabase
import com.final_pj.voice.feature.blocklist.BlockedNumberDao
import com.final_pj.voice.feature.blocklist.BlockedNumberEntity
import com.final_pj.voice.feature.report.entity.PhishingNumberEntity
import com.final_pj.voice.feature.report.network.dto.PhishingDao
import com.final_pj.voice.feature.stt.SttResultDao
import com.final_pj.voice.feature.stt.SttResultEntity

/**
 * Room DB 정의
 * - entities 배열에 테이블들 등록
 * - version 변경 시 migration 고려 (배포용 아니면 destructive도 가능)
 */
@Database(
    entities = [
        // 기존 차단번호 테이블 엔티티 (이미 있다면 이름 맞춰서)
        BlockedNumberEntity::class,
        // 새로 추가된 STT 결과 테이블
        SttResultEntity::class,
        // 신고된번호 테이블
        PhishingNumberEntity::class

    ],
    version = 3, // 이거 올리고 마이그레이션 하는 듯
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun SttResultDao(): SttResultDao
    abstract fun phishingDao(): PhishingDao
}