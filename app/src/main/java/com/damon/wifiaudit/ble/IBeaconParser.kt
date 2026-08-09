package com.damon.wifiaudit.ble

import android.bluetooth.le.ScanRecord
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Major/Minor are not standard BLE advertisement fields — they only exist
 * inside Apple's iBeacon manufacturer-specific data payload (company ID 0x004C).
 * Layout after the 2-byte "beacon type" prefix (0x02, 0x15):
 *   [16 bytes UUID][2 bytes Major][2 bytes Minor][1 byte measured Tx power]
 * Devices broadcasting standard BLE (not iBeacon) will simply have no match here.
 */
object IBeaconParser {
    private const val APPLE_COMPANY_ID = 0x004C
    private const val IBEACON_TYPE_PREFIX: Byte = 0x02
    private const val IBEACON_LENGTH_BYTE: Byte = 0x15
    private const val EXPECTED_PAYLOAD_LENGTH = 23 // 2 prefix + 16 uuid + 2 major + 2 minor + 1 power

    data class IBeaconData(val uuid: String, val major: Int, val minor: Int)

    fun parse(scanRecord: ScanRecord?): IBeaconData? {
        val data = scanRecord?.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return null
        if (data.size < EXPECTED_PAYLOAD_LENGTH) return null
        if (data[0] != IBEACON_TYPE_PREFIX || data[1] != IBEACON_LENGTH_BYTE) return null

        return try {
            val buffer = ByteBuffer.wrap(data)
            buffer.position(2)

            val uuidBytes = ByteArray(16)
            buffer.get(uuidBytes)
            val uuid = bytesToUuidString(uuidBytes)

            val major = buffer.short.toInt() and 0xFFFF
            val minor = buffer.short.toInt() and 0xFFFF

            IBeaconData(uuid, major, minor)
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToUuidString(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
