package com.damon.wifiaudit.ble

import android.bluetooth.le.ScanRecord
import android.util.SparseArray
import java.util.UUID

data class ParsedAdvertisement(
    val flags: Int? = null,
    val localName: String? = null,
    val txPowerLevel: Int? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val isConnectable: Boolean = false,
    val rawHex: String = ""
)

object AdvertisementParser {
    fun parse(bytes: ByteArray?): ParsedAdvertisement {
        if (bytes == null) return ParsedAdvertisement()

        val record: ScanRecord? = try {
            val method = ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
            method.isAccessible = true
            method.invoke(null, bytes) as? ScanRecord
        } catch (e: Exception) {
            null
        }

        if (record == null) return ParsedAdvertisement(rawHex = bytes.joinToString(" ") { "%02X".format(it) })

        val flags = try { record.advertiseFlags } catch (e: NoSuchMethodError) { -1 }.let { if (it != -1) it else null }
        val connectable = flags?.let { (it.toInt() and 0x02) != 0 } ?: false

        // Manufacturer data
        val mfg = mutableMapOf<Int, ByteArray>()
        try {
            record.manufacturerSpecificData?.let { sparse ->
                for (i in 0 until sparse.size()) {
                    val key = sparse.keyAt(i)
                    sparse.get(key)?.let { data -> mfg[key] = data }
                }
            }
        } catch (e: Exception) {}

        // Service data
        val svcData = mutableMapOf<UUID, ByteArray>()
        try {
            record.serviceData?.forEach { (parcelUuid, data) ->
                svcData[parcelUuid.uuid] = data
            }
        } catch (e: Exception) {}

        // Service UUIDs
        val uuids = try {
            record.serviceUuids?.map { it.uuid } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val rawHex = bytes.joinToString(" ") { "%02X".format(it) }

        return ParsedAdvertisement(
            flags = flags,
            localName = try { record.deviceName } catch (e: Exception) { null },
            txPowerLevel = try { record.txPowerLevel } catch (e: Exception) { null },
            serviceUuids = uuids,
            manufacturerData = mfg,
            serviceData = svcData,
            isConnectable = connectable,
            rawHex = rawHex
        )
    }

    fun formatFlags(flags: Int): List<String> {
        val list = mutableListOf<String>()
        val f = flags.toInt()
        if (f and 0x01 != 0) list.add("LE Limited Discoverable")
        if (f and 0x02 != 0) list.add("LE General Discoverable")
        if (f and 0x04 != 0) list.add("BR/EDR Not Supported")
        if (f and 0x08 != 0) list.add("Simultaneous LE + BR/EDR Controller")
        if (f and 0x10 != 0) list.add("Simultaneous LE + BR/EDR Host")
        return list
    }
}
