package com.damon.wifiaudit.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class LightGattManager(private val context: Context, private val device: BluetoothDevice) {

    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        object Discovering : State()
        data class Ready(val services: List<BleService>) : State()
        data class Error(val msg: String) : State()
    }

    data class BleService(
        val uuid: UUID,
        val name: String?,
        val characteristics: List<BleCharacteristic>
    )

    data class BleCharacteristic(
        val uuid: UUID,
        val name: String?,
        val properties: Int,
        val serviceUuid: UUID,
        val value: ByteArray? = null,        // last read/notify value
        val isNotifying: Boolean = false,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state

    private var gatt: BluetoothGatt? = null
    private val operationQueue = ConcurrentLinkedQueue<GattOperation>()
    private var pendingOperation: GattOperation? = null

    private sealed class GattOperation {
        data class Read(val charUuid: UUID, val serviceUuid: UUID) : GattOperation()
        data class Write(val charUuid: UUID, val serviceUuid: UUID, val data: ByteArray, val type: Int) : GattOperation()
        data class EnableNotify(val charUuid: UUID, val serviceUuid: UUID, val enable: Boolean) : GattOperation()
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (_state.value is State.Connecting) return
        _state.value = State.Connecting

        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        g.requestMtu(517)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _state.value = State.Disconnected
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                g.discoverServices()
                _state.value = State.Discovering
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _state.value = State.Error("Service discovery failed: $status")
                    return
                }
                val svcs = g.services.map { svc ->
                    BleService(
                        uuid = svc.uuid,
                        name = svc.uuid.toString(), // resolved later in UI
                        characteristics = svc.characteristics.map { c ->
                            BleCharacteristic(
                                uuid = c.uuid,
                                name = null,
                                properties = c.properties,
                                serviceUuid = svc.uuid
                            )
                        }
                    )
                }
                _state.value = State.Ready(svcs)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                updateCharacteristic(characteristic, value, status, "read")
                completeOperation()
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateCharacteristic(characteristic, characteristic.value, status, "write")
                } else {
                    markError(characteristic, "Write failed: $status")
                }
                completeOperation()
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                updateCharacteristic(characteristic, value, BluetoothGatt.GATT_SUCCESS, "notify")
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val char = descriptor.characteristic
                    val isEnabling = descriptor.value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            descriptor.value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    updateNotifyState(char, isEnabling)
                }
                completeOperation()
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        enqueue(GattOperation.Read(charUuid, serviceUuid))
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, data: ByteArray, type: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        enqueue(GattOperation.Write(charUuid, serviceUuid, data, type))
    }

    @SuppressLint("MissingPermission")
    fun setNotify(serviceUuid: UUID, charUuid: UUID, enable: Boolean) {
        enqueue(GattOperation.EnableNotify(charUuid, serviceUuid, enable))
    }

    @SuppressLint("MissingPermission")
    private fun enqueue(op: GattOperation) {
        operationQueue.add(op)
        if (pendingOperation == null) doNextOperation()
    }

    @SuppressLint("MissingPermission")
    private fun doNextOperation() {
        if (pendingOperation != null) return
        val op = operationQueue.poll() ?: return
        pendingOperation = op
        val g = gatt ?: run { pendingOperation = null; return }

        when (op) {
            is GattOperation.Read -> {
                val char = findChar(g, op.serviceUuid, op.charUuid) ?: run {
                    pendingOperation = null; return
                }
                if (!g.readCharacteristic(char)) {
                    markError(char, "Read not permitted")
                    pendingOperation = null
                }
            }
            is GattOperation.Write -> {
                val char = findChar(g, op.serviceUuid, op.charUuid) ?: run {
                    pendingOperation = null; return
                }
                char.writeType = op.type
                char.value = op.data
                if (!g.writeCharacteristic(char)) {
                    markError(char, "Write not permitted")
                    pendingOperation = null
                }
            }
            is GattOperation.EnableNotify -> {
                val char = findChar(g, op.serviceUuid, op.charUuid) ?: run {
                    pendingOperation = null; return
                }
                g.setCharacteristicNotification(char, op.enable)
                val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (descriptor != null) {
                    descriptor.value = if (op.enable) {
                        if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        else
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                    g.writeDescriptor(descriptor)
                } else {
                    updateNotifyState(char, op.enable)
                    pendingOperation = null
                }
            }
        }
    }

    private fun completeOperation() {
        pendingOperation = null
        doNextOperation()
    }

    private fun findChar(g: BluetoothGatt, svcUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? {
        return g.getService(svcUuid)?.getCharacteristic(charUuid)
    }

    private fun updateCharacteristic(
        btChar: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
        source: String
    ) {
        val current = (_state.value as? State.Ready)?.services ?: return
        val updated = current.map { svc ->
            if (svc.uuid != btChar.service.uuid) return@map svc
            val newChars = svc.characteristics.map { c ->
                if (c.uuid != btChar.uuid) return@map c
                c.copy(
                    value = value.copyOf(),
                    lastError = if (status == BluetoothGatt.GATT_SUCCESS) null else "$source error $status"
                )
            }
            svc.copy(characteristics = newChars)
        }
        _state.value = State.Ready(updated)
    }

    private fun updateNotifyState(btChar: BluetoothGattCharacteristic, isNotifying: Boolean) {
        val current = (_state.value as? State.Ready)?.services ?: return
        val updated = current.map { svc ->
            if (svc.uuid != btChar.service.uuid) return@map svc
            val newChars = svc.characteristics.map { c ->
                if (c.uuid != btChar.uuid) return@map c
                c.copy(isNotifying = isNotifying)
            }
            svc.copy(characteristics = newChars)
        }
        _state.value = State.Ready(updated)
    }

    private fun markError(btChar: BluetoothGattCharacteristic, error: String) {
        val current = (_state.value as? State.Ready)?.services ?: return
        val updated = current.map { svc ->
            if (svc.uuid != btChar.service.uuid) return@map svc
            val newChars = svc.characteristics.map { c ->
                if (c.uuid != btChar.uuid) return@map c
                c.copy(lastError = error)
            }
            svc.copy(characteristics = newChars)
        }
        _state.value = State.Ready(updated)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        operationQueue.clear()
        pendingOperation = null
        gatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun release() {
        disconnect()
        gatt?.close()
        gatt = null
    }
}
