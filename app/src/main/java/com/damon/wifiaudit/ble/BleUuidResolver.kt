package com.damon.wifiaudit.ble

import java.util.UUID

object BleUuidResolver {

    // ============ SERVICES ============
    private val services = mapOf(
        // Generic
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "180A" to "Device Information",
        "180F" to "Battery Service",
        "1812" to "Human Interface Device",
        "180D" to "Heart Rate",
        "180E" to "Phone Alert Status",
        "1808" to "Glucose",
        "1810" to "Blood Pressure",
        "1811" to "Alert Notification",
        "1813" to "Scan Parameters",
        "1814" to "Running Speed & Cadence",
        "1815" to "Automation IO",
        "1816" to "Cycling Speed & Cadence",
        "1818" to "Cycling Power",
        "1819" to "Location & Navigation",
        "181A" to "Environmental Sensing",
        "181B" to "Body Composition",
        "181C" to "User Data",
        "181D" to "Weight Scale",
        "181E" to "Bond Management",
        "181F" to "Continuous Glucose Monitoring",
        "1820" to "Internet Protocol Support",
        "1821" to "Indoor Positioning",
        "1822" to "Pulse Oximeter",
        "1823" to "HTTP Proxy",
        "1824" to "Transport Discovery",
        "1825" to "Object Transfer",
        "1826" to "Fitness Machine",
        "1827" to "Mesh Provisioning",
        "1828" to "Mesh Proxy",
        "1829" to "Reconnection Configuration",
        "183A" to "Mesh Proxy Solicitation",
        "183B" to "Binary Sensor",
        "183C" to "Emergency Configuration",
        // Apple
        "FD43" to "Apple HomeKit",
        "FD44" to "Apple HomeKit",
        "FD4D" to "Apple HomeKit",
        "FE9F" to "Apple/Google Nearby",
        // Google
        "FEAA" to "Eddystone",
        "FD6F" to "Exposure Notification",
        "FEF3" to "Google Fast Pair",
        // Microsoft
        "FE2C" to "Microsoft",
        // Samsung
        "FD5A" to "Samsung",
        "FD5B" to "Samsung",
        "FD5F" to "Samsung SmartThings",
        // Tile
        "FEED" to "Tile Tracker",
        // Nordic
        "FE59" to "Nordic DFU",
        // Amazon
        "FE58" to "Amazon",
        "FE61" to "Amazon Sidewalk",
        // Audio
        "FE50" to "Sony",
        "FE55" to "Bose",
        "FE56" to "CSR / Qualcomm",
        "FE2E" to "Bose",
        "FE3C" to "JBL",
        "FE4D" to "Sennheiser",
        "FE5A" to "Bang & Olufsen",
        // Fitness
        "FE4B" to "Fitbit",
        "FE48" to "Garmin",
        "FEA1" to "Polar",
        "FEA8" to "Whoop",
        "FEAA" to "Withings",
        // Phones/Tech
        "FE60" to "Huawei",
        "FE61" to "Xiaomi",
        "FE68" to "Espressif",
        "FE95" to "Xiaomi MiBeacon",
        "FE96" to "Xiaomi Flora",
        // Smart Home
        "FE0F" to "Philips Hue",
        "FE13" to "Philips Lighting",
        "FECB" to "Ring",
        "FECC" to "Wyze",
        "FECD" to "Eve Systems",
        "FECE" to "Arlo",
        "FECF" to "Nanoleaf",
        "FED0" to "Sonos",
        "FED1" to "LIFX",
        "FED2" to "TP-Link Kasa",
        "FED3" to "Wiz",
        "FED4" to "IKEA TRÅDFRI",
        "FED5" to "SwitchBot",
        "FED6" to "Ecobee",
        "FED7" to "Nest",
        "FED8" to "August Lock",
        "FED9" to "Yale",
        "FEDA" to "Level Lock",
        "FEDB" to "Schlage",
        "FEDC" to "MyQ (Chamberlain)",
        "FEE0" to "Tuya Smart",
        "FEE7" to "Tencent",
        "FEE8" to "WeChat",
        "FEE9" to "Xiaomi",
        "FEF6" to "Govee",
        "FEF7" to "Nuki",
        "FEF8" to "Aufit",
        // Automotive
        "FEF1" to "Tesla",
        "FEF2" to "VW",
        "FEF4" to "BMW",
        "FEF5" to "Mercedes",
        // Other
        "FE4C" to "Swatch",
        "FE51" to "Casio",
        "FE52" to "Pebble",
        "FE53" to "Qualcomm AllPlay",
        "FE54" to "Qualcomm",
        "FE57" to "Zebra",
        "FE62" to "Ledger",
        "FE63" to "GoPro",
        "FE64" to "Nike",
        "FE65" to "Pioneer",
        "FE66" to "SiriusXM",
        "FE67" to "Line",
        "FE69" to "Toshiba",
        "FE6A" to "Canon",
        "FE6B" to "Fujitsu",
        "FE6C" to "NEC",
        "FE6D" to "Panasonic",
        "FE6E" to "Sharp",
        "FE6F" to "Sony Ericsson",
        "FE70" to "Nintendo",
        "FE71" to "Logitech",
        "FE72" to "DJI",
        "FE73" to "Anker",
        "FE74" to "Belkin",
        "FE75" to "Roku",
        "FE76" to "Chromecast",
        "FE77" to "Dropcam",
        "FE78" to "Nest Cam"
    )

    // ============ CHARACTERISTICS ============
    private val characteristics = mapOf(
        "2A00" to "Device Name",
        "2A01" to "Appearance",
        "2A02" to "Peripheral Privacy Flag",
        "2A03" to "Reconnection Address",
        "2A04" to "Peripheral Preferred Connection Parameters",
        "2AA6" to "Central Address Resolution",
        "2AC9" to "Resolvable Private Address Only",
        "2A29" to "Manufacturer Name",
        "2A24" to "Model Number",
        "2A25" to "Serial Number",
        "2A26" to "Firmware Revision",
        "2A27" to "Hardware Revision",
        "2A28" to "Software Revision",
        "2A23" to "System ID",
        "2A2A" to "IEEE 11073 Regulatory",
        "2A50" to "PnP ID",
        "2A19" to "Battery Level",
        "2A1A" to "Battery Power State",
        "2A1B" to "Battery Level State",
        "2A37" to "Heart Rate Measurement",
        "2A38" to "Body Sensor Location",
        "2A39" to "Heart Rate Control Point",
        "2A4E" to "Protocol Mode",
        "2A4D" to "Report",
        "2A4B" to "Report Map",
        "2A4C" to "Boot Keyboard Input",
        "2A4A" to "HID Information",
        "2A4F" to "Boot Keyboard Output",
        "2A50" to "Boot Mouse Input",
        "2A53" to "RSC Measurement",
        "2A54" to "RSC Feature",
        "2A5C" to "CSC Measurement",
        "2A5D" to "CSC Feature",
        "2A63" to "Cycling Power Measurement",
        "2A65" to "Cycling Power Feature",
        "2A6E" to "Temperature",
        "2A6F" to "Humidity",
        "2A76" to "UV Index",
        "2A77" to "Irradiance",
        "2A7A" to "Wind Chill",
        "2A67" to "Location & Speed",
        "2A68" to "Navigation",
        "2A69" to "Position Quality",
        "2A6A" to "LN Feature",
        "2A05" to "Service Changed",
        "2A0F" to "Local Time Information",
        "2A10" to "Daylight Saving Time",
        "2A11" to "Time Accuracy",
        "2A12" to "Time Source",
        "2A13" to "Reference Time Information",
        "2A08" to "Date Time",
        "2A09" to "Day of Week",
        "2A0A" to "Exact Time 256",
        "2A0C" to "Exact Time 100",
        "2A2F" to "Alert Status",
        "2A3F" to "Unread Alert Status",
        "2A40" to "New Alert",
        "2A41" to "Supported New Alert Category",
        "2A42" to "Supported Unread Alert Category",
        "2A45" to "Alert Notification Control Point",
        "8EC90001" to "DFU Control",
        "8EC90002" to "DFU Packet"
    )

    // ============ DESCRIPTORS ============
    private val descriptors = mapOf(
        "2900" to "Characteristic Extended Properties",
        "2901" to "Characteristic User Description",
        "2902" to "Client Characteristic Configuration",
        "2903" to "Server Characteristic Configuration",
        "2904" to "Characteristic Presentation Format",
        "2905" to "Characteristic Aggregate Format",
        "2906" to "Valid Range",
        "2907" to "External Report Reference",
        "2908" to "Report Reference",
        "290B" to "Environmental Sensing Configuration",
        "290C" to "Environmental Sensing Measurement",
        "290D" to "Environmental Sensing Trigger Setting"
    )

    // ============ COMPANY IDs ============
    private val companyIds = mapOf(
        0x004C to "Apple, Inc.",
        0x000F to "Broadcom Corporation",
        0x0002 to "Intel Corp.",
        0x000A to "Qualcomm",
        0x000D to "Texas Instruments",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung Electronics",
        0x00E0 to "Google",
        0x0109 to "CSR plc",
        0x0131 to "Cypress Semiconductor",
        0x0157 to "Anhui Huami",
        0x02E1 to "Fitbit, Inc.",
        0x02E5 to "Sony Corporation",
        0x02F2 to "Bose Corporation",
        0x0350 to "Tile, Inc.",
        0x038E to "Espressif Systems",
        0x03EC to "Huawei Technologies",
        0x03FE to "Xiaomi Inc.",
        0x0461 to "Amazon Technologies",
        0x0498 to "Garmin International",
        0x05A7 to "Philips Lighting",
        0x05C4 to "Ring Inc.",
        0x0609 to "Wyze Labs",
        0x0622 to "Arlo Technologies",
        0x0645 to "Eve Systems",
        0x0658 to "Nanoleaf",
        0x066A to "Sonos",
        0x06E8 to "Meta Platforms",
        0x0776 to "Microsoft Corporation",
        0x07D7 to "Raspberry Pi"
    )

    // ============ WARDRIVING CONTEXT ============
    data class WardrivingContext(
        val icon: String,
        val label: String,
        val threatLevel: ThreatLevel,
        val description: String
    )

    enum class ThreatLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }

    private val serviceContext = mapOf(
        "180F" to WardrivingContext("🔋", "Battery Level", ThreatLevel.LOW, "Pinpoints mobile/IoT assets needing maintenance or indicates a dynamic tracker."),
        "180A" to WardrivingContext("ℹ️", "Device Information", ThreatLevel.LOW, "Exposes exact firmware versions and hardware models for vulnerability mapping."),
        "1800" to WardrivingContext("🏷️", "Generic Access", ThreatLevel.NONE, "Holds the actual user-configured network name."),
        "FEAA" to WardrivingContext("📡", "Eddystone", ThreatLevel.MEDIUM, "Google beacon protocol — often used for indoor tracking and marketing."),
        "FD6F" to WardrivingContext("📍", "Exposure/Tracking Beacon", ThreatLevel.HIGH, "Identifies nearby mobile smartphones broadcasting constant telemetry."),
        "FE9F" to WardrivingContext("📱", "Apple/Google Nearby", ThreatLevel.MEDIUM, "Proximity-based service discovery — potential tracking vector."),
        "1812" to WardrivingContext("⌨️", "HID (Keyboard/Mouse)", ThreatLevel.MEDIUM, "Human interface device — could be a wireless keylogger or input injector."),
        "FE59" to WardrivingContext("🔄", "Nordic DFU", ThreatLevel.HIGH, "Device Firmware Update service active — device may be in bootloader mode."),
        "FEED" to WardrivingContext("🔷", "Tile Tracker", ThreatLevel.HIGH, "Bluetooth tracker beacon — commonly used for stalking and asset tracking."),
        "FE68" to WardrivingContext("🛠️", "Espressif", ThreatLevel.MEDIUM, "ESP32/ESP8266 device — often IoT, sometimes with default credentials."),
        "FEE0" to WardrivingContext("🏠", "Tuya Smart", ThreatLevel.MEDIUM, "Tuya-based IoT device — extremely common generic smart home hardware."),
        "FEF6" to WardrivingContext("💡", "Govee", ThreatLevel.LOW, "Govee smart lighting or environmental sensor."),
        "FECC" to WardrivingContext("📹", "Wyze", ThreatLevel.MEDIUM, "Wyze smart home device — often cameras or sensors."),
        "FECB" to WardrivingContext("🔔", "Ring", ThreatLevel.MEDIUM, "Ring doorbell or security device."),
        "FED0" to WardrivingContext("🔊", "Sonos", ThreatLevel.LOW, "Sonos audio equipment.")
    )

    private val charContext = mapOf(
        "2A19" to WardrivingContext("🔋", "Battery Level", ThreatLevel.LOW, "Current charge percentage. Helps estimate how long a tracker has been deployed."),
        "2A29" to WardrivingContext("🏭", "Manufacturer", ThreatLevel.NONE, "OEM string — useful for device fingerprinting."),
        "2A24" to WardrivingContext("🔢", "Model Number", ThreatLevel.NONE, "Hardware revision — cross-reference with known vulnerabilities."),
        "2A26" to WardrivingContext("🔄", "Firmware", ThreatLevel.LOW, "Exact firmware version — check against CVE databases."),
        "2A00" to WardrivingContext("🏷️", "Device Name", ThreatLevel.LOW, "Broadcast device name — often contains owner info or model hints.")
    )

    // ============ API ============
    
    fun serviceName(uuid: UUID): String {
        val short = shortUuid(uuid)
        return services[short] ?: "Unknown Service"
    }

    fun characteristicName(uuid: UUID): String {
        val short = shortUuid(uuid)
        return characteristics[short] ?: "Unknown Characteristic"
    }

    fun descriptorName(uuid: UUID): String {
        val short = shortUuid(uuid)
        return descriptors[short] ?: "Unknown Descriptor"
    }

    fun companyName(id: Int): String {
        return companyIds[id] ?: "Unknown (0x${id.toString(16).padStart(4, '0')})"
    }

    fun serviceContext(uuid: UUID): WardrivingContext? {
        return serviceContext[shortUuid(uuid)]
    }

    fun characteristicContext(uuid: UUID): WardrivingContext? {
        return charContext[shortUuid(uuid)]
    }

    fun isStandardUuid(uuid: UUID): Boolean {
        val s = uuid.toString().lowercase()
        return s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")
    }

    fun shortUuid(uuid: UUID): String {
        val s = uuid.toString().uppercase()
        return if (isStandardUuid(uuid)) {
            s.substring(4, 8)
        } else {
            s.take(8)
        }
    }

    fun fullShortForm(uuid: UUID): String {
        return "0x${shortUuid(uuid)}"
    }
}
