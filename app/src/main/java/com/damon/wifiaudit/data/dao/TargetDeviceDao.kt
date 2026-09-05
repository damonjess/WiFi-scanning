package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damon.wifiaudit.data.TargetDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDeviceDao {
    @Query("SELECT * FROM targeted_devices WHERE category = :category ORDER BY lastSeen DESC")
    fun getByCategory(category: String): Flow<List<TargetDevice>>

    @Query("SELECT * FROM targeted_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDevice(mac: String): TargetDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: TargetDevice)
}
