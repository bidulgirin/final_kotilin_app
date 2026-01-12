package com.final_pj.voice.core

import android.content.Context
import androidx.room.Room
import com.final_pj.voice.core.AppDatabase

object DbProvider {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app.db"
            ).build().also { INSTANCE = it }
        }
    }
}