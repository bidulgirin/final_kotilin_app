package com.final_pj.voice.core

import android.content.Context
import androidx.room.Room

object AppDatabaseProvider {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_db"
            )
                .addMigrations(AppDatabase.MIGRATION_8_9) // 마이그레이션 등록
                .fallbackToDestructiveMigration() // 만약 마이그레이션 실패 시 데이터 초기화 후 재생성 (보험용)
                .build()
                .also { INSTANCE = it }
        }
    }
}
