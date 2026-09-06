package com.damon.wifiaudit.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-JVM beacon parsers. These construct raw BLE
 * advertisement byte arrays by hand (matching the AD-structure layout) so the
 * parsers are validated independently of any Android [android.bluetooth.le.ScanRecord].
 */
class BeaconDecoderTest {

    // ---- AD structure helpers ----

    /** Builds an AD structure: [length][type][payload]. */
    private fun ad(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(2 + payload.size)
        out[0] = (payload.size + 1).toByte()
        out[1] = type.toByte()
        System.arraycopy(payload, 0, out, 2, payload.size)
        return out
    }

    /** Manufacturer-specific data AD (0xFF): [companyId LE][payload]. */
    private fun mfrAd(companyId: Int, payload: ByteArray): ByteArray =
        ad(0xFF, byteArrayOf(
            (companyId and 0xFF).toByte(),
            ((companyId shr 8) and 0xFF).toByte()
        ) + payload)

    /** Service Data 16-bit AD (0x16): [uuid LE][payload]. */
    private fun serviceData16Ad(uuidShort: Int, payload: ByteArray): ByteArray =
        ad(0x16, byteArrayOf(
            (uuidShort and 0xFF).toByte(),
            ((uuidShort shr 8) and 0xFF).toByte()
        ) + payload)

    // ---- iBeacon ----

    @Test
    fun decodesIBeacon() {
        // Apple 0x004C, prefix 02 15, UUID, major, minor, tx power
        val uuid = "F7826DA64FA24E988024BC5B71E0893E".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = byteArrayOf(0x02, 0x15) + uuid + byteArrayOf(0x00, 0x01, 0x00, 0x02, 0xC5.toByte())
        val bytes = mfrAd(0x004C, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("iBeacon", decoded!!.type)
        assertTrue(decoded.summary.contains("F7826DA6-4FA2-4E98-8024-BC5B71E0893E"))
        assertEquals("1", decoded.fields["Major"])
        assertEquals("2", decoded.fields["Minor"])
    }

    // ---- AltBeacon ----

    @Test
    fun decodesAltBeacon() {
        // 0xBEAC code + 20-byte id + tx power. Company ID is arbitrary for AltBeacon.
        val id = ByteArray(20) { 0xAB.toByte() }
        val payload = byteArrayOf(0xBE.toByte(), 0xAC.toByte()) + id + byteArrayOf(0xC5.toByte())
        val bytes = mfrAd(0x0118, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("AltBeacon", decoded!!.type)
        assertEquals("43947", decoded.fields["Major"]) // 0xABAB
        assertEquals("43947", decoded.fields["Minor"])
    }

    @Test
    fun ignoresNonAltBeaconManufacturerData() {
        val bytes = mfrAd(0x004C, byteArrayOf(0x00, 0x00, 0x00)) // not 02 15, too short
        assertNull(BeaconDecoder.decodeBytes(bytes))
    }

    // ---- Eddystone UID ----

    @Test
    fun decodesEddystoneUid() {
        val namespace = ByteArray(10) { 0x11 }
        val instance = ByteArray(6) { 0x22 }
        val payload = byteArrayOf(0x00, 0xC5.toByte()) + namespace + instance
        val bytes = serviceData16Ad(0xFEAA, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Eddystone-UID", decoded!!.type)
        assertEquals("11111111111111111111", decoded.fields["Namespace"])
        assertEquals("222222222222", decoded.fields["Instance"])
    }

    // ---- Eddystone TLM ----

    @Test
    fun decodesEddystoneTlm() {
        // frame 0x20, version 0, battery 3000mV (0x0BB8), temp 14.0625C (0x0E10 = 3600 /256)
        val payload = byteArrayOf(
            0x20, 0x00, 0x0B, 0xB8.toByte(), 0x0E, 0x10, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x0A
        )
        val bytes = serviceData16Ad(0xFEAA, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Eddystone-TLM", decoded!!.type)
        assertEquals("3000 mV", decoded.fields["Battery"])
        assertEquals("5", decoded.fields["Adv Count"])
    }

    @Test
    fun decodesEddystoneTlmNegativeTemperature() {
        // -5 C = signed -1280 = 0xFB00 as unsigned 16-bit
        val payload = byteArrayOf(
            0x20, 0x00, 0x0C, 0x54.toByte(), 0xFB.toByte(), 0x00, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x0B
        )
        val bytes = serviceData16Ad(0xFEAA, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Eddystone-TLM", decoded!!.type)
        assertEquals("-5.0 C", decoded.fields["Temperature"])
    }

    // ---- Eddystone URL ----

    @Test
    fun decodesEddystoneUrl() {
        // frame 0x10, tx 0xC5, scheme 0x02 (http://), then 'g','o','o','g','l', 0x00 (.com/)
        val payload = byteArrayOf(0x10, 0xC5.toByte(), 0x02) + "googl".toByteArray() + byteArrayOf(0x00)
        val bytes = serviceData16Ad(0xFEAA, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Eddystone-URL", decoded!!.type)
        assertEquals("http://googl.com/", decoded.fields["URL"])
    }

    // ---- RuuviTag (manufacturer 0x0499) ----

    @Test
    fun decodesRuuviTag() {
        val payload = byteArrayOf(0x05, 0x0F, 0x27, 0x40, 0x35, 0xC4.toByte(), 0x54, 0x00)
        val bytes = mfrAd(0x0499, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("RuuviTag (v5)", decoded!!.type)
        assertEquals("v5 (0x05)", decoded.fields["Data Format"])
    }

    // ---- Xiaomi MiBeacon (service data 0xFE95) ----

    @Test
    fun decodesXiaomiMiBeacon() {
        // frame control 0x0001, product id 0x0123, frame counter 0x07
        val payload = byteArrayOf(0x01, 0x00, 0x23, 0x01, 0x07)
        val bytes = serviceData16Ad(0xFE95, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Xiaomi MiBeacon", decoded!!.type)
        assertEquals("0x0123", decoded.fields["Product ID"])
        assertEquals("7", decoded.fields["Frame Counter"])
    }

    // ---- Google Fast Pair (service data 0xFE2C) ----

    @Test
    fun decodesFastPair() {
        // 0x00 (show model id), then 3-byte model id 0x000001
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val bytes = serviceData16Ad(0xFE2C, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Google Fast Pair", decoded!!.type)
        assertEquals("0x000001", decoded.fields["Model ID"])
    }

    // ---- Tile (service data 0xFEED) ----

    @Test
    fun decodesTile() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val bytes = serviceData16Ad(0xFEED, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Tile Tracker", decoded!!.type)
        assertEquals("01020304", decoded.fields["Raw Payload"])
    }

    // ---- Microsoft (manufacturer 0x0006) ----

    @Test
    fun decodesMicrosoftBeacon() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val bytes = mfrAd(0x0006, payload)

        val decoded = BeaconDecoder.decodeBytes(bytes)
        assertNotNull(decoded)
        assertEquals("Microsoft Beacon", decoded!!.type)
        assertEquals("0102030405", decoded.fields["Raw Payload"])
    }

    // ---- Unknown advertisement returns null ----

    @Test
    fun unknownAdvertisementReturnsNull() {
        val bytes = ad(0x09, "JustAName".toByteArray()) // only local name, no beacon
        assertNull(BeaconDecoder.decodeBytes(bytes))
    }

    // ---- Raw AD parser: multiple manufacturer data blocks ----

    @Test
    fun parsesMultipleManufacturerDataBlocks() {
        val bytes = mfrAd(0x004C, byteArrayOf(0x02, 0x15)) + mfrAd(0x0006, byteArrayOf(0x01))
        val adv = BleAdvertisementParser.parse(bytes)
        assertTrue(adv.manufacturerData.containsKey(0x004C))
        assertTrue(adv.manufacturerData.containsKey(0x0006))
    }
}
