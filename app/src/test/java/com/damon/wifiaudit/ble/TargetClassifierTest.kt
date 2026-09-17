package com.damon.wifiaudit.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TargetClassifierTest {

    private fun createDevice(
        mac: String = "11:22:33:44:55:66",
        deviceName: String? = null,
        manufacturerFromAdv: String? = null,
        rssi: Int = -60,
        iBeaconUuid: String? = null,
        beaconType: String? = null,
        serviceUuids: List<String> = emptyList()
    ): BleDeviceInfo {
        return BleDeviceInfo(
            macAddress = mac,
            deviceName = deviceName,
            rssi = rssi,
            txPowerLevel = null,
            serviceUuids = serviceUuids,
            iBeaconMajor = null,
            iBeaconMinor = null,
            iBeaconUuid = iBeaconUuid,
            beaconType = beaconType,
            beaconPayload = null,
            lastSeenMillis = System.currentTimeMillis(),
            manufacturerFromAdv = manufacturerFromAdv
        )
    }

    @Test
    fun classifiesAppleIBeaconAsTracker() {
        val device = createDevice(
            deviceName = "Keys",
            iBeaconUuid = "F7826DA6-4FA2-4E98-8024-BC5B71E0893E"
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("TRACKER", classification!!.category)
        assertEquals("Keys", classification.label)
    }

    @Test
    fun classifiesAirTagAsTracker() {
        val device = createDevice(
            deviceName = "AirTag",
            manufacturerFromAdv = "Apple Inc."
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("TRACKER", classification!!.category)
        assertEquals("Apple AirTag", classification.label)
    }

    @Test
    fun classifiesTileAsTracker() {
        val device = createDevice(
            beaconType = "Tile Tracker"
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("TRACKER", classification!!.category)
    }

    @Test
    fun classifiesAutomotiveAsAuto() {
        val device = createDevice(
            deviceName = "Tesla Model 3"
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("AUTO", classification!!.category)
        assertEquals("Tesla Model 3", classification.label)
    }

    @Test
    fun classifiesRingAndWyzeCamerasAsCamera() {
        val ringDevice = createDevice(
            mac = "9C:76:13:AA:BB:CC",
            deviceName = "Front Door"
        )
        val ringClassification = TargetClassifier.classify(ringDevice)
        assertNotNull(ringClassification)
        assertEquals("CAMERA", ringClassification!!.category)

        val wyzeDevice = createDevice(
            deviceName = "Wyze Cam v3"
        )
        val wyzeClassification = TargetClassifier.classify(wyzeDevice)
        assertNotNull(wyzeClassification)
        assertEquals("CAMERA", wyzeClassification!!.category)
    }

    @Test
    fun classifiesEspressifAsIoT() {
        val device = createDevice(
            deviceName = "ESP32-Node",
            manufacturerFromAdv = "Espressif Systems"
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("IOT", classification!!.category)
    }

    @Test
    fun classifiesServiceUuidFallback() {
        val device = createDevice(
            serviceUuids = listOf("0000FE78-0000-1000-8000-00805F9B34FB") // smart home FE78
        )
        val classification = TargetClassifier.classify(device)
        assertNotNull(classification)
        assertEquals("SMART_HOME", classification!!.category)
    }

    @Test
    fun returnsNullForUnrecognizedDevice() {
        val device = createDevice(
            deviceName = "Random Headset",
            manufacturerFromAdv = "Generic Audio Inc."
        )
        val classification = TargetClassifier.classify(device)
        assertNull(classification)
    }
}
