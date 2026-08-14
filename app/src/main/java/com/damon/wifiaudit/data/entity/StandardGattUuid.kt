package com.damon.wifiaudit.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "standard_gatt_uuids",
    primaryKeys = ["uuid", "type"],
    indices = [Index(value = ["uuid"])]
)
data class StandardGattUuid(
    val uuid: String,       // Normalized: "180F" (16-bit) or full 128-bit for custom
    val type: String,       // "service" | "characteristic" | "descriptor"
    val name: String        // e.g. "Battery Service"
)
