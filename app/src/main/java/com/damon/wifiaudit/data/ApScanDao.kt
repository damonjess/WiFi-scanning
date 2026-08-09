package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ApScanDao {
    @Insert
    suspend fun insertAll(scans: List<ApScanEntity>)

    @Query("SELECT * FROM ap_scans ORDER BY timestampMillis ASC")
    suspend fun getAll(): List<ApScanEntity>

    @Query("SELECT COUNT(*) FROM ap_scans")
    suspend fun count(): Int
}
