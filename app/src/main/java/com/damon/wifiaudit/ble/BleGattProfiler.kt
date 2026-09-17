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
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

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
        val descriptors: List<GattDescriptor> = emptyList()
    )

    data class GattDescriptor(
        val uuid: String,
        val name: String,
        val value: ByteArray? = null
    )

    enum class ConnectionState { CONNECTED, DISCONNECTED, FAILED }

    private var gatt: BluetoothGatt? = null
    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var servicesDeferred: CompletableDeferred<Boolean>? = null
    private var mtuDeferred: CompletableDeferred<Boolean>? = null
    private val readQueue = ConcurrentLinkedQueue<Pair<BluetoothGattCharacteristic, UUID>>()
    private var readDeferred: CompletableDeferred<ByteArray?>? = null
    private var currentReadChar: BluetoothGattCharacteristic? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(tag, "GATT connected, requesting MTU...")
                    connectDeferred?.complete(true)
                    // Request larger MTU for faster reads
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(tag, "GATT disconnected (status=$status)")
                    connectDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
                    servicesDeferred?.complete(false)
                    mtuDeferred?.complete(false)
                    readDeferred?.complete(null)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "MTU changed to $mtu (status=$status)")
            mtuDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "Services discovered (status=$status, ${gatt.services.size} services)")
            servicesDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(tag, "Characteristic read: ${characteristic.uuid} (status=$status, ${value.size} bytes)")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readDeferred?.complete(value)
            } else {
                readDeferred?.complete(null)
            }
            // Process next read in queue
            processReadQueue(gatt)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(tag, "Descriptor read: ${descriptor.uuid} (status=$status)")
        }
    }

    /**
     * Connect to a BLE device and profile all GATT services.
     * Requests MTU 517 for faster reads, then discovers services.
     * Auto-reads standard readable characteristics (Device Name, Model,
     * Serial, Firmware, Hardware, Software, Manufacturer, Battery).
     * Times out after 10 seconds total.
     */
    suspend fun profileDevice(device: BluetoothDevice): GattProfile = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val mac = device.address
        val name = device.name

        connectDeferred = CompletableDeferred()
        servicesDeferred = CompletableDeferred()
        mtuDeferred = CompletableDeferred()
        readQueue.clear()

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

            // Wait for MTU change (with timeout — proceed even if MTU fails)
            withTimeoutOrNull(2000L) { mtuDeferred?.await() }

            // If MTU callback didn't fire, discover services directly
            if (servicesDeferred?.isCompleted == false) {
                gatt?.discoverServices()
            }

            // Wait for service discovery
            withTimeoutOrNull(5000L) { servicesDeferred?.await() }

            // Enumerate services and auto-read standard characteristics
            val autoReadUuids = setOf(
                UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb"), // Device Name
                UUID.fromString("00002A01-0000-1000-8000-00805f9b34fb"), // Appearance
                UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb"), // Battery Level
                UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb"), // Model Number
                UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb"), // Serial Number
                UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb"), // Firmware Revision
                UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb"), // Hardware Revision
                UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb"), // Software Revision
                UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb"), // Manufacturer Name
                UUID.fromString("00002A23-0000-1000-8000-00805f9b34fb"), // System ID
                UUID.fromString("00002A07-0000-1000-8000-00805f9b34fb")  // Tx Power Level
            )

            val services = gatt?.services?.map { service ->
                val serviceUuid = service.uuid
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

                    // Queue reads for standard characteristics
                    val rawValue: ByteArray? = if (readable && char.uuid in autoReadUuids) {
                        readCharacteristicSync(gatt!!, char)
                    } else null

                    val decodedValue = rawValue?.let {
                        BleGattDecoder.decodeValue(char.uuid, it)
                    } ?: rawValue?.let {
                        // Fallback: show as hex if not decodable
                        val hex = it.joinToString(" ") { byte -> "%02X".format(byte) }
                        if (hex.isNotBlank()) "HEX: $hex" else null
                    }

                    // Read descriptors
                    val descriptors = char.descriptors.map { desc ->
                        GattDescriptor(
                            uuid = desc.uuid.toString(),
                            name = BleUuidResolver.descriptorName(desc.uuid)
                        )
                    }

                    GattCharacteristic(
                        uuid = char.uuid.toString(),
                        name = BleUuidResolver.characteristicName(char.uuid),
                        properties = props.joinToString(", "),
                        readable = readable,
                        writable = writable,
                        notifiable = notifiable,
                        value = decodedValue,
                        rawValue = rawValue,
                        descriptors = descriptors
                    )
                }

                GattService(
                    uuid = serviceUuid.toString(),
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
     * Reads a single characteristic synchronously with a timeout.
     * GATT operations must be serialized, so this blocks until the read completes.
     */
    private suspend fun readCharacteristicSync(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): ByteArray? {
        readDeferred = CompletableDeferred()
        currentReadChar = characteristic

        if (!gatt.readCharacteristic(characteristic)) {
            currentReadChar = null
            return null
        }

        val result = withTimeoutOrNull(2000L) { readDeferred?.await() }
        currentReadChar = null
        return result
    }

    /**
     * Processes the next read in the queue. Called after each characteristic read completes.
     */
    private fun processReadQueue(gatt: BluetoothGatt) {
        val next = readQueue.poll() ?: return
        val (char, _) = next
        readDeferred = CompletableDeferred()
        currentReadChar = char
        if (!gatt.readCharacteristic(char)) {
            readDeferred?.complete(null)
            currentReadChar = null
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
        mtuDeferred?.complete(false)
        readDeferred?.complete(null)
        readQueue.clear()
    }
}
