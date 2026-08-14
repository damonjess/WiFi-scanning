package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RssiHeatmapDao {
    @Insert
    suspend fun insert(point: RssiHeatmapPoint)

    @Query("SELECT * FROM rssi_heatmap_points WHERE macAddress = :mac ORDER BY timestamp DESC")
    fun getPointsForMac(mac: String): Flow<List<RssiHeatmapPoint>>

    @Query("SELECT * FROM rssi_heatmap_points WHERE macAddress = :mac ORDER BY timestamp DESC LIMIT 500")
    suspend fun getRecentPoints(mac: String): List<RssiHeatmapPoint>

    @Query("DELETE FROM rssi_heatmap_points WHERE macAddress = :mac")
    suspend fun clearForMac(mac: String)

    @Query("SELECT COUNT(*) FROM rssi_heatmap_points WHERE macAddress = :mac")
    suspend fun countForMac(mac: String): Int
}
