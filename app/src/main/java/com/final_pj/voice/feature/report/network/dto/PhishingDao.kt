package com.final_pj.voice.feature.report.network.dto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.final_pj.voice.feature.report.entity.PhishingNumberEntity

@Dao
interface PhishingDao {
    @Query("SELECT * FROM phishing_number_list WHERE numberNormalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): PhishingNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PhishingNumberEntity>)

    @Query("DELETE FROM phishing_number_list")
    suspend fun clearAll()
}