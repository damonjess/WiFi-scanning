package com.damon.wifiaudit.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rssi_heatmap_points",
    indices = [Index(value = ["macAddress", "timestamp"])]
)
data class RssiHeatmapPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macAddress: String,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val timestamp: Long = System.currentTimeMillis()
)
