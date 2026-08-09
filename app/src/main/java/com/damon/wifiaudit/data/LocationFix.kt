package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_fixes",
    indices = [Index(value = ["timestamp"])]
)
data class LocationFix(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
)
