package com.damon.wifiaudit.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LightGattManager(
    private val context: Context,
    private val device: BluetoothDevice,
    private val scope: CoroutineScope
) {
    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        object Connected : State()
        object Discovering : State()
        data class Ready(val services: List<BleService>) : State()
        data class Error(val msg: String) : State()
    }

    data class BleService(
        val uuid: UUID,
        val name: String,
        val characteristics: List<BleCharacteristic>
    )

    data class BleCharacteristic(
        val uuid: UUID,
        val name: String,
        val properties: Int,
        val value: String? = null
    )

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var connectTimeoutJob: Job? = null
    
    private val readQueue = mutableListOf<BluetoothGattCharacteristic>()
    private var isReading = false

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            connectTimeoutJob?.cancel()
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = State.Connected
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_state.value !is State.Error) {
                        _state.value = State.Disconnected
                    }
                    this@LightGattManager.gatt = null
                    readQueue.clear()
                    isReading = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            _state.value = State.Discovering
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Error("Discovery failed")
                return
            }

            val services = gatt.services.map { svc ->
                BleService(
                    uuid = svc.uuid,
                    name = BleUuidResolver.serviceName(svc.uuid),
                    characteristics = svc.characteristics.map { char ->
                        BleCharacteristic(
                            uuid = char.uuid,
                            name = BleUuidResolver.characteristicName(char.uuid),
                            properties = char.properties
                        )
                    }
                )
            }

            _state.value = State.Ready(services)
            
            // Start auto-reading
            readQueue.clear()
            gatt.services.forEach { svc ->
                svc.characteristics.forEach { char ->
                    if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        readQueue.add(char)
                    }
                }
            }
            readNext()
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateCharacteristicValue(characteristic.uuid, characteristic.value)
            }
            isReading = false
            // Small delay to prevent GATT congestion
            scope.launch {
                delay(100)
                readNext()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readNext() {
        val gatt = gatt ?: return
        if (isReading || readQueue.isEmpty()) return
        
        val next = readQueue.removeAt(0)
        isReading = true
        if (!gatt.readCharacteristic(next)) {
            isReading = false
            readNext()
        }
    }

    private fun updateCharacteristicValue(uuid: UUID, value: ByteArray?) {
        val current = _state.value
        if (current !is State.Ready) return

        val stringValue = value?.let { bytes ->
            // Try to detect if it's a printable string
            if (bytes.isNotEmpty() && bytes.all { it in 32..126 || it == 10.toByte() || it == 13.toByte() }) {
                String(bytes).trim()
            } else {
                "0x" + bytes.joinToString("") { "%02X".format(it) }
            }
        } ?: ""

        val newServices = current.services.map { svc ->
            svc.copy(characteristics = svc.characteristics.map { char ->
                if (char.uuid == uuid) char.copy(value = stringValue) else char
            })
        }
        _state.value = State.Ready(newServices)
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (_state.value is State.Connecting || _state.value is State.Connected) return
        
        _state.value = State.Connecting
        
        // Timeout after 10 seconds
        connectTimeoutJob = scope.launch {
            delay(10_000)
            if (_state.value is State.Connecting) {
                _state.value = State.Error("Connection timed out — device may be out of range or non-connectable")
                release()
            }
        }
        
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
    }

    fun release() {
        disconnect()
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        readQueue.clear()
    }
}
