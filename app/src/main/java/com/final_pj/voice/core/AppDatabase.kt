package com.final_pj.voice.core

import androidx.room.Database
import androidx.room.RoomDatabase
import com.final_pj.voice.feature.blocklist.network.dao.BlockedNumberDao
import com.final_pj.voice.feature.blocklist.network.entity.BlockedNumberEntity
import com.final_pj.voice.feature.mypage.dao.SttResultStatsDao
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
        PhishingNumberEntity::class,
        // 새로 추가
        CallSummaryEntity::class 
    ],
    version = 8, // 버전 증가: 7 -> 8
    exportSchema = false
)

// 로컬 저장 데이터 베이스 dao
abstract class AppDatabase : RoomDatabase() {
    // 차단목록
    abstract fun blockedNumberDao(): BlockedNumberDao
    // callid 별 요약내용
    abstract fun sttSummaryDao(): SttResultDao
    // 보이스 피싱 데이터피싱
    abstract fun phishingDao(): PhishingDao
    abstract fun callSummaryDao(): CallSummaryDao
    // 통계/시각화용 DAO 추가
    abstract fun sttResultStatsDao(): SttResultStatsDao
}