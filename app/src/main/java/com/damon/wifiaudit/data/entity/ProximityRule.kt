package com.damon.wifiaudit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proximity_rules")
data class ProximityRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                    // "Desk Lamp", "Security Key", "CTS Sync"
    val targetMac: String,               // MAC address or "ANY" for generic beacons
    val ruleType: String,                // "TIME_SYNC" | "PROXIMITY_ACTION" | "SECURITY_KEY"
    
    // Trigger conditions
    val rssiThreshold: Int? = null,      // e.g. -75 (fire when weaker than this)
    val rssiThresholdMax: Int? = null,   // e.g. -40 (fire when stronger than this)
    
    // GATT action (for SMART_HOME / TIME_SYNC)
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    val writePayloadHex: String? = null, // hex string to write when triggered
    
    // Notification / lock action
    val showNotification: Boolean = false,
    val lockAppOnExit: Boolean = false,  // Security Key feature
    
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
