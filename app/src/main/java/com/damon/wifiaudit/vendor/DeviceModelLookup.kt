package com.damon.wifiaudit.vendor

import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo

object DeviceModelLookup {

    /**
     * Identifies specific hardware models based on BLE data (UUIDs, names, MAC prefixes)
     * and Wi-Fi data (SSIDs, MAC prefixes).
     */
    fun identify(ble: BleDeviceInfo): String? {
        // Check for Meshtastic/MeshCore Service UUIDs
        if (ble.serviceUuids.any { it.startsWith("6ba1b210", ignoreCase = true) }) {
            return "Meshtastic Node"
        }

        val name = ble.deviceName ?: ""
        val mac = ble.macAddress.replace(":", "").replace("-", "").uppercase()

        return when {
            name.contains("Heltec", ignoreCase = true) -> "Heltec V3"
            name.contains("LilyGo", ignoreCase = true) -> "LilyGo T-Beam"
            name.contains("RAK", ignoreCase = true) -> "RAK4631"
            name.contains("Meshtastic", ignoreCase = true) -> "Meshtastic Device"
            
            // Known OUI prefixes for Mesh hardware manufacturers
            mac.startsWith("00E04C") -> "Realtek Mesh"
            mac.startsWith("80E127") -> "Heltec Node"
            mac.startsWith("A4CF12") -> "LilyGo/Heltec ESP32"
            mac.startsWith("AC67B2") -> "RAK Wireless"
            
            // Generic Espressif OUIs often used in DIY Mesh nodes
            mac.startsWith("240AC4") || mac.startsWith("246F28") || 
            mac.startsWith("30AEA4") || mac.startsWith("3C71BF") ||
            mac.startsWith("4C11AE") || mac.startsWith("5460E9") ||
            mac.startsWith("AC67B2") || mac.startsWith("C82B96") -> "ESP32 Mesh Candidate"
            
            else -> null
        }
    }

    fun identify(wifi: ScanResult): String? {
        val ssid = wifi.SSID
        val bssid = wifi.BSSID.replace(":", "").replace("-", "").uppercase()

        return when {
            ssid.contains("Meshtastic", ignoreCase = true) -> "Meshtastic WiFi"
            bssid.startsWith("80E127") -> "Heltec Node"
            bssid.startsWith("A4CF12") -> "LilyGo/Heltec ESP32"
            bssid.startsWith("AC67B2") -> "RAK Wireless"
            bssid.startsWith("240AC4") || bssid.startsWith("246F28") -> "ESP32 AP"
            else -> null
        }
    }
}
