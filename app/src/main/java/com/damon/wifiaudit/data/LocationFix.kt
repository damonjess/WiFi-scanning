package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_fixes",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timestamp"]), Index(value = ["sessionId"])]
)
data class LocationFix(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
)
