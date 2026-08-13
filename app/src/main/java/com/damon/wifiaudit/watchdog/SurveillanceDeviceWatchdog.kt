package com.damon.wifiaudit.watchdog

object SurveillanceDeviceWatchdog {

    enum class DeviceCategory(val label: String) {
        CAMERA("Possible Camera"),
        DOORBELL("Possible Doorbell Cam"),
        NVR_DVR("Possible NVR/DVR"),
        STARLINK("Starlink Terminal")
    }

    data class Match(val category: DeviceCategory, val matchedOn: String)

    // Named-brand vendors — matched against the OUI-resolved manufacturer name.
    // Case-insensitive substring match.
    private val cameraVendorKeywords = listOf(
        "Hikvision", "Dahua", "Ezviz", "Uniview", "Reolink", "Foscam", "Amcrest",
        "Swann", "Lorex", "Wyze", "Arlo", "Eufy", "Anker Innovations", "Vivotek",
        "Geovision", "Annke", "Zmodo", "Axis Communications", "Bosch Security",
        "Honeywell Video", "Yi Technology", "SimCam", "Blink"
    )

    private val doorbellVendorKeywords = listOf("Ring LLC", "Amazon Technologies")

    // Generic-chipset cameras rarely show a helpful OUI vendor, so default
    // SSIDs (set at first boot, often left unchanged) are the more reliable
    // signal for these. Matched against the SSID itself, case-insensitive.
    private val cameraSsidKeywords = listOf(
        "wyze_cam", "ezviz_", "ring_", "amcrest_", "reolink_", "foscam_",
        "yi_cam", "tapo_cam", "ipc-", "dcs-", "hikvision", "dahua_",
        "eufycam", "arlo_", "annke_", "swann_"
    )

    private val doorbellSsidKeywords = listOf("ring_doorbell", "doorbell")

    private val starlinkVendorKeywords = listOf("SpaceX")
    private val starlinkSsidPattern = Regex("^STARLINK-\\d{4}$", RegexOption.IGNORE_CASE)

    fun classifyWifi(ssid: String, vendorName: String?): Match? {
        val vendor = vendorName ?: ""
        if (starlinkVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            starlinkSsidPattern.matches(ssid)) {
            return Match(DeviceCategory.STARLINK, "SSID/vendor pattern")
        }
        if (doorbellVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            doorbellSsidKeywords.any { ssid.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.DOORBELL, "vendor/SSID")
        }
        if (cameraVendorKeywords.any { vendor.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "vendor: $vendor")
        }
        if (cameraSsidKeywords.any { ssid.contains(it, ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "SSID pattern")
        }
        return null
    }

    fun classifyBle(deviceName: String?, vendorName: String?): Match? {
        val name = deviceName ?: ""
        val vendor = vendorName ?: ""
        if (doorbellVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            name.contains("doorbell", ignoreCase = true)) {
            return Match(DeviceCategory.DOORBELL, "vendor/name")
        }
        if (cameraVendorKeywords.any { vendor.contains(it, ignoreCase = true) } ||
            cameraSsidKeywords.any { name.contains(it.trimEnd('_', '-'), ignoreCase = true) }) {
            return Match(DeviceCategory.CAMERA, "vendor/name")
        }
        return null
    }
}
