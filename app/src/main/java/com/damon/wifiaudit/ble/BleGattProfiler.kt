package com.damon.wifiaudit.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * BLE GATT Profiler.
 *
 * Connects to a BLE device and enumerates all GATT services, characteristics,
 * and descriptors. This reveals what data the device exposes — readable sensors,
 * writable controls, notification endpoints, etc.
 *
 * Usage:
 *   val profile = BleGattProfiler(context).profileDevice(device)
 *   profile.services.forEach { svc ->
 *     println("${svc.serviceName}: ${svc.characteristics.size} characteristics")
 *   }
 *
 * This is a user-triggered operation per device, not automatic during scanning.
 * GATT connections are slow (1-5 seconds) and may not be supported by all devices.
 */
class BleGattProfiler(private val context: Context) {

    private val tag = "BleGattProfiler"

    data class GattProfile(
        val macAddress: String,
        val deviceName: String?,
        val services: List<GattService>,
        val connectionState: ConnectionState,
        val profileTimeMs: Long
    )

    data class GattService(
        val uuid: String,
        val serviceName: String,
        val characteristics: List<GattCharacteristic>
    )

    data class GattCharacteristic(
        val uuid: String,
        val name: String,
        val properties: String,
        val readable: Boolean,
        val writable: Boolean,
        val notifiable: Boolean,
        val value: String?
    )

    enum class ConnectionState { CONNECTED, DISCONNECTED, FAILED }

    private var gatt: BluetoothGatt? = null
    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var servicesDeferred: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(tag, "GATT connected, discovering services...")
                    connectDeferred?.complete(true)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(tag, "GATT disconnected (status=$status)")
                    connectDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
                    servicesDeferred?.complete(false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "Services discovered (status=$status, ${gatt.services.size} services)")
            servicesDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    /**
     * Connect to a BLE device and profile all GATT services.
     * Times out after 8 seconds.
     */
    suspend fun profileDevice(device: BluetoothDevice): GattProfile = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val mac = device.address
        val name = device.name

        connectDeferred = CompletableDeferred()
        servicesDeferred = CompletableDeferred()

        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

            // Wait for connection
            val connected = withTimeoutOrNull(5000L) { connectDeferred?.await() } ?: false
            if (!connected) {
                return@withContext GattProfile(
                    macAddress = mac,
                    deviceName = name,
                    services = emptyList(),
                    connectionState = ConnectionState.FAILED,
                    profileTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Wait for service discovery
            withTimeoutOrNull(5000L) { servicesDeferred?.await() }

            // Enumerate services
            val services = gatt?.services?.map { service ->
                val serviceUuid = service.uuid.toString()
                val serviceName = BleUuidResolver.serviceName(service.uuid)

                val characteristics = service.characteristics.map { char ->
                    val props = mutableListOf<String>()
                    val readable = char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                    val writable = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                                  (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                    val notifiable = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                                    (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

                    if (readable) props.add("READ")
                    if (writable) props.add("WRITE")
                    if (notifiable) props.add("NOTIFY")

                    GattCharacteristic(
                        uuid = char.uuid.toString(),
                        name = BleUuidResolver.characteristicName(char.uuid),
                        properties = props.joinToString(", "),
                        readable = readable,
                        writable = writable,
                        notifiable = notifiable,
                        value = null
                    )
                }

                GattService(
                    uuid = serviceUuid,
                    serviceName = serviceName,
                    characteristics = characteristics
                )
            } ?: emptyList()

            val profileTime = System.currentTimeMillis() - startTime

            gatt?.disconnect()
            gatt?.close()

            GattProfile(
                macAddress = mac,
                deviceName = name,
                services = services,
                connectionState = if (services.isEmpty()) ConnectionState.FAILED else ConnectionState.CONNECTED,
                profileTimeMs = profileTime
            )
        } catch (e: Exception) {
            Log.e(tag, "GATT profiling failed for $mac", e)
            try { gatt?.close() } catch (_: Exception) {}
            GattProfile(
                macAddress = mac,
                deviceName = name,
                services = emptyList(),
                connectionState = ConnectionState.FAILED,
                profileTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Cancel any in-progress GATT operation and clean up.
     */
    fun cancel() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        connectDeferred?.complete(false)
        servicesDeferred?.complete(false)
    }
}
