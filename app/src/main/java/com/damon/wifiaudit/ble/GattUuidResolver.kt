package com.damon.wifiaudit.ble

import com.damon.wifiaudit.data.AppDatabase
import java.util.UUID

/**
 * Resolves GATT UUIDs for display.
 *
 * The local Bluetooth SIG resolver is deliberately consulted before the Room
 * catalogue. This makes labels available immediately during a live discovery
 * and also keeps previously saved GATT snapshots readable if the database has
 * not yet been seeded or was upgraded from an earlier app version.
 */
object GattUuidResolver {
    private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /**
     * Converts a Bluetooth Base UUID to its 16-bit assigned-number form;
     * custom and vendor UUIDs remain in full canonical form.
     */
    fun normalize(uuid: UUID): String {
        val value = uuid.toString().lowercase()
        return if (isBluetoothSigUuid(uuid)) value.substring(4, 8) else value
    }

    fun isBluetoothSigUuid(uuid: UUID): Boolean {
        val value = uuid.toString().lowercase()
        return value.startsWith("0000") && value.endsWith(BASE_UUID_SUFFIX)
    }

    /** A compact identifier for badges without misleading custom UUIDs as 16-bit IDs. */
    fun displayId(uuid: UUID): String = if (isBluetoothSigUuid(uuid)) {
        "0x${normalize(uuid).uppercase()}"
    } else {
        "Custom UUID"
    }

    /**
     * Resolves a service UUID from the persisted catalogue, then the built-in
     * Bluetooth SIG/company mapping, and finally a clear category label.
     */
    suspend fun resolveServiceName(uuid: UUID, db: AppDatabase): String {
        return db.standardGattUuidDao().lookupName(normalize(uuid), "service")
            ?: serviceFallbackName(uuid)
    }

    /**
     * Resolves a characteristic UUID from the persisted catalogue, then the
     * built-in Bluetooth SIG mapping, and finally a clear category label.
     */
    suspend fun resolveCharacteristicName(uuid: UUID, db: AppDatabase): String {
        return db.standardGattUuidDao().lookupName(normalize(uuid), "characteristic")
            ?: characteristicFallbackName(uuid)
    }

    /** Synchronous fallback used during live discovery and snapshot creation. */
    fun serviceFallbackName(uuid: UUID): String {
        val resolved = BleUuidResolver.serviceName(uuid)
        return when {
            resolved != "Unknown Service" -> resolved
            isBluetoothSigUuid(uuid) -> "Bluetooth SIG Service (${displayId(uuid)})"
            else -> "Custom / Vendor Service"
        }
    }

    /** Synchronous fallback used during live discovery and snapshot creation. */
    fun characteristicFallbackName(uuid: UUID): String {
        val resolved = BleUuidResolver.characteristicName(uuid)
        return when {
            resolved != "Unknown Characteristic" -> resolved
            isBluetoothSigUuid(uuid) -> "Bluetooth SIG Characteristic (${displayId(uuid)})"
            else -> "Custom / Vendor Characteristic"
        }
    }
}
