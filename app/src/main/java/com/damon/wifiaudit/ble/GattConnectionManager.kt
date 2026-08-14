package com.damon.wifiaudit.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class GattConnectionManager(
    private val context: Context,
    private val device: BluetoothDevice
) {
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Discovering : ConnectionState()
        object Ready : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class DiscoveredService(
        val uuid: UUID,
        val type: Int,
        val instanceId: Int,
        val characteristics: List<DiscoveredCharacteristic>
    )

    data class DiscoveredCharacteristic(
        val uuid: UUID,
        val properties: Int,
        val permissions: Int,
        val instanceId: Int,
        val descriptors: List<UUID>
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("Gatt", "Connected to ${device.address}")
                    _connectionState.value = ConnectionState.Connected
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("Gatt", "Disconnected from ${device.address}")
                    _connectionState.value = ConnectionState.Disconnected
                    this@GattConnectionManager.gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Gatt", "MTU negotiated: $mtu")
            }
            _connectionState.value = ConnectionState.Discovering
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status")
                return
            }

            val discovered = gatt.services.map { service ->
                DiscoveredService(
                    uuid = service.uuid,
                    type = service.type,
                    instanceId = service.instanceId,
                    characteristics = service.characteristics.map { char ->
                        DiscoveredCharacteristic(
                            uuid = char.uuid,
                            properties = char.properties,
                            permissions = char.permissions,
                            instanceId = char.instanceId,
                            descriptors = char.descriptors.map { it.uuid }
                        )
                    }
                )
            }

            _services.value = discovered
            _connectionState.value = ConnectionState.Ready
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d("Gatt", "Read ${characteristic.uuid}: ${value.toHex()}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) return

        _connectionState.value = ConnectionState.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
    }

    fun release() {
        disconnect()
        scope.cancel()
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.e("Gatt", "Permission revoked mid-release", e)
        } catch (_: Exception) {}
        gatt = null
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        val g = gatt ?: return
        val service = g.getService(serviceUuid) ?: return
        val char = service.getCharacteristic(charUuid) ?: return
        if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            g.readCharacteristic(char)
        }
    }
}

fun ByteArray.toHex(separator: String = " "): String =
    joinToString(separator) { "%02X".format(it) }

object BleUuidResolver {
    private val knownServices = mapOf(
        UUID.fromString("00001800-0000-1000-8000-00805f9b34fb") to "Generic Access",
        UUID.fromString("00001801-0000-1000-8000-00805f9b34fb") to "Generic Attribute",
        UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb") to "Device Information",
        UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb") to "Battery Service",
        UUID.fromString("00001812-0000-1000-8000-00805f9b34fb") to "Human Interface Device",
        UUID.fromString("0000fe9f-0000-1000-8000-00805f9b34fb") to "Google Nearby",
        UUID.fromString("0000fd6f-0000-1000-8000-00805f9b34fb") to "Exposure Notification",
        UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb") to "Eddystone",
    )

    private val knownCharacteristics = mapOf(
        UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb") to "Device Name",
        UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb") to "Appearance",
        UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb") to "Manufacturer Name",
        UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb") to "Model Number",
        UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb") to "Serial Number",
        UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb") to "Firmware Revision",
        UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb") to "Battery Level",
    )

    fun serviceName(uuid: UUID): String = knownServices[uuid] ?: "Unknown Service"
    fun characteristicName(uuid: UUID): String = knownCharacteristics[uuid] ?: "Unknown Characteristic"

    fun isStandardUuid(uuid: UUID): Boolean =
        uuid.toString().startsWith("0000") && uuid.toString().endsWith("-0000-1000-8000-00805f9b34fb")

    fun shortUuid(uuid: UUID): String {
        val s = uuid.toString()
        return if (isStandardUuid(uuid)) s.substring(4, 8).uppercase() else s.take(8)
    }
}
