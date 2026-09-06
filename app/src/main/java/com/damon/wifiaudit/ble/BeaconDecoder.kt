package com.damon.wifiaudit.ble

import android.bluetooth.le.ScanRecord
import java.util.UUID

/**
 * Single entry point that runs every known beacon parser against a BLE
 * advertisement and returns the first match, in priority order:
 *
 *  1. Apple iBeacon      (manufacturer data 0x004C, prefix 02 15)
 *  2. AltBeacon          (manufacturer data with 0xBEAC code)
 *  3. Eddystone          (service data UUID 0xFEAA: UID/URL/TLM/EID)
 *  4. RuuviTag           (manufacturer data 0x0499)
 *  5. Xiaomi MiBeacon    (service data 0xFE95)
 *  6. Google Fast Pair   (service data 0xFE2C)
 *  7. Tile               (service data 0xFEED)
 *  8. Samsung SmartThings (service data 0xFD54/5A/5B/5F or mfr 0x0075)
 *  9. Microsoft           (manufacturer data 0x0006)
 *
 * Works for both live [ScanResult] scans and historical raw [scanRecord] bytes
 * loaded from the database, because it parses raw AD structures itself rather
 * than depending on Android [ScanRecord] reflection.
 */
object BeaconDecoder {

    private const val APPLE_COMPANY_ID = 0x004C
    private const val EDDYSTONE_SERVICE_UUID_SHORT = 0xFEAA

    private val eddystoneUuid: UUID = shortUuid(EDDYSTONE_SERVICE_UUID_SHORT)

    /** Decode from a live Android [ScanRecord]. */
    fun decode(scanRecord: ScanRecord?): DecodedBeacon? {
        if (scanRecord == null) return null
        return decodeBytes(scanRecord.bytes)
    }

    /** Decode from raw advertisement bytes (works for DB-loaded scan records). */
    fun decodeBytes(bytes: ByteArray?): DecodedBeacon? {
        val adv = BleAdvertisementParser.parse(bytes)
        return decode(adv)
    }

    fun decode(adv: BleAdvertisement): DecodedBeacon? {
        // 1. iBeacon
        adv.manufacturerData[APPLE_COMPANY_ID]?.let { payload ->
            IBeaconParser.parsePayload(payload)?.let {
                return DecodedBeacon(
                    type = "iBeacon",
                    summary = "uuid=${it.uuid} major=${it.major} minor=${it.minor}",
                    fields = mapOf(
                        "UUID" to it.uuid,
                        "Major" to it.major.toString(),
                        "Minor" to it.minor.toString()
                    )
                )
            }
        }

        // 2. AltBeacon — scan every manufacturer-data entry for the 0xBEAC code
        for ((_, payload) in adv.manufacturerData) {
            AltBeaconParser.parse(payload)?.let { return it }
        }

        // 3. Eddystone
        adv.serviceData[eddystoneUuid]?.let { EddystoneParser.parse(it) }?.let { return it }

        // 4-7. Proprietary service-data envelopes (Tile, FastPair, Xiaomi, Samsung)
        for ((uuid, payload) in adv.serviceData) {
            ProprietaryBeaconRecognizers.recognizeServiceData(uuid, payload)?.let { return it }
        }

        // 8-9. Proprietary manufacturer-data envelopes (Ruuvi, Microsoft, Samsung)
        for ((companyId, payload) in adv.manufacturerData) {
            ProprietaryBeaconRecognizers.recognizeManufacturer(companyId, payload)?.let { return it }
        }

        return null
    }

    private fun shortUuid(value: Int): UUID {
        val hex = value.toString(16).padStart(4, '0')
        return UUID.fromString("0000$hex-0000-1000-8000-00805f9b34fb")
    }
}
