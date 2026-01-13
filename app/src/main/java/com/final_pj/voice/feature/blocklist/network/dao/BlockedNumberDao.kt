package com.final_pj.voice.feature.blocklist.network.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.final_pj.voice.feature.blocklist.network.entity.BlockedNumberEntity

@Dao
interface BlockedNumberDao {

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insert(item: BlockedNumberEntity): Long

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNormalized(normalizedNumber: String): Int

    @Query("SELECT normalizedNumber FROM blocked_numbers")
    suspend fun getAllNormalized(): List<String>
}