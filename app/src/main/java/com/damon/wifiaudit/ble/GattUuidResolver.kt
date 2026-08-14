package com.damon.wifiaudit.ble

import com.damon.wifiaudit.data.AppDatabase
import java.util.UUID

object GattUuidResolver {
    private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /**
     * Converts a 128-bit UUID to its 16-bit short form if it matches
     * the Bluetooth Base UUID. Otherwise returns the full lowercase UUID.
     *
     * 0000180f-0000-1000-8000-00805f9b34fb → "180f"
     * 12345678-1234-1234-1234-123456789abc → "12345678-1234-1234-1234-123456789abc"
     */
    fun normalize(uuid: UUID): String {
        val str = uuid.toString().lowercase()
        return if (str.endsWith(BASE_UUID_SUFFIX) && str.startsWith("0000")) {
            str.substring(4, 8)  // 16-bit short UUID
        } else {
            str
        }
    }

    /**
     * Resolves a service UUID to its human-readable name.
     * Returns null if not a known standard UUID.
     */
    suspend fun resolveServiceName(uuid: UUID, db: AppDatabase): String? {
        return db.standardGattUuidDao().lookupName(normalize(uuid), "service")
    }

    /**
     * Resolves a characteristic UUID to its human-readable name.
     */
    suspend fun resolveCharacteristicName(uuid: UUID, db: AppDatabase): String? {
        return db.standardGattUuidDao().lookupName(normalize(uuid), "characteristic")
    }
}
