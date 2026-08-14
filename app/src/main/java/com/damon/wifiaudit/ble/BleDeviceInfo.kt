package com.damon.wifiaudit.ble

import java.util.UUID

data class BleDeviceInfo(
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val txPowerLevel: Int?,
    val serviceUuids: List<String>,
    val iBeaconMajor: Int?,
    val iBeaconMinor: Int?,
    val iBeaconUuid: String?,
    val lastSeenMillis: Long,
    val manufacturerFromAdv: String? = null,
    val rawBytes: ByteArray? = null,
    val isConnectable: Boolean = false
) {
    val vendorName: String?
        get() = com.damon.wifiaudit.vendor.OuiVendorLookup.lookup(macAddress)
}
