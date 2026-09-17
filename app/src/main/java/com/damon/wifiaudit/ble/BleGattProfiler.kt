package com.damon.wifiaudit.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        val value: String?,
        val rawValue: ByteArray? = null,
        val descriptors: List<GattDescriptor> = emptyList(),
        val securityFlags: List<BleGattDecoder.SecurityFlag> = emptyList()
    )

    data class GattDescriptor(
        val uuid: String,
        val name: String,
        val value: ByteArray? = null
    )

    enum class ConnectionState { CONNECTED, DISCONNECTED, FAILED }

    private val pool = GattConnectionPool.getInstance(context)

    /**
     * Profile a BLE device's GATT services using the consolidated [LightGattManager] and [GattConnectionPool].
     */
    suspend fun profileDevice(
        device: BluetoothDevice,
        knownDeviceName: String? = null
    ): GattProfile = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val mac = device.address
        val name = knownDeviceName ?: device.name

        try {
            val manager = pool.getOrCreate(device)

            val finalState = withTimeoutOrNull(8000L) {
                manager.state.first { state ->
                    state is LightGattManager.State.Ready || state is LightGattManager.State.Error
                }
            } ?: manager.state.value

            val services = if (finalState is LightGattManager.State.Ready) {
                finalState.services.map { svc ->
                    val characteristics = svc.characteristics.map { c ->
                        val props = mutableListOf<String>()
                        val readable = c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                        val writable = (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                                      (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                        val notifiable = (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                                        (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

                        if (readable) props.add("READ")
                        if (writable) props.add("WRITE")
                        if (notifiable) props.add("NOTIFY")

                        val decodedValue = c.decodedValue ?: c.value?.let { bytes ->
                            val hex = bytes.joinToString(" ") { "%02X".format(it) }
                            if (hex.isNotBlank()) "HEX: $hex" else null
                        }

                        GattCharacteristic(
                            uuid = c.uuid.toString(),
                            name = c.name ?: BleUuidResolver.characteristicName(c.uuid),
                            properties = props.joinToString(", "),
                            readable = readable,
                            writable = writable,
                            notifiable = notifiable,
                            value = decodedValue,
                            rawValue = c.value,
                            descriptors = c.descriptors.map { desc ->
                                GattDescriptor(
                                    uuid = desc.uuid.toString(),
                                    name = desc.name ?: BleUuidResolver.descriptorName(desc.uuid)
                                )
                            },
                            securityFlags = c.securityFlags
                        )
                    }

                    GattService(
                        uuid = svc.uuid.toString(),
                        serviceName = svc.name ?: BleUuidResolver.serviceName(svc.uuid),
                        characteristics = characteristics
                    )
                }
            } else emptyList()

            GattProfile(
                macAddress = mac,
                deviceName = name,
                services = services,
                connectionState = if (services.isNotEmpty()) ConnectionState.CONNECTED else ConnectionState.FAILED,
                profileTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(tag, "GATT profiling failed for $mac", e)
            GattProfile(
                macAddress = mac,
                deviceName = name,
                services = emptyList(),
                connectionState = ConnectionState.FAILED,
                profileTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    fun cancel() {
        // Managed via GattConnectionPool
    }
}
