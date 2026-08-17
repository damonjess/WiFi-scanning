package com.damon.wifiaudit.ui.map

/**
 * A single stored radio observation selected outside the map.  Keeping the
 * coordinate on the navigation payload lets the map centre and highlight the
 * exact scan even when a device has numerous historical sightings.
 */
data class ScanLocationTarget(
    val observationId: Long,
    val macAddress: String,
    val type: RadioType,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val rssi: Int,
    val timestamp: Long,
) {
    enum class RadioType { WIFI, BLE }
}
