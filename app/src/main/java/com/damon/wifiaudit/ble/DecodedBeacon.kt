package com.damon.wifiaudit.ble

/**
 * A beacon decoded from a BLE advertisement. [type] is the canonical format
 * name (e.g. "iBeacon", "Eddystone-UID"); [summary] is a one-line human-readable
 * rendering; [fields] holds structured key/value pairs for the detail screen.
 *
 * Designed so a device that matches no known format simply has
 * [BeaconDecoder.decode] return null — callers then fall back to the generic
 * BLE device path.
 */
data class DecodedBeacon(
    val type: String,
    val summary: String,
    val fields: Map<String, String> = emptyMap()
)
