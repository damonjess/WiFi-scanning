package com.damon.wifiaudit.ble

data class BleDeviceInfo(
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val txPowerLevel: Int?,       // null if not advertised
    val serviceUuids: List<String>,
    val iBeaconMajor: Int?,       // null if not an iBeacon-format advertiser
    val iBeaconMinor: Int?,
    val iBeaconUuid: String?,
    val lastSeenMillis: Long
) {
    val vendorName: String?
        get() = com.damon.wifiaudit.vendor.OuiVendorLookup.lookup(macAddress)
}
