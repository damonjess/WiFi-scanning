package com.damon.wifiaudit.ble

import java.util.UUID

/**
 * Lightweight recognizers for proprietary / vendor beacon envelopes.
 *
 * These formats are not fully open or have drifted across firmware revisions,
 * so per the spec review we only decode the stable envelope fields
 * (company ID / service UUID / well-known framing) and surface the rest as a
 * raw hex payload summary. This reliably *detects* and *identifies* the beacon
 * without risking wrong field values from guessed layouts.
 *
 * The one exception is Xiaomi MiBeacon (0xFE95) and RuuviTag (0x0499), whose
 * leading framing fields are well documented; those are lightly parsed.
 */
object ProprietaryBeaconRecognizers {

    // ---- Service-data (16-bit) envelopes ----

    private val TILE_UUID = shortUuid(0xFEED)
    private val FAST_PAIR_UUID = shortUuid(0xFE2C)
    private val XIAOMI_UUID = shortUuid(0xFE95)
    private val SAMSUNG_UUIDS = setOf(
        shortUuid(0xFD54), shortUuid(0xFD5A), shortUuid(0xFD5B), shortUuid(0xFD5F)
    )

    fun recognizeServiceData(uuid: UUID, payload: ByteArray): DecodedBeacon? {
        val short = shortUuidValue(uuid)
        return when {
            uuid == XIAOMI_UUID -> parseXiaomi(payload)
            uuid == TILE_UUID -> envelope("Tile Tracker", "Tile", payload)
            uuid == FAST_PAIR_UUID -> parseFastPair(payload)
            uuid in SAMSUNG_UUIDS -> envelope("Samsung SmartThings", "Samsung", payload)
            short == 0xFEAA -> null // Eddystone handled by EddystoneParser
            else -> null
        }
    }

    // ---- Manufacturer-data envelopes ----

    fun recognizeManufacturer(companyId: Int, payload: ByteArray): DecodedBeacon? {
        return when (companyId) {
            0x0499 -> parseRuuvi(payload)
            0x0006 -> envelope("Microsoft Beacon", "Microsoft", payload)
            0x0075 -> envelope("Samsung BLE", "Samsung", payload)
            else -> null
        }
    }

    // ---- Xiaomi MiBeacon (service data 0xFE95) ----
    // [Frame Control:2 LE][Product ID:2 LE][Frame Counter:1][MAC?:6][Capability?][Object?]
    private fun parseXiaomi(payload: ByteArray): DecodedBeacon? {
        if (payload.size < 5) return envelope("Xiaomi MiBeacon", "Xiaomi", payload)
        val frameControl = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
        val productId = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
        val frameCounter = payload[4].toInt() and 0xFF
        return DecodedBeacon(
            type = "Xiaomi MiBeacon",
            summary = "product=0x${productId.toString(16).padStart(4, '0').uppercase()} cnt=$frameCounter",
            fields = mapOf(
                "Frame Control" to "0x${frameControl.toString(16).padStart(4, '0').uppercase()}",
                "Product ID" to "0x${productId.toString(16).padStart(4, '0').uppercase()}",
                "Frame Counter" to frameCounter.toString(),
                "Raw" to payload.toHex()
            )
        )
    }

    // ---- RuuviTag (manufacturer data, company 0x0499) ----
    // payload[0] = data format (0x03 v3, 0x04 v4, 0x05 v5). v5 is the current raw format.
    private fun parseRuuvi(payload: ByteArray): DecodedBeacon? {
        if (payload.isEmpty()) return envelope("RuuviTag", "Ruuvi", payload)
        val format = payload[0].toInt() and 0xFF
        return DecodedBeacon(
            type = "RuuviTag (v$format)",
            summary = "format=0x${format.toString(16).padStart(2, '0')} raw=${payload.toHex()}",
            fields = mapOf(
                "Data Format" to "v$format (0x${format.toString(16).padStart(2, '0').uppercase()})",
                "Raw Payload" to payload.toHex()
            )
        )
    }

    // ---- Google Fast Pair (service data 0xFE2C) ----
    // Model ID frame: [0x00 show UI][Model ID:3 BE]. Battery/etc. are later frames.
    private fun parseFastPair(payload: ByteArray): DecodedBeacon? {
        if (payload.size >= 4 && payload[0].toInt() == 0x00) {
            val modelId = ((payload[1].toInt() and 0xFF) shl 16) or
                ((payload[2].toInt() and 0xFF) shl 8) or
                (payload[3].toInt() and 0xFF)
            return DecodedBeacon(
                type = "Google Fast Pair",
                summary = "model=0x${modelId.toString(16).padStart(6, '0').uppercase()}",
                fields = mapOf(
                    "Model ID" to "0x${modelId.toString(16).padStart(6, '0').uppercase()}",
                    "Raw" to payload.toHex()
                )
            )
        }
        return envelope("Google Fast Pair", "Google", payload)
    }

    private fun envelope(type: String, vendor: String, payload: ByteArray): DecodedBeacon =
        DecodedBeacon(
            type = type,
            summary = "raw=${payload.toHex()}",
            fields = mapOf("Vendor" to vendor, "Raw Payload" to payload.toHex())
        )

    // ---- UUID helpers ----

    private fun shortUuid(value: Int): UUID {
        val hex = value.toString(16).padStart(4, '0')
        return UUID.fromString("0000$hex-0000-1000-8000-00805f9b34fb")
    }

    private fun shortUuidValue(uuid: UUID): Int {
        val s = uuid.toString().uppercase()
        val isStandard = s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805F9B34FB")
        val short = if (isStandard) s.substring(4, 8) else s.substring(0, 8)
        return runCatching { short.toInt(16) }.getOrDefault(0)
    }
}
