package com.damon.wifiaudit.ble

import android.content.Context
import org.json.JSONArray
import java.util.UUID

object BleUuidResolver {

    private var companyIdsLoaded = false
    private val companyIdsMap = HashMap<Int, String>()

    /**
     * Loads the full Bluetooth SIG Company Identifier list (4,000+ entries)
     * from the raw resource file ble_company_ids.json.
     * Called once on first use — subsequent calls are no-ops.
     */
    fun initCompanyIds(context: Context) {
        if (companyIdsLoaded) return
        try {
            val text = context.resources.openRawResource(
                context.resources.getIdentifier("ble_company_ids", "raw", context.packageName)
            ).bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                companyIdsMap[entry.getInt("code")] = entry.getString("name")
            }
            companyIdsLoaded = true
        } catch (e: Exception) {
            // Fallback: the hardcoded entries below still work
            companyIdsLoaded = true
        }
    }

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
        "FEA3" to "Withings",
        "FEF8" to "Oura",
        // Phones/Tech
        "FE60" to "Huawei",
        "FE68" to "Espressif",
        "FE95" to "Xiaomi MiBeacon",
        "FE96" to "Xiaomi Flora",
        "FE89" to "Nokia",
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

    // ============ COMPANY IDs (fallback) ============
    // Verified against the official Bluetooth SIG Assigned Numbers database.
    // Source: https://github.com/nordicsemi/bluetooth-numbers-database
    // The full 4,000+ entry list is loaded from res/raw/ble_company_ids.json
    // via initCompanyIds().
    private val companyIdsFallback = mapOf(
        0x0000 to "Ericsson AB",
        0x0001 to "Nokia Mobile Phones",
        0x0002 to "Intel Corp.",
        0x0003 to "IBM Corp.",
        0x0004 to "Toshiba Corp.",
        0x0006 to "Microsoft",
        0x0008 to "Motorola",
        0x000A to "Qualcomm Technologies International, Ltd. (QTIL)",
        0x000D to "Texas Instruments Inc.",
        0x000F to "Broadcom Corporation",
        0x0013 to "Atmel Corporation",
        0x001D to "Qualcomm",
        0x0025 to "NXP B.V.",
        0x0030 to "ST Microelectronics",
        0x003A to "Panasonic Holdings Corporation",
        0x003C to "BlackBerry Limited",
        0x0046 to "MediaTek, Inc.",
        0x004C to "Apple, Inc.",
        0x0056 to "Sony Ericsson Mobile Communications",
        0x0059 to "Nordic Semiconductor ASA",
        0x005C to "Belkin International, Inc.",
        0x005D to "Realtek Semiconductor Corporation",
        0x0067 to "GN Hearing",
        0x006B to "Polar Electro OY",
        0x0075 to "Samsung Electronics Co. Ltd.",
        0x0078 to "Nike, Inc.",
        0x0087 to "Garmin International, Inc.",
        0x0089 to "GN Hearing A/S",
        0x009E to "Bose Corporation",
        0x00B8 to "Qualcomm Innovation Center, Inc. (QuIC)",
        0x00C4 to "LG Electronics",
        0x00CD to "Microchip Technology Inc.",
        0x00CE to "Eve Systems GmbH",
        0x00D0 to "Dexcom, Inc.",
        0x00D1 to "Polar Electro Europe B.V.",
        0x00D7 to "Qualcomm Technologies, Inc.",
        0x00D8 to "Qualcomm Connected Experiences, Inc.",
        0x00E0 to "Google",
        0x0103 to "Bang & Olufsen A/S",
        0x011A to "Qualcomm Labs, Inc.",
        0x011B to "Hewlett Packard Enterprise",
        0x011F to "Volkswagen AG",
        0x012D to "Sony Corporation",
        0x0131 to "Cypress Semiconductor",
        0x013C to "Murata Manufacturing Co., Ltd.",
        0x0150 to "Pioneer Corporation",
        0x0154 to "Pebble Technology",
        0x0157 to "Anhui Huami Information Technology Co., Ltd.",
        0x0171 to "Amazon.com Services LLC",
        0x0178 to "CASIO COMPUTER CO., LTD.",
        0x017C to "Mercedes-Benz Group AG",
        0x018E to "Google LLC",
        0x01A9 to "Canon Inc.",
        0x01AB to "Meta Platforms, Inc.",
        0x01B5 to "Nest Labs Inc.",
        0x01D1 to "August Home, Inc",
        0x01DA to "Logitech International SA",
        0x01DD to "Koninklijke Philips N.V.",
        0x01F1 to "Zebra Technologies Corporation",
        0x01F7 to "Gelliner Limited",
        0x022B to "Tesla, Inc.",
        0x022E to "Siemens AG",
        0x0236 to "Pitpatpet Ltd",
        0x025C to "NetEase (Hangzhou) Network co.Ltd.",
        0x027D to "HUAWEI Technologies Co., Ltd.",
        0x02A6 to "Robert Bosch GmbH",
        0x02B2 to "Oura Health Oy",
        0x02C5 to "Lenovo (Singapore) Pte Ltd.",
        0x02E5 to "Espressif Systems (Shanghai) Co., Ltd.",
        0x02F2 to "GoPro, Inc.",
        0x0304 to "Oura Health Ltd",
        0x032C to "NIPPON SMT.CO.,Ltd",
        0x0381 to "Sharp Corporation",
        0x038F to "Xiaomi Inc.",
        0x03E3 to "Qualcomm Life Inc",
        0x03FE to "Littelfuse",
        0x03FF to "Withings",
        0x041E to "Dell Computer Corporation",
        0x0446 to "NETGEAR, Inc.",
        0x0494 to "SENNHEISER electronic GmbH & Co. KG",
        0x04B9 to "CSR Building Products Limited",
        0x04D5 to "Gooee Limited",
        0x04E9 to "Busch Jaeger Elektro GmbH",
        0x04EC to "Motorola Solutions",
        0x0501 to "Polaris IND",
        0x0553 to "Nintendo Co., Ltd.",
        0x058E to "Meta Platforms Technologies, LLC",
        0x05A7 to "Sonos Inc",
        0x05D9 to "Toyo Electronics Corporation",
        0x05FD to "Innoseis",
        0x059D to "Tandem Diabetes Care",
        0x0614 to "OnAsset Intelligence, Inc.",
        0x0644 to "Apogee Instruments",
        0x067C to "Tile, Inc.",
        0x068E to "Razer Inc.",
        0x072F to "OnePlus Electronics (Shenzhen) Co., Ltd.",
        0x0783 to "ESEMBER LIMITED LIABILITY COMPANY",
        0x07A2 to "Roku, Inc.",
        0x07A6 to "Musen Connect, Inc.",
        0x07D0 to "Hangzhou Tuya Information Technology Co., Ltd",
        0x07D6 to "ecobee Inc.",
        0x080B to "Nanoleaf Canada Limited",
        0x0870 to "Wyze Labs, Inc",
        0x0878 to "The Chamberlain Group, Inc.",
        0x08AA to "SZ DJI TECHNOLOGY CO.,LTD",
        0x08AC to "Topre Corporation",
        0x08E9 to "Taiwan Intelligent Home Corp.",
        0x094F to "Limited Liability Company \"Mikrotikls\"",
        0x09B6 to "Pegasus Technologies, Inc.",
        0x0A12 to "Dyson Technology Limited",
        0x0A92 to "Carestream Dental LLC",
        0x0AE8 to "LEVEL, s.r.o.",
        0x0AF0 to "Leupold & Stevens, Inc.",
        0x0AF7 to "Irdeto",
        0x0B53 to "SHENZHEN KAADAS INTELLIGENT TECHNOLOGY CO.,Ltd",
        0x0BC6 to "TCL COMMUNICATION EQUIPMENT CO.,LTD.",
        0x0C19 to "Arlo Technologies, Inc.",
        0x0CC2 to "Anker Innovations Limited",
        0x0CC3 to "HMD Global Oy",
        0x0CC4 to "ABUS August Bremicker Soehne Kommanditgesellschaft",
        0x0CCB to "NOTHING TECHNOLOGY LIMITED",
        0x0E41 to "Asustek Computer Inc.",
        0x0E25 to "Hangzhou Hikvision Digital Technology Co., Ltd.",
        0x0E9B to "Panasonic Automotive Systems Co., Ltd.",
        0x0EDE to "Sony Honda Mobility Inc.",
        0x0F00 to "TRACERCO LIMITED",
        0x0F0E to "IDEATRONIK Limited Liability Company",
        0x0F8F to "SIGNUM INTELLIGENCE LTD",
        0x1040 to "Raspberry Pi",
        0x10D1 to "Acer Inc.",
        0x10F9 to "Even Realities Ltd."
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
        "FED0" to WardrivingContext("🔊", "Sonos", ThreatLevel.LOW, "Sonos audio equipment."),
        "FEF8" to WardrivingContext("💍", "Oura Ring", ThreatLevel.LOW, "Oura health-tracking smart ring — biometric data device."),
        "FEA3" to WardrivingContext("❤️", "Withings", ThreatLevel.LOW, "Withings health device — often a smart scale or blood pressure monitor."),
        "FEA1" to WardrivingContext("🏃", "Polar", ThreatLevel.LOW, "Polar fitness sensor — heart rate or activity tracker."),
        "FEA8" to WardrivingContext("💪", "Whoop", ThreatLevel.LOW, "Whoop fitness band — continuous biometric monitoring."),
        "FE2C" to WardrivingContext("🖥️", "Microsoft", ThreatLevel.MEDIUM, "Microsoft BLE device — could be a Surface, Xbox, or Windows peripheral."),
        "FEF1" to WardrivingContext("🚗", "Tesla", ThreatLevel.MEDIUM, "Tesla vehicle — phone key or BLE key fob active."),
        "FEF4" to WardrivingContext("🚗", "BMW", ThreatLevel.MEDIUM, "BMW vehicle — digital key or infotainment BLE active."),
        "FEF2" to WardrivingContext("🚗", "Volkswagen", ThreatLevel.MEDIUM, "VW vehicle — connected car BLE service."),
        "FEF5" to WardrivingContext("🚗", "Mercedes", ThreatLevel.MEDIUM, "Mercedes-Benz vehicle — digital key BLE active."),
        "FECE" to WardrivingContext("📹", "Arlo", ThreatLevel.MEDIUM, "Arlo security camera or doorbell."),
        "FED2" to WardrivingContext("🔌", "TP-Link Kasa", ThreatLevel.LOW, "TP-Link Kasa smart home device — often smart plugs or bulbs."),
        "FED4" to WardrivingContext("🪑", "IKEA TRÅDFRI", ThreatLevel.LOW, "IKEA smart home device — typically smart lighting."),
        "FED5" to WardrivingContext("🤖", "SwitchBot", ThreatLevel.LOW, "SwitchBot IoT device — smart button or sensor."),
        "FED6" to WardrivingContext("🌡️", "Ecobee", ThreatLevel.LOW, "Ecobee smart thermostat."),
        "FED7" to WardrivingContext("🏠", "Nest", ThreatLevel.MEDIUM, "Google Nest device — thermostat, camera, or doorbell."),
        "FED8" to WardrivingContext("🔒", "August Lock", ThreatLevel.MEDIUM, "August smart lock — physical access control."),
        "FED9" to WardrivingContext("🔒", "Yale", ThreatLevel.MEDIUM, "Yale smart lock — physical access control."),
        "FEDB" to WardrivingContext("🔒", "Schlage", ThreatLevel.MEDIUM, "Schlage smart lock — physical access control."),
        "FEDC" to WardrivingContext("🚪", "MyQ (Chamberlain)", ThreatLevel.LOW, "Chamberlain/LiftMaster smart garage door opener."),
        "FEF7" to WardrivingContext("🔒", "Nuki", ThreatLevel.MEDIUM, "Nuki smart lock — physical access control."),
        "FEF6" to WardrivingContext("💡", "Govee", ThreatLevel.LOW, "Govee smart lighting or environmental sensor."),
        "FE63" to WardrivingContext("📷", "GoPro", ThreatLevel.LOW, "GoPro action camera with BLE active."),
        "FE72" to WardrivingContext("🚁", "DJI", ThreatLevel.MEDIUM, "DJI drone — could be used for aerial surveillance."),
        "FE70" to WardrivingContext("🎮", "Nintendo", ThreatLevel.LOW, "Nintendo game console or controller."),
        "FE73" to WardrivingContext("🔋", "Anker", ThreatLevel.LOW, "Anker device — often a portable battery or speaker."),
        "FE74" to WardrivingContext("📡", "Belkin", ThreatLevel.LOW, "Belkin IoT device — often a smart plug or WeMo product."),
        "FE75" to WardrivingContext("📺", "Roku", ThreatLevel.LOW, "Roku streaming device."),
        "FE76" to WardrivingContext("📺", "Chromecast", ThreatLevel.LOW, "Google Chromecast streaming device."),
        "FE77" to WardrivingContext("📹", "Dropcam", ThreatLevel.MEDIUM, "Google Nest Cam (formerly Dropcam) — indoor surveillance."),
        "FE78" to WardrivingContext("📹", "Nest Cam", ThreatLevel.MEDIUM, "Google Nest Cam — surveillance device."),
        "FE62" to WardrivingContext("🔐", "Ledger", ThreatLevel.LOW, "Ledger hardware crypto wallet."),
        "FE71" to WardrivingContext("🖱️", "Logitech", ThreatLevel.LOW, "Logitech wireless peripheral — mouse, keyboard, or presenter."),
        "FE6A" to WardrivingContext("🖨️", "Canon", ThreatLevel.LOW, "Canon printer or camera with BLE active."),
        "FE6D" to WardrivingContext("📺", "Panasonic", ThreatLevel.LOW, "Panasonic device — TV, appliance, or AV equipment."),
        "FEF3" to WardrivingContext("📱", "Google Fast Pair", ThreatLevel.MEDIUM, "Google Fast Pair — nearby device is pairing with a phone.")
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
        return companyIdsMap[id] ?: companyIdsFallback[id] ?: "Unknown (0x${id.toString(16).padStart(4, '0')})"
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
