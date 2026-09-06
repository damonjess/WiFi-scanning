package com.damon.wifiaudit.ble

import java.util.UUID

/**
 * Decoded BLE advertisement extracted directly from raw AD structures.
 *
 * Pure-JVM (no Android dependencies) so it is unit-testable and can be reused
 * both for live [android.bluetooth.le.ScanResult] scans and for historical
 * [scanRecord] bytes loaded from the database.
 *
 * AD-type reference (Core Specification, Vol 3, Part C, Sec 11):
 *  0x01 Flags
 *  0x02/0x03 Incomplete/Complete 16-bit Service UUID list
 *  0x04/0x05 Incomplete/Complete 32-bit Service UUID list
 *  0x06/0x07 Incomplete/Complete 128-bit Service UUID list
 *  0x08 Shortened Local Name, 0x09 Complete Local Name
 *  0x0A Tx Power Level
 *  0x16 Service Data (16-bit UUID)
 *  0x20 Service Data (32-bit UUID)
 *  0x21 Service Data (128-bit UUID)
 *  0xFF Manufacturer Specific Data
 */
data class BleAdvertisement(
    val rawBytes: ByteArray,
    val flags: Int? = null,
    val localName: String? = null,
    val txPowerLevel: Int? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val isConnectable: Boolean = false
) {
    /** All manufacturer-data payloads concatenated for quick inspection. */
    fun manufacturerHex(): String =
        manufacturerData.entries.joinToString(", ") { (id, b) ->
            "0x${id.toString(16).padStart(4, '0').uppercase()}=${b.toHex()}"
        }

    fun serviceDataHex(): String =
        serviceData.entries.joinToString(", ") { (u, b) ->
            "${BleUuidResolver.shortUuid(u)}=${b.toHex()}"
        }

    fun rawHex(): String = rawBytes.toHex()
}

object BleAdvertisementParser {

    private const val AD_FLAGS: Byte = 0x01
    private const val AD_UUID16_COMPLETE: Byte = 0x03
    private const val AD_UUID16_INCOMPLETE: Byte = 0x02
    private const val AD_UUID32_COMPLETE: Byte = 0x05
    private const val AD_UUID32_INCOMPLETE: Byte = 0x04
    private const val AD_UUID128_COMPLETE: Byte = 0x07
    private const val AD_UUID128_INCOMPLETE: Byte = 0x06
    private const val AD_NAME_SHORT: Byte = 0x08
    private const val AD_NAME_COMPLETE: Byte = 0x09
    private const val AD_TX_POWER: Byte = 0x0A
    private const val AD_SERVICE_DATA_16: Byte = 0x16
    private const val AD_SERVICE_DATA_32: Byte = 0x20
    private const val AD_SERVICE_DATA_128: Byte = 0x21
    private const val AD_MANUFACTURER: Byte = 0xFF.toByte()

    fun parse(bytes: ByteArray?): BleAdvertisement {
        if (bytes == null || bytes.isEmpty()) return BleAdvertisement(ByteArray(0))

        val serviceUuids = mutableListOf<UUID>()
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        val serviceData = mutableMapOf<UUID, ByteArray>()
        var flags: Int? = null
        var localName: String? = null
        var txPower: Int? = null

        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + 1 + length > bytes.size) break // truncated, stop

            val type = bytes[offset + 1].toInt() and 0xFF
            // payload starts after the type byte; its length is (length - 1)
            val payloadStart = offset + 2
            val payloadEnd = offset + 1 + length
            if (payloadEnd > bytes.size) break
            val payload = bytes.copyOfRange(payloadStart, payloadEnd)

            when (type.toByte()) {
                AD_FLAGS -> {
                    if (payload.isNotEmpty()) flags = payload[0].toInt() and 0xFF
                }
                AD_UUID16_COMPLETE, AD_UUID16_INCOMPLETE -> {
                    var i = 0
                    while (i + 1 < payload.size) {
                        serviceUuids += uuid16(payload[i], payload[i + 1])
                        i += 2
                    }
                }
                AD_UUID32_COMPLETE, AD_UUID32_INCOMPLETE -> {
                    var i = 0
                    while (i + 3 < payload.size) {
                        serviceUuids += uuid32(payload, i)
                        i += 4
                    }
                }
                AD_UUID128_COMPLETE, AD_UUID128_INCOMPLETE -> {
                    var i = 0
                    while (i + 15 < payload.size) {
                        serviceUuids += uuid128(payload, i)
                        i += 16
                    }
                }
                AD_NAME_SHORT, AD_NAME_COMPLETE -> {
                    localName = runCatching { String(payload, Charsets.ISO_8859_1).trim().ifEmpty { null } }.getOrNull()
                }
                AD_TX_POWER -> {
                    if (payload.isNotEmpty()) txPower = payload[0].toInt()
                }
                AD_SERVICE_DATA_16 -> {
                    if (payload.size >= 2) {
                        val uuid = uuid16(payload[0], payload[1])
                        serviceData[uuid] = payload.copyOfRange(2, payload.size)
                    }
                }
                AD_SERVICE_DATA_32 -> {
                    if (payload.size >= 4) {
                        val uuid = uuid32(payload, 0)
                        serviceData[uuid] = payload.copyOfRange(4, payload.size)
                    }
                }
                AD_SERVICE_DATA_128 -> {
                    if (payload.size >= 16) {
                        val uuid = uuid128(payload, 0)
                        serviceData[uuid] = payload.copyOfRange(16, payload.size)
                    }
                }
                AD_MANUFACTURER -> {
                    if (payload.size >= 2) {
                        val companyId = (payload[0].toInt() and 0xFF) or
                            ((payload[1].toInt() and 0xFF) shl 8)
                        manufacturerData[companyId] = payload.copyOfRange(2, payload.size)
                    }
                }
            }

            offset = payloadEnd
        }

        val connectable = flags?.let { (it and 0x02) != 0 } ?: false
        return BleAdvertisement(
            rawBytes = bytes,
            flags = flags,
            localName = localName,
            txPowerLevel = txPower,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            isConnectable = connectable
        )
    }

    // ---- UUID helpers ----

    private fun uuid16(b0: Byte, b1: Byte): UUID {
        val value = (b0.toInt() and 0xFF) or ((b1.toInt() and 0xFF) shl 8)
        val hex = value.toString(16).padStart(4, '0')
        return UUID.fromString("0000$hex-0000-1000-8000-00805f9b34fb")
    }

    private fun uuid32(b: ByteArray, offset: Int): UUID {
        var v = 0
        for (i in 0..3) v = v or ((b[offset + i].toInt() and 0xFF) shl (8 * i))
        val hex = v.toString(16).padStart(8, '0')
        return UUID.fromString("$hex-0000-1000-8000-00805f9b34fb")
    }

    /** 128-bit service UUIDs are stored little-endian; reverse to MSB-first then build. */
    private fun uuid128(b: ByteArray, offset: Int): UUID {
        val le = b.copyOfRange(offset, offset + 16)
        val msbFirst = le.reversedArray()
        var msb = 0L
        var lsb = 0L
        for (i in 0..7) msb = (msb shl 8) or (msbFirst[i].toLong() and 0xFF)
        for (i in 8..15) lsb = (lsb shl 8) or (msbFirst[i].toLong() and 0xFF)
        return UUID(msb, lsb)
    }
}

fun ByteArray.toHex(separator: String = ""): String =
    joinToString(separator) { "%02X".format(it) }
