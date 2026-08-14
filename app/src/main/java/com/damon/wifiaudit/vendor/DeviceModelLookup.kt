package com.damon.wifiaudit.vendor

import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo

/**
 * Best-effort device model identification from Wi-Fi ScanResult or BLE advertisement.
 * Uses SSID / device-name patterns and known OUI vendors — never guaranteed accurate.
 */
object DeviceModelLookup {

    fun identify(scanResult: ScanResult): String? {
        val ssid = scanResult.SSID?.trim().orEmpty()
        if (ssid.isEmpty() || ssid == "<hidden>") return null

        val vendor = OuiVendorLookup.lookup(scanResult.BSSID)
        return matchWifi(ssid, vendor)
    }

    fun identify(ble: BleDeviceInfo): String? {
        val name = ble.deviceName?.trim().orEmpty()
        val vendor = ble.vendorName ?: OuiVendorLookup.lookup(ble.macAddress)
        return matchBle(name, vendor, ble)
    }

    private fun matchWifi(ssid: String, vendor: String?): String? {
        val s = ssid.lowercase()

        // Brand-specific SSID patterns (most reliable signal)
        when {
            s.startsWith("wyze_cam") || s.contains("wyze cam") -> return "Wyze Cam"
            s.startsWith("ezviz_") -> return "EZVIZ Camera"
            s.startsWith("reolink") || s.contains("reolink") -> return "Reolink Camera"
            s.startsWith("amcrest") -> return "Amcrest Camera"
            s.startsWith("foscam") -> return "Foscam Camera"
            s.startsWith("tapo_cam") || s.startsWith("tapo_") -> return "TP-Link Tapo Cam"
            s.startsWith("eufycam") || s.contains("eufy") -> return "Eufy Camera"
            s.startsWith("arlo_") || s.contains("arlo") -> return "Arlo Camera"
            s.startsWith("ring_") || s.contains("ring_doorbell") -> return "Ring Device"
            s.startsWith("yi_cam") || s.startsWith("yi-") -> return "Yi Camera"
            s.contains("hikvision") || s.startsWith("ds-") -> return "Hikvision Camera"
            s.contains("dahua") || s.startsWith("ipc-") -> return "Dahua Camera"
            s.startsWith("dcs-") -> return "D-Link Camera"
            s.startsWith("starlink-") -> return "Starlink Terminal"
            s.contains("nest") && (s.contains("cam") || s.contains("doorbell")) -> return "Google Nest Cam"
            s.contains("blink") -> return "Blink Camera"
            s.contains("logitech") && s.contains("circle") -> return "Logitech Circle"
        }

        // Vendor + generic cues
        val v = vendor?.lowercase().orEmpty()
        when {
            v.contains("hikvision") -> return "Hikvision Camera"
            v.contains("dahua") -> return "Dahua Camera"
            v.contains("axis communications") -> return "Axis Camera"
            v.contains("reolink") -> return "Reolink Camera"
            v.contains("amcrest") -> return "Amcrest Camera"
            v.contains("foscam") -> return "Foscam Camera"
            v.contains("wyze") -> return "Wyze Device"
            v.contains("arlo") -> return "Arlo Device"
            v.contains("ring") || v.contains("amazon technologies") && s.contains("door") -> return "Ring Device"
            v.contains("ubiquiti") -> return "Ubiquiti Device"
            v.contains("synology") -> return "Synology NAS"
            v.contains("qnap") -> return "QNAP NAS"
            v.contains("sonos") -> return "Sonos Speaker"
            v.contains("roku") -> return "Roku"
            v.contains("philips") -> return "Philips Hue"
            v.contains("espressif") || v.contains("tuya") -> return "IoT Module (ESP/Tuya)"
            v.contains("space") || v.contains("spacex") -> return "Starlink Terminal"
        }

        return null
    }

    private fun matchBle(name: String, vendor: String?, ble: BleDeviceInfo): String? {
        val n = name.lowercase()
        val v = vendor?.lowercase().orEmpty()

        when {
            n.contains("ring") || n.contains("doorbell") -> return "Ring Doorbell"
            n.contains("wyze") -> return "Wyze Device"
            n.contains("arlo") -> return "Arlo Device"
            n.contains("eufy") -> return "Eufy Device"
            n.contains("nest") -> return "Google Nest"
            n.contains("tile") -> return "Tile Tracker"
            n.contains("airtag") || (v.contains("apple") && ble.iBeaconUuid != null) -> return "Apple AirTag / iBeacon"
            n.contains("xiaomi") || n.contains("mi ") -> return "Xiaomi Device"
            n.contains("fitbit") || n.contains("garmin") || n.contains("whoop") -> return "Wearable"
            n.contains("ibeacon") || ble.iBeaconUuid != null -> return "iBeacon"
        }

        when {
            v.contains("apple") -> return "Apple Device"
            v.contains("samsung") -> return "Samsung Device"
            v.contains("google") || v.contains("nest") -> return "Google Device"
            v.contains("amazon") -> return "Amazon Device"
            v.contains("espressif") -> return "ESP32/ESP8266"
            v.contains("ring") || v.contains("amazon technologies") -> return "Ring / Amazon"
        }

        return null
    }
}
