package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_sightings",
    foreignKeys = [
        ForeignKey(
            entity = LocationFix::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["locationId"]), Index(value = ["macAddress"])]
)
data class BleSighting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val txPower: Int?,
    val proximityUuid: String?,
    val deviceModel: String? = null
)
