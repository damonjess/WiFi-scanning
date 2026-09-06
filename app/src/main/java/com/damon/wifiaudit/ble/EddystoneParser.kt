package com.damon.wifiaudit.ble

/**
 * Google Eddystone parser. Eddystone frames are carried in Service Data
 * under the 16-bit service UUID 0xFEAA. The first byte of the service-data
 * payload is the frame type:
 *   0x00 UID   — 10-byte namespace + 6-byte instance + tx power
 *   0x10 URL   — compressed URL (Physical Web, largely deprecated)
 *   0x20 TLM   — telemetry: battery voltage, temperature, adv count, uptime
 *   0x30 EID   — ephemeral rotating identifier (not resolvable without key)
 *
 * Spec: https://github.com/google/eddystone (archived Dec 2022, still deployed).
 */
object EddystoneParser {

    const val EDDYSTONE_SERVICE_UUID_SHORT = 0xFEAA

    private const val FRAME_UID: Byte = 0x00
    private const val FRAME_URL: Byte = 0x10
    private const val FRAME_TLM: Byte = 0x20
    private const val FRAME_EID: Byte = 0x30

    fun parse(serviceData: ByteArray?): DecodedBeacon? {
        val data = serviceData ?: return null
        if (data.isEmpty()) return null
        val frame = data[0]
        return when (frame) {
            FRAME_UID -> parseUid(data)
            FRAME_URL -> parseUrl(data)
            FRAME_TLM -> parseTlm(data)
            FRAME_EID -> parseEid(data)
            else -> null
        }
    }

    private fun parseUid(data: ByteArray): DecodedBeacon? {
        // [frame:1][txPower:1][namespace:10][instance:6] + optional RFU
        if (data.size < 18) return null
        val txPower = data[1].toInt()
        val namespace = data.copyOfRange(2, 12).toHex()
        val instance = data.copyOfRange(12, 18).toHex()
        return DecodedBeacon(
            type = "Eddystone-UID",
            summary = "ns=$namespace inst=$instance",
            fields = mapOf(
                "Namespace" to namespace,
                "Instance" to instance,
                "Tx Power" to "${txPower} dBm"
            )
        )
    }

    private fun parseUrl(data: ByteArray): DecodedBeacon? {
        // [frame:1][txPower:1][scheme:1][encoded url...]
        if (data.size < 3) return null
        val txPower = data[1].toInt()
        val scheme = data[2].toInt() and 0xFF
        val prefix = when (scheme) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> ""
        }
        val suffixExpansion = mapOf(
            0x00 to ".com/",
            0x01 to ".org/",
            0x02 to ".edu/",
            0x03 to ".net/",
            0x04 to ".info/",
            0x05 to ".biz/",
            0x06 to ".gov/",
            0x07 to ".com",
            0x08 to ".org",
            0x09 to ".edu",
            0x0A to ".net",
            0x0B to ".info",
            0x0C to ".biz",
            0x0D to ".gov"
        )
        val sb = StringBuilder(prefix)
        for (i in 3 until data.size) {
            val b = data[i].toInt() and 0xFF
            suffixExpansion[b]?.let { sb.append(it) } ?: sb.append(b.toChar())
        }
        val url = sb.toString()
        return DecodedBeacon(
            type = "Eddystone-URL",
            summary = url,
            fields = mapOf(
                "URL" to url,
                "Tx Power" to "${txPower} dBm"
            )
        )
    }

    private fun parseTlm(data: ByteArray): DecodedBeacon? {
        // [frame:1][version:1][batt mV:2 BE][temp 8.8 fixed BE][adv count:4 BE][uptime sec:4 BE]
        if (data.size < 14) return null
        val version = data[1].toInt() and 0xFF
        val batteryMv = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        // Temperature is signed 8.8 fixed-point (big-endian); 0x8000 = -128 C (not supported).
        val tempRaw = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val temperature = tempRaw.toShort().toInt() / 256.0
        val advCount = readBe32(data, 6)
        val uptimeSec = readBe32(data, 10)
        return DecodedBeacon(
            type = "Eddystone-TLM",
            summary = "${batteryMv}mV ${temperature}C uptime=${uptimeSec}s",
            fields = mapOf(
                "Version" to version.toString(),
                "Battery" to "${batteryMv} mV",
                "Temperature" to "${temperature} C",
                "Adv Count" to advCount.toString(),
                "Uptime" to "${uptimeSec} s"
            )
        )
    }

    private fun parseEid(data: ByteArray): DecodedBeacon? {
        // [frame:1][txPower:1][EID:8]
        if (data.size < 10) return null
        val txPower = data[1].toInt()
        val eid = data.copyOfRange(2, 10).toHex()
        return DecodedBeacon(
            type = "Eddystone-EID",
            summary = "eid=$eid (rotating, not resolvable without key)",
            fields = mapOf(
                "EID" to eid,
                "Tx Power" to "${txPower} dBm",
                "Note" to "Ephemeral ID; rotates and requires server key to resolve"
            )
        )
    }

    private fun readBe32(b: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0..3) v = (v shl 8) or (b[offset + i].toLong() and 0xFF)
        return v and 0xFFFFFFFFL
    }
}
