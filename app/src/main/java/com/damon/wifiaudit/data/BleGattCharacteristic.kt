package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_gatt_characteristics",
    foreignKeys = [
        ForeignKey(
            entity = BleGattService::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["serviceId"])]
)
data class BleGattCharacteristic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceId: Long,
    val uuid: String,
    val properties: Int,
    val permissions: Int,
    val instanceId: Int,
    val descriptorsJson: String = "[]" // JSON array of descriptor UUIDs
)
