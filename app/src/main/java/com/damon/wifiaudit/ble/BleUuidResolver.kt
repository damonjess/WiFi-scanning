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
        "FE6E" to "Sharp",
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
        "FE6F" to "Sony Ericsson",
        "FE70" to "Nintendo",
        "FE71" to "Logitech",
        "FE72" to "DJI",
        "FE73" to "Anker",
        "FE74" to "Belkin",
        "FE75" to "Roku",
        "FE76" to "Chromecast",
        "FE77" to "Dropcam",
        "FE78" to "Nest Cam",
        // Additional modern vendor services
        "FE79" to "Google",
        "FE7A" to "Sandisk",
        "FE7B" to "Garmin",
        "FE7C" to "Asahi Kasei",
        "FE7D" to "HMD Global",
        "FE7E" to "Schneider Electric",
        "FE7F" to "Toshiba",
        "FE80" to "Tandem Diabetes Care",
        "FE81" to "Vaisala",
        "FE82" to "BT",
        "FE83" to "Fitbit",
        "FE84" to "Dexcom",
        "FE85" to "Brain Co",
        "FE86" to "Scriptr",
        "FE87" to "Soundmaster",
        "FE88" to "Google Fast Pair",
        "FE8A" to "Google LLC",
        "FE8B" to "Invensense",
        "FE8C" to "Microsoft",
        "FE8D" to "NetEase",
        "FE8E" to "Hewlett-Packard",
        "FE8F" to "Google",
        "FE90" to "MOGO",
        "FE91" to "Air Fuel Alliance",
        "FE92" to "Nike",
        "FE93" to "Motorola Mobility",
        "FE94" to "NXP",
        "FE97" to "Exele Information Systems",
        "FE98" to "Dexcom",
        "FE99" to "Senix Corporation",
        "FE9A" to "Siemens AG",
        "FE9B" to "Robert Bosch",
        "FE9C" to "TTS Tooling Technology",
        "FE9D" to "Inbe Entertainment",
        "FE9E" to "Innophase",
        "FEA0" to "Apple Inc.",
        "FEA2" to "Lenovo",
        "FEA4" to "Richardson RFPD",
        "FEA5" to "ASIGE",
        "FEA6" to "Apogee",
        "FEA7" to "Littelfuse",
        "FEA9" to "Orion Labs",
        "FEAA" to "Eddystone",
        "FEAB" to "Panasonic",
        "FEAC" to "Veggie Grower",
        "FEAD" to "JustNworks",
        "FEAE" to "Vencislav",
        "FEAF" to "AMI",
        "FEB0" to "HP Inc.",
        "FEB1" to "Atos",
        "FEB2" to "Musen Connect",
        "FEB3" to "Pitpat",
        "FEB4" to "Kami (Yi)",
        "FEB5" to "Kubity",
        "FEB6" to "Aclima",
        "FEB7" to "Plume Design",
        "FEB8" to "AAES",
        "FEB9" to "Optiemus",
        "FEBA" to "PayPal",
        "FEBB" to "Inseego",
        "FEBC" to "Janium",
        "FEBD" to "WeeMan",
        "FEBE" to "Toyo IbeTech",
        "FEBF" to "SMT",
        "FEC0" to "Eight Best",
        "FEC1" to "Volans",
        "FEC2" to "Elpro",
        "FEC3" to "Innoseis",
        "FEC4" to "Invacare",
        "FEC5" to "Gooee",
        "FEC6" to "Star Trac",
        "FEC7" to "Vega",
        "FEC8" to "Sensoria",
        "FEC9" to "SpiderLightning",
        "FECA" to "Topre",
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
        "FEDD" to "Lockly",
        "FEDE" to "Braun",
        "FEDF" to "Ursalink",
        "FEE0" to "Tuya Smart",
        "FEE1" to "Huawei",
        "FEE2" to "MOKO",
        "FEE3" to "Toshiba",
        "FEE4" to "Philips",
        "FEE5" to "Leupold",
        "FEE6" to "Dyson",
        "FEE7" to "Tencent",
        "FEE8" to "WeChat",
        "FEE9" to "Xiaomi",
        "FEEA" to "Busch-Jaeger",
        "FEEB" to "Silicon Laboratories",
        "FEEC" to "Juma",
        "FEED" to "Tile Tracker",
        "FEEE" to "Irdeto",
        "FEEF" to "Carestream",
        "FEF0" to "Bose",
        "FEF1" to "Tesla",
        "FEF2" to "VW",
        "FEF3" to "Apple Nearby",
        "FEF4" to "BMW",
        "FEF5" to "Mercedes",
        "FEF6" to "Govee",
        "FEF7" to "Nuki",
        "FEF8" to "Oura",
        "FEF9" to "Wynd",
        "FEFA" to "Samsung SmartThings",
        "FEFB" to "Nest Labs",
        "FEFC" to "TCL",
        "FEFD" to "Hikvision",
        "FEFE" to "Dahua"
    )

    // ============ CHARACTERISTICS ============
    private val characteristics = mapOf(
        // Generic Access
        "2A00" to "Device Name",
        "2A01" to "Appearance",
        "2A02" to "Peripheral Privacy Flag",
        "2A03" to "Reconnection Address",
        "2A04" to "Peripheral Preferred Connection Parameters",
        "2A05" to "Service Changed",
        "2A06" to "Alert Level",
        "2A07" to "Tx Power Level",
        // Time
        "2A08" to "Date Time",
        "2A09" to "Day of Week",
        "2A0A" to "Day Date Time",
        "2A0B" to "Exact Time 100",
        "2A0C" to "Exact Time 256",
        "2A0D" to "DST Offset",
        "2A0E" to "Time Zone",
        "2A0F" to "Local Time Information",
        "2A10" to "Daylight Saving Time",
        "2A11" to "Time Accuracy",
        "2A12" to "Time Source",
        "2A13" to "Reference Time Information",
        "2A14" to "Time Broadcast",
        "2A15" to "Time Update Control Point",
        "2A16" to "Time Update State",
        // Battery
        "2A19" to "Battery Level",
        "2A1A" to "Battery Power State",
        "2A1B" to "Battery Level State",
        // Temperature
        "2A1C" to "Temperature Measurement",
        "2A1D" to "Temperature Type",
        "2A1E" to "Intermediate Temperature",
        "2A1F" to "Temperature Celsius",
        "2A20" to "Temperature Fahrenheit",
        "2A21" to "Measurement Interval",
        // Device Info
        "2A23" to "System ID",
        "2A24" to "Model Number",
        "2A25" to "Serial Number",
        "2A26" to "Firmware Revision",
        "2A27" to "Hardware Revision",
        "2A28" to "Software Revision",
        "2A29" to "Manufacturer Name",
        "2A2A" to "IEEE 11073 Regulatory",
        "2A2B" to "Current Time",
        "2A2C" to "Secondary Time Zone",
        // Alerts
        "2A2E" to "Alert Category ID Bit Mask",
        "2A2F" to "Alert Status",
        "2A30" to "Alert Category ID",
        "2A31" to "Alert Control Point",
        "2A40" to "New Alert",
        "2A41" to "Supported New Alert Category",
        "2A42" to "Supported Unread Alert Category",
        "2A43" to "Alert Category ID",
        "2A44" to "Unread Alert Status",
        "2A45" to "Alert Notification Control Point",
        // Heart Rate
        "2A37" to "Heart Rate Measurement",
        "2A38" to "Body Sensor Location",
        "2A39" to "Heart Rate Control Point",
        // Blood Pressure
        "2A46" to "Blood Pressure Measurement",
        "2A47" to "Intermediate Cuff Pressure",
        "2A48" to "Blood Pressure Feature",
        // HID
        "2A49" to "HID Information",
        "2A4A" to "Report Map",
        "2A4B" to "HID Control Point",
        "2A4C" to "Report",
        "2A4D" to "Protocol Mode",
        "2A4E" to "Boot Keyboard Input Report",
        "2A4F" to "Boot Keyboard Output Report",
        "2A50" to "Boot Mouse Input Report",
        // Running Speed & Cadence
        "2A53" to "RSC Measurement",
        "2A54" to "RSC Feature",
        "2A55" to "SC Control Point",
        // Cycling
        "2A56" to "CSC Measurement",
        "2A57" to "CSC Feature",
        "2A58" to "Sensor Location",
        "2A5C" to "CSC Measurement",
        "2A5D" to "CSC Feature",
        "2A5E" to "Sensor Location",
        "2A5F" to "Cycling Power Measurement",
        "2A60" to "Cycling Power Vector",
        "2A61" to "Cycling Power Feature",
        "2A62" to "Cycling Power Control Point",
        "2A63" to "Cycling Power Measurement",
        "2A64" to "Cycling Power Vector",
        "2A65" to "Cycling Power Feature",
        // Location & Navigation
        "2A66" to "Location and Speed",
        "2A67" to "Location and Speed",
        "2A68" to "Navigation",
        "2A69" to "Position Quality",
        "2A6A" to "LN Feature",
        "2A6B" to "LN Control Point",
        // Environmental Sensing
        "2A6E" to "Temperature",
        "2A6F" to "Humidity",
        "2A70" to "True Wind Speed",
        "2A71" to "True Wind Direction",
        "2A72" to "Apparent Wind Speed",
        "2A73" to "Apparent Wind Direction",
        "2A74" to "Gust Factor",
        "2A75" to "Pollen Concentration",
        "2A76" to "UV Index",
        "2A77" to "Irradiance",
        "2A78" to "Rainfall",
        "2A79" to "Wind Chill",
        "2A7A" to "Heat Index",
        "2A7B" to "Dew Point",
        "2A7C" to "Trend",
        "2A7D" to "Descriptor Value Changed",
        // Body Composition
        "2A87" to "Body Composition Measurement",
        "2A88" to "Body Composition Feature",
        "2A89" to "Weight Measurement",
        "2A8A" to "Weight Feature",
        "2A8B" to "User Control Point",
        "2A8C" to "User Data",
        "2A8D" to "Maximum Recommended Heart Rate",
        "2A8E" to "Language",
        "2A8F" to "Barometric Pressure Trend",
        // Heart Rate Zones
        "2A7E" to "Aerobic Heart Rate Lower Limit",
        "2A7F" to "Aerobic Threshold",
        "2A80" to "Anaerobic Heart Rate Lower Limit",
        "2A81" to "Anaerobic Threshold",
        "2A82" to "Aerobic Heart Rate Upper Limit",
        "2A83" to "Anaerobic Heart Rate Upper Limit",
        "2A84" to "Five Zone Heart Rate Limits",
        "2A85" to "Three Zone Heart Rate Limits",
        "2A86" to "Maximum Heart Rate",
        // Pulse Oximeter
        "2A59" to "PLX Spot-Check Measurement",
        "2A5A" to "PLX Continuous Measurement",
        "2A5B" to "PLX Features",
        // CGM
        "2AA7" to "CGM Measurement",
        "2AA8" to "CGM Measurement",
        "2AA9" to "CGM Feature",
        "2AAA" to "CGM Status",
        "2AAB" to "CGM Session Start Time",
        "2AAC" to "CGM Session Run Time",
        "2AAD" to "CGM Specific Ops Control Point",
        // Indoor Positioning
        "2AAE" to "Indoor Positioning Configuration",
        "2AAF" to "Latitude",
        "2AB0" to "Longitude",
        "2AB1" to "Local North Coordinate",
        "2AB2" to "Local East Coordinate",
        "2AB3" to "Floor Number",
        "2AB4" to "Altitude",
        "2AB5" to "Uncertainty",
        "2AB6" to "Location Name",
        // HTTP Proxy
        "2AB7" to "URI",
        "2AB8" to "HTTP Headers",
        "2AB9" to "HTTP Status Code",
        "2ABA" to "HTTP Entity Body",
        "2ABB" to "HTTP Control Point",
        "2ABC" to "HTTPS Security",
        "2ABD" to "HTTP Proxy",
        // Object Transfer
        "2ABE" to "OTS Feature",
        "2ABF" to "Object Name",
        "2AC0" to "Object Type",
        "2AC1" to "Object Size",
        "2AC2" to "Object First-Created",
        "2AC3" to "Object Last-Modified",
        "2AC4" to "Object ID",
        "2AC5" to "Object Properties",
        "2AC6" to "Object Action Control Point",
        "2AC7" to "Object List Control Point",
        "2AC8" to "Object List Filter",
        "2AC9" to "Object Changed",
        // Privacy & Resolution
        "2AA6" to "Central Address Resolution",
        "2ACA" to "Resolvable Private Address Only",
        // Mesh
        "2ACB" to "Mesh Provisioning Data In",
        "2ACC" to "Mesh Provisioning Data Out",
        "2ACD" to "Mesh Proxy Data In",
        "2ACE" to "Mesh Proxy Data Out",
        // Reconnection
        "2ACF" to "Reconnection Configuration Control Point",
        "2AD0" to "Reconnection Configuration Settings",
        "2AD1" to "Reconnection Configuration Connection Preference",
        "2AD2" to "Reconnection Configuration Optional Features",
        // GATT Enhanced
        "2B05" to "Client Supported Features",
        "2B06" to "Database Hash",
        // DFU
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
        "2909" to "Number of Digitals",
        "290A" to "Value Trigger Setting",
        "290B" to "Environmental Sensing Configuration",
        "290C" to "Environmental Sensing Measurement",
        "290D" to "Environmental Sensing Trigger Setting",
        "290E" to "Time Trigger Setting",
        "290F" to "Classification",
        "2910" to "Sensor Configuration",
        "2911" to "Valid Range Enhanced",
        "2912" to "Characteristic Report Reference",
        "2913" to "HID Report Reference",
        "2914" to "HID Boot Report Reference",
        "2915" to "GATT Service Changed",
        "2916" to "GATT Client Supported Features",
        "2917" to "GATT Database Hash",
        "2918" to "GATT Client Awareness",
        "2919" to "Mesh Provisioning",
        "291A" to "Mesh Proxy",
        "291B" to "Reconnection Configuration",
        "291C" to "Spare"
    )

    // ============ COMPANY IDs (fallback) ============
    // These entries are used if the JSON resource fails to load.
    // The full 4,000+ entry list is loaded from res/raw/ble_company_ids.json
    // via initCompanyIds(). Source: https://github.com/NordicSemiconductor/bluetooth-numbers-database
    private val companyIdsFallback = mapOf(
        0x0000 to "Ericsson AB",
        0x0001 to "Nokia Mobile Phones",
        0x0002 to "Intel Corp.",
        0x0003 to "IBM Corporation",
        0x0004 to "Toshiba Corp.",
        0x0006 to "Microsoft",
        0x000A to "Qualcomm",
        0x000D to "Texas Instruments",
        0x000F to "Broadcom Corporation",
        0x0013 to "Atmel Corporation",
        0x001D to "Qualcomm Technologies",
        0x0025 to "NXP Semiconductors",
        0x002D to "Logitech",
        0x0030 to "ST Microelectronics",
        0x003C to "BlackBerry (RIM)",
        0x0046 to "MediaTek",
        0x004C to "Apple, Inc.",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung Electronics",
        0x0087 to "Garmin International",
        0x008C to "Realtek Semiconductor",
        0x009E to "Bose Corporation",
        0x00B5 to "Sony Corporation",
        0x00D2 to "Dialog Semiconductor",
        0x00E0 to "Google",
        0x00E4 to "Fitbit, Inc.",
        0x0109 to "CSR plc (Qualcomm)",
        0x011B to "Jabra (GN Audio)",
        0x012F to "Sennheiser Electronic",
        0x0131 to "Cypress Semiconductor",
        0x0157 to "Huami (Amazfit)",
        0x0171 to "Anker Innovations",
        0x019A to "SwitchBot",
        0x01A7 to "Oura Health",
        0x01C2 to "Wyze Labs",
        0x01D3 to "Ring Inc.",
        0x0211 to "Tuya Smart",
        0x02D7 to "GoPro, Inc.",
        0x02E1 to "Fitbit, Inc.",
        0x02E5 to "Sony Corporation",
        0x02F2 to "Bose Corporation",
        0x0340 to "Bang & Olufsen",
        0x0350 to "Tile, Inc.",
        0x038E to "Espressif Systems",
        0x03EC to "Huawei Technologies",
        0x03FE to "Xiaomi Inc.",
        0x0461 to "Amazon Technologies",
        0x0498 to "Garmin International",
        0x04C2 to "OnePlus Electronics",
        0x0529 to "Govee Technology",
        0x05A7 to "Philips Lighting",
        0x05C4 to "Ring Inc.",
        0x0609 to "Wyze Labs",
        0x0622 to "Arlo Technologies",
        0x0645 to "Eve Systems",
        0x0658 to "Nanoleaf",
        0x066A to "Sonos",
        0x06E8 to "Meta Platforms",
        0x0776 to "Microsoft Corporation",
        0x07D7 to "Raspberry Pi",
        0x0819 to "Tesla Motors",
        0x08A6 to "Nothing Technology",
        0x0A2B to "DJI Technology",
        0x01C3 to "Nuki Home Solutions",
        0x02A2 to "Ecobee",
        0x02A4 to "Schlage",
        0x030A to "LIFX",
        0x033B to "August Home",
        0x0358 to "JBL",
        0x038F to "Nest Labs",
        0x04C4 to "IKEA of Sweden",
        0x054C to "TP-Link",
        0x0579 to "Reolink",
        0x05A5 to "Hikvision",
        0x05D5 to "Dahua",
        0x06E3 to "Withings",
        0x0719 to "Polar Electro",
        0x0765 to "Whoop",
        0x0828 to "Dyson",
        0x0867 to "Volkswagen",
        0x0876 to "BMW",
        0x0884 to "Mercedes-Benz",
        0x0917 to "Lockly",
        0x0947 to "Chamberlain Group",
        0x0956 to "Shelly",
        0x0964 to "Nintendo",
        0x0982 to "Roku",
        0x0994 to "Ledger",
        0x0A4A to "Synology",
        0x0A5B to "QNAP Systems",
        0x0A6E to "Ubiquiti Networks",
        0x0A7C to "NETGEAR",
        0x0A89 to "ASUS",
        0x0B24 to "Belkin International",
        0x0B5E to "Dropcam",
        0x0B68 to "Raspberry Pi Foundation",
        0x0B85 to "Foscam",
        0x0B9C to "Amcrest Technologies",
        0x0BAE to "MikroTik",
        0x0BB2 to "Lorex Technology",
        0x0BC6 to "Annke Innovation",
        0x0BD0 to "ZOSI Technology"
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
