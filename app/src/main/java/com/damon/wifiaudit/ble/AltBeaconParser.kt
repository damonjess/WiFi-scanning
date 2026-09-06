package com.damon.wifiaudit.ble

/**
 * AltBeacon parser. AltBeacon is an open spec (Radius Networks) that mirrors
 * iBeacon's UUID/Major/Minor layout but is identified by the 2-byte beacon
 * code 0xBEAC at the start of the manufacturer-specific-data payload (the
 * company-ID field is not fixed, so we scan every manufacturer-data entry).
 *
 * Layout after company ID:
 *   [0xBE][0xAC]              beacon code
 *   [16 bytes]                proximity beacon id (UUID-like)
 *   [2 bytes]                 major  (big-endian)
 *   [2 bytes]                 minor  (big-endian)
 *   [1 byte]                 measured tx power (signed)
 *
 * Spec: https://github.com/AltBeacon/spec
 */
object AltBeaconParser {

    private val BEACON_CODE = byteArrayOf(0xBE.toByte(), 0xAC.toByte())
    private const val MIN_PAYLOAD_LENGTH = 23 // 2 code + 20 id + 1 power

    fun parse(manufacturerPayload: ByteArray?): DecodedBeacon? {
        val data = manufacturerPayload ?: return null
        if (data.size < MIN_PAYLOAD_LENGTH) return null
        if (data[0] != BEACON_CODE[0] || data[1] != BEACON_CODE[1]) return null

        val idBytes = data.copyOfRange(2, 18)
        val beaconId = idBytes.toHex().lowercase()
        val uuidString = formatAsUuid(beaconId)
        val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        val txPower = data[22].toInt()

        return DecodedBeacon(
            type = "AltBeacon",
            summary = "uuid=$uuidString major=$major minor=$minor",
            fields = mapOf(
                "Beacon ID" to beaconId,
                "UUID" to uuidString,
                "Major" to major.toString(),
                "Minor" to minor.toString(),
                "Tx Power" to "${txPower} dBm"
            )
        )
    }

    private fun formatAsUuid(hex32: String): String =
        "${hex32.substring(0, 8)}-${hex32.substring(8, 12)}-${hex32.substring(12, 16)}-" +
            "${hex32.substring(16, 20)}-${hex32.substring(20, 32)}"
}
