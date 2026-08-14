package com.damon.wifiaudit.watchdog

object SurveillanceDeviceWatchdog {

    enum class DeviceCategory(val label: String) {
        CAMERA("Possible Camera"),
        HIDDEN_CAMERA("Hidden Camera / IoT Module"),
        DOORBELL("Possible Doorbell Cam"),
        NVR_DVR("Possible NVR/DVR"),
        STARLINK("Starlink Terminal"),
        VULNERABLE_ROUTER("Vulnerable Router")
    }

    data class Match(val category: DeviceCategory, val matchedOn: String, val severity: Severity = Severity.MEDIUM)

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    // Named-brand vendors — matched against the OUI-resolved manufacturer name.
    // Case-insensitive substring match.
    private val cameraVendorKeywords = listOf(
        "Hikvision", "Dahua", "Ezviz", "Uniview", "Reolink", "Foscam", "Amcrest",
        "Swann", "Lorex", "Wyze", "Arlo", "Eufy", "Anker Innovations", "Vivotek",
        "Geovision", "Annke", "Zmodo", "Axis Communications", "Bosch Security",
        "Honeywell Video", "Yi Technology", "SimCam", "Blink", "Hanwha", "LTS"
    )

    private val hiddenCameraKeywords = listOf(
        "Tuya", "Espressif", "AI-Thinker", "Shenzhen", "Hangzhou", "Broadlink",
        "Sonoff", "XMEye", "iCSee", "V380", "Yoosee", "EseeCloud"
    )

    private val routerVendorKeywords = listOf(
        "TP-Link", "Netgear", "D-Link", "Linksys", "ASUS", "Huawei", "ZTE", "MikroTik", "Ubiquiti", "Cisco"
    )

    private val doorbellVendorKeywords = listOf("Ring LLC", "Amazon Technologies", "Ring.com", "Ring", "Amazon")
    private val doorbellSsidKeywords = listOf("ring_doorbell", "doorbell")

    // Generic-chipset cameras rarely show a helpful OUI vendor, so default
    // SSIDs (set at first boot, often left unchanged) are the more reliable
    // signal for these. Matched against the SSID itself, case-insensitive.
    private val cameraSsidKeywords = listOf(
        "wyze_cam", "ezviz_", "ring_", "amcrest_", "reolink_", "foscam_",
        "yi_cam", "tapo_cam", "ipc-", "dcs-", "hikvision", "dahua_",
        "eufycam", "arlo_", "annke_", "swann_", "cam_", "ipcam", "amazon"
    )

    private val hiddenCameraSsidKeywords = listOf(
        "tuya_", "smartlife_", "esp_", "hichip_", "cam_", "v380", "yoosee"
    )

    private val starlinkVendorKeywords = listOf("SpaceX")
    private val starlinkSsidPattern = Regex("^STARLINK-\\d{4}$", RegexOption.IGNORE_CASE)

    fun classifyWifi(ssid: String, vendorName: String?): Match? {
        val vendor = vendorName ?: ""
        if (starlinkVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            starlinkSsidPattern.matches(ssid)) {
            return Match(DeviceCategory.STARLINK, "SSID/vendor pattern", Severity.LOW)
        }
        if (doorbellVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            doorbellSsidKeywords.any { ssid.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.DOORBELL, "vendor/SSID", Severity.MEDIUM)
        }
        if (cameraVendorKeywords.any { vendor.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "vendor: $vendor", Severity.HIGH)
        }
        if (hiddenCameraKeywords.any { vendor.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.HIDDEN_CAMERA, "Generic IoT vendor: $vendor", Severity.HIGH)
        }
        if (cameraSsidKeywords.any { ssid.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "SSID pattern", Severity.HIGH)
        }
        if (hiddenCameraSsidKeywords.any { ssid.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.HIDDEN_CAMERA, "Generic IoT SSID", Severity.HIGH)
        }
        return null
    }

    fun classifyBle(deviceName: String?, vendorName: String?): Match? {
        val name = deviceName ?: ""
        val vendor = vendorName ?: ""
        if (doorbellVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            name.contains("doorbell", ignoreCase = true)) {
            return Match(DeviceCategory.DOORBELL, "vendor/name", Severity.MEDIUM)
        }
        if (cameraVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            cameraSsidKeywords.any { name.contains(it.trimEnd('_', '-'), ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "vendor/name", Severity.HIGH)
        }
        if (hiddenCameraKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            hiddenCameraSsidKeywords.any { name.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.HIDDEN_CAMERA, "vendor/name", Severity.HIGH)
        }
        return null
    }

    fun analyzeVulnerabilities(vendor: String?, openPorts: List<Int>): List<Match> {
        val matches = mutableListOf<Match>()
        val isRouter = routerVendorKeywords.any { vendor?.contains(it, ignoreCase = true) == true }

        if (23 in openPorts) {
            matches.add(Match(
                if (isRouter) DeviceCategory.VULNERABLE_ROUTER else DeviceCategory.HIDDEN_CAMERA,
                "Exposed Telnet (Port 23)",
                Severity.CRITICAL
            ))
        }
        if (21 in openPorts) {
            matches.add(Match(
                if (isRouter) DeviceCategory.VULNERABLE_ROUTER else DeviceCategory.HIDDEN_CAMERA,
                "Exposed FTP (Port 21)",
                Severity.HIGH
            ))
        }
        if (161 in openPorts) {
            matches.add(Match(DeviceCategory.VULNERABLE_ROUTER, "Exposed SNMP (Port 161)", Severity.HIGH))
        }
        if (445 in openPorts || 139 in openPorts) {
            if (isRouter) {
                matches.add(Match(DeviceCategory.VULNERABLE_ROUTER, "Exposed SMB/NetBIOS", Severity.MEDIUM))
            }
        }
        if (554 in openPorts || 8554 in openPorts) {
            matches.add(Match(DeviceCategory.CAMERA, "Exposed RTSP Stream (Port 554/8554)", Severity.HIGH))
        }

        return matches
    }
}
