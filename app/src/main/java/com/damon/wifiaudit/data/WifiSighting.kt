package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wifi_sightings",
    foreignKeys = [
        ForeignKey(
            entity = LocationFix::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["locationId"]), Index(value = ["bssid"])]
)
data class WifiSighting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val encryption: String,
    val deviceModel: String? = null
)
