package com.damon.wifiaudit.scan

import android.content.Context

/**
 * WiFi Security Audit stubs.
 *
 * WPS PIN testing and WPA handshake capture require monitor mode or root
 * access, which is not available on stock Android devices. These features
 * are documented here with explanations of what's needed to enable them.
 *
 * On a rooted device or one with a compatible USB WiFi adapter, these
 * could be implemented using external tools (e.g., reaver, hcxdumptool).
 */
object WifiSecurityAudit {

    data class AuditCapability(
        val name: String,
        val description: String,
        val isAvailable: Boolean,
        val requirement: String
    )

    /**
     * Returns the list of WiFi security audit capabilities and their
     * availability on this device.
     */
    fun getCapabilities(context: Context): List<AuditCapability> {
        val isRooted = checkRootAccess()

        return listOf(
            AuditCapability(
                name = "WPS PIN Testing (Pixie Dust)",
                description = "Attempt WPS Pixie Dust attack against WPS-enabled access points. " +
                        "This can recover the WPS PIN and subsequently the WPA2 password.",
                isAvailable = isRooted,
                requirement = "Requires root + monitor mode WiFi adapter. " +
                        "On stock Android, WPS operations are handled by the system WiFi stack " +
                        "and cannot be intercepted or attacked programmatically."
            ),
            AuditCapability(
                name = "WPA/WPA2 Handshake Capture",
                description = "Capture the 4-way WPA handshake for offline password cracking. " +
                        "Useful for auditing your own WiFi password strength.",
                isAvailable = isRooted,
                requirement = "Requires root + monitor mode. " +
                        "Handshakes are only visible in monitor mode, which Android does not expose " +
                        "through its standard WiFi API. Use hcxdumptool or aircrack-ng on a rooted " +
                        "device with a compatible USB adapter."
            ),
            AuditCapability(
                name = "WPA3 SAE Analysis",
                description = "Analyze WPA3 SAE (Simultaneous Authentication of Equals) " +
                        "configuration and check for downgrade attacks.",
                isAvailable = false,
                requirement = "Requires monitor mode + specialized tools. " +
                        "WPA3 uses SAE which is resistant to offline dictionary attacks, " +
                        "but downgrade attacks (forcing WPA2 fallback) can still be tested " +
                        "with monitor mode access."
            ),
            AuditCapability(
                name = "WiFi Deauthentication Detection",
                description = "Monitor for 802.11 deauthentication frames that indicate " +
                        "active attacks on your network.",
                isAvailable = isRooted,
                requirement = "Requires root + monitor mode. " +
                        "Deauth frames are management frames only visible in monitor mode."
            ),
            AuditCapability(
                name = "Hidden SSID Discovery",
                description = "Capture probe requests and responses to discover hidden SSIDs. " +
                        "Devices that have connected to hidden networks will periodically " +
                        "send probe requests containing the SSID in plaintext.",
                isAvailable = isRooted,
                requirement = "Requires root + monitor mode. " +
                        "On stock Android, probe requests are handled by the system and " +
                        "not exposed to applications."
            ),
            AuditCapability(
                name = "Port-Based Vulnerability Assessment",
                description = "Passive assessment of open ports on discovered LAN devices. " +
                        "Flags known vulnerable services and recommends fixes.",
                isAvailable = true,
                requirement = "Available now. Uses VulnerabilityDatabase to match open ports " +
                        "against known risk patterns."
            ),
            AuditCapability(
                name = "Default Credential Checking",
                description = "Checks HTTP admin panels for common default credentials. " +
                        "Limited to a small set of known defaults (admin/admin, etc.).",
                isAvailable = true,
                requirement = "Available now. Manually triggered per device via " +
                        "DiscoveryCoordinator.checkDefaultCredentials()."
            ),
            AuditCapability(
                name = "BLE GATT Profiling",
                description = "Connect to BLE devices and enumerate all GATT services, " +
                        "characteristics, and their read/write/notify properties.",
                isAvailable = true,
                requirement = "Available now. User-triggered per device via " +
                        "BleScanManager.profileDevice(macAddress)."
            ),
            AuditCapability(
                name = "BLE Tracker Detection",
                description = "Cross-session analysis of BLE devices to detect tracking " +
                        "beacons (AirTags, Tiles, SmartTags) following you.",
                isAvailable = true,
                requirement = "Available now. Automatically accumulates across scan " +
                        "sessions. View alerts via BleScanManager.getTrackerAlerts()."
            ),
            AuditCapability(
                name = "BLE Spoofing/Cloning Detection",
                description = "Detects BLE device cloning, MAC randomization abuse, " +
                        "iBeacon cloning, and manufacturer mismatches.",
                isAvailable = true,
                requirement = "Available now. Automatically analyzes scan results. " +
                        "View alerts via BleScanManager.getSpoofingAlerts()."
            )
        )
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
