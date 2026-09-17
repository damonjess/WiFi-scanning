package com.damon.wifiaudit.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.LinkedHashMap

/**
 * LRU connection pool for BLE GATT clients.
 * Caches up to [maxSize] concurrent GATT connections keyed by MAC address to eliminate
 * redundant cold-connect overhead for frequently profiled devices.
 */
class GattConnectionPool private constructor(
    private val context: Context,
    private val maxSize: Int = 4
) {
    companion object {
        @Volatile
        private var INSTANCE: GattConnectionPool? = null

        fun getInstance(context: Context): GattConnectionPool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GattConnectionPool(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val pool = object : LinkedHashMap<String, LightGattManager>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LightGattManager>?): Boolean {
            if (size > maxSize) {
                eldest?.value?.release()
                return true
            }
            return false
        }
    }

    @Synchronized
    fun getOrCreate(device: BluetoothDevice): LightGattManager {
        val mac = device.address
        return pool.getOrPut(mac) {
            LightGattManager(context, device).apply {
                connect()
            }
        }
    }

    @Synchronized
    fun get(mac: String): LightGattManager? {
        return pool[mac]
    }

    @Synchronized
    fun release(mac: String) {
        pool.remove(mac)?.release()
    }

    @Synchronized
    fun clear() {
        pool.values.forEach { it.release() }
        pool.clear()
    }
}
