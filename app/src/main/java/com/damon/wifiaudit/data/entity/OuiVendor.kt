package com.damon.wifiaudit.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "oui_vendors",
    indices = [Index(value = ["oui"], unique = true)]
)
data class OuiVendor(
    @PrimaryKey
    val oui: String,              // Normalized: "AC233F" (no colons, uppercase)
    val vendorName: String,       // e.g. "Espressif Inc."
    val country: String? = null,  // e.g. "CN"
    val address: String? = null   // Full IEEE registry address (optional)
)
