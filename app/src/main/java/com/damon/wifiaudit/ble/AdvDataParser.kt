package com.damon.wifiaudit.ble

object AdvDataParser {

    data class AdStructure(
        val length: Int,
        val type: Int,
        val typeName: String,
        val data: ByteArray,
        val hexOffset: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AdStructure) return false
            return length == other.length && type == other.type && data.contentEquals(other.data)
        }
        override fun hashCode(): Int = 31 * (31 * length + type) + data.contentHashCode()
    }

    private val typeNames = mapOf(
        0x01 to "Flags",
        0x02 to "Incomplete 16-bit UUIDs",
        0x03 to "Complete 16-bit UUIDs",
        0x04 to "Incomplete 32-bit UUIDs",
        0x05 to "Complete 32-bit UUIDs",
        0x06 to "Incomplete 128-bit UUIDs",
        0x07 to "Complete 128-bit UUIDs",
        0x08 to "Shortened Local Name",
        0x09 to "Complete Local Name",
        0x0A to "Tx Power Level",
        0x0D to "Class of Device",
        0x0E to "Simple Pairing Hash C",
        0x0F to "Simple Pairing Randomizer R",
        0x10 to "Security Manager TK Value",
        0x11 to "Security Manager OOB Flags",
        0x12 to "Slave Connection Interval Range",
        0x14 to "List of 16-bit Service Solicitation UUIDs",
        0x15 to "List of 128-bit Service Solicitation UUIDs",
        0x16 to "Service Data - 16-bit UUID",
        0x17 to "Public Target Address",
        0x18 to "Random Target Address",
        0x19 to "Appearance",
        0x1A to "Advertising Interval",
        0x1B to "LE Bluetooth Device Address",
        0x1C to "LE Role",
        0x1D to "Simple Pairing Hash C-256",
        0x1E to "Simple Pairing Randomizer R-256",
        0x1F to "List of 32-bit Service Solicitation UUIDs",
        0x20 to "Service Data - 32-bit UUID",
        0x21 to "Service Data - 128-bit UUID",
        0x22 to "LE Secure Connections Confirmation Value",
        0x23 to "LE Secure Connections Random Value",
        0x24 to "URI",
        0x25 to "Indoor Positioning",
        0x26 to "Transport Discovery Data",
        0x27 to "LE Supported Features",
        0x28 to "Channel Map Update Indication",
        0x29 to "PB-ADV",
        0x2A to "Mesh Message",
        0x2B to "Mesh Beacon",
        0x2C to "BIGInfo",
        0x2D to "Broadcast Code",
        0x2E to "Resolvable Set Identifier",
        0x2F to "Advertising Interval - long",
        0x30 to "Broadcast Name",
        0x31 to "Encrypted Advertising Data",
        0x32 to "Periodic Advertising Response Timing Information",
        0x3D to "3D Information Data",
        0xFF to "Manufacturer Specific Data"
    )

    fun parse(bytes: ByteArray?): List<AdStructure> {
        if (bytes == null || bytes.isEmpty()) return emptyList()

        val structures = mutableListOf<AdStructure>()
        var offset = 0

        while (offset < bytes.size) {
            if (offset + 1 >= bytes.size) break

            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) break

            val type = bytes[offset + 1].toInt() and 0xFF
            val dataLength = length - 1

            if (offset + 2 + dataLength > bytes.size) break

            val data = bytes.copyOfRange(offset + 2, offset + 2 + dataLength)

            structures.add(AdStructure(
                length = length,
                type = type,
                typeName = typeNames[type] ?: "Unknown (0x${type.toString(16).padStart(2, '0')})",
                data = data,
                hexOffset = offset
            ))

            offset += 1 + length
        }

        return structures
    }

    fun formatHexDump(bytes: ByteArray?, structures: List<AdStructure> = emptyList()): String {
        if (bytes == null || bytes.isEmpty()) return "No data"

        val sb = StringBuilder()
        val typeColors = mapOf(
            0x01 to "🔵", // Flags
            0x09 to "🟢", // Name
            0xFF to "🟠", // Manufacturer
            0x16 to "🟣", // Service Data
        )

        // Header
        sb.appendLine("OFFSET  HEX DATA                                          ASCII")
        sb.appendLine("------  ------------------------------------------------  ----------------")

        for (i in bytes.indices step 16) {
            // Offset
            sb.append(String.format("%04X    ", i))

            // Hex bytes
            val hexChunk = StringBuilder()
            val asciiChunk = StringBuilder()

            for (j in 0 until 16) {
                val idx = i + j
                if (idx < bytes.size) {
                    val b = bytes[idx]
                    hexChunk.append(String.format("%02X ", b))
                    asciiChunk.append(if (b in 32..126) b.toInt().toChar() else '.')
                } else {
                    hexChunk.append("   ")
                }

                if (j == 7) hexChunk.append(" ")
            }

            sb.append(String.format("%-50s", hexChunk.toString()))
            sb.append("  ")
            sb.append(asciiChunk)
            sb.appendLine()

            // Insert structure separator if this line ends a structure
            val endIdx = i + 15
            if (structures.any { (it.hexOffset + it.length) in i..endIdx }) {
                sb.appendLine()
            }
        }

        // Parsed structures summary
        if (structures.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("PARSED ADVERTISEMENT STRUCTURES:")
            sb.appendLine("─".repeat(60))
            structures.forEach { struct ->
                val icon = typeColors[struct.type] ?: "⚪"
                sb.appendLine("$icon [0x${struct.type.toString(16).padStart(2, '0')}] ${struct.typeName}")
                sb.appendLine("   Length: ${struct.length} bytes | Data: ${struct.data.toHex()}")

                // Type-specific parsing
                when (struct.type) {
                    0x01 -> { // Flags
                        val flags = struct.data.firstOrNull()?.toInt() ?: 0
                        val flagList = mutableListOf<String>()
                        if (flags and 0x01 != 0) flagList.add("LE Limited Discoverable")
                        if (flags and 0x02 != 0) flagList.add("LE General Discoverable")
                        if (flags and 0x04 != 0) flagList.add("BR/EDR Not Supported")
                        if (flags and 0x08 != 0) flagList.add("Simultaneous LE + BR/EDR Controller")
                        if (flags and 0x10 != 0) flagList.add("Simultaneous LE + BR/EDR Host")
                        sb.appendLine("   → ${flagList.joinToString(", ")}")
                    }
                    0x09, 0x08 -> { // Local Name
                        sb.appendLine("   → \"${String(struct.data, Charsets.UTF_8)}\"")
                    }
                    0x0A -> { // Tx Power
                        val power = struct.data.firstOrNull()?.toInt() ?: 0
                        sb.appendLine("   → ${power} dBm")
                    }
                    0xFF -> { // Manufacturer Specific
                        if (struct.data.size >= 2) {
                            val companyId = (struct.data[0].toInt() and 0xFF) or
                                          ((struct.data[1].toInt() and 0xFF) shl 8)
                            sb.appendLine("   → Company ID: 0x${companyId.toString(16).padStart(4, '0')}")
                            if (struct.data.size > 2) {
                                sb.appendLine("   → Payload: ${struct.data.copyOfRange(2, struct.data.size).toHex()}")
                            }
                        }
                    }
                    0x16 -> { // Service Data - 16-bit
                        if (struct.data.size >= 2) {
                            val uuid = (struct.data[0].toInt() and 0xFF) or
                                      ((struct.data[1].toInt() and 0xFF) shl 8)
                            sb.appendLine("   → Service UUID: 0x${uuid.toString(16).padStart(4, '0')}")
                        }
                    }
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
