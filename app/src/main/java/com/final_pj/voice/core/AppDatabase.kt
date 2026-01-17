package com.final_pj.voice.core

import androidx.room.Database
import androidx.room.RoomDatabase
import com.final_pj.voice.feature.blocklist.network.dao.BlockedNumberDao
import com.final_pj.voice.feature.blocklist.network.entity.BlockedNumberEntity
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
    version = 6, // 버전 증가
    exportSchema = false
)

// 로컬 저장 데이터 베이스 dao
abstract class AppDatabase : RoomDatabase() {
    // 차단목록
    abstract fun blockedNumberDao(): BlockedNumberDao
    // callid 별 요약내용
    abstract fun SttResultDao(): SttResultDao
    // 보이스 피싱 데이터피싱
    abstract fun phishingDao(): PhishingDao
    // 이게 문제네... ㅠㅠㅠㅠㅠ아아ㅏ아아아앙
    // 이 요약 문서는...필요없어
    // sttResultDao 에 컬럼하나 더 추가하면 됨...
    abstract fun callSummaryDao(): CallSummaryDao 
}