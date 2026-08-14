package com.damon.wifiaudit.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleScanManager(context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<Map<String, BleDeviceInfo>>(emptyMap())
    val devices: StateFlow<Map<String, BleDeviceInfo>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            upsertDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { upsertDevice(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("BleScanManager", "Scan failed with code $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun upsertDevice(result: ScanResult) {
        val record = result.scanRecord
        val iBeacon = IBeaconParser.parse(record)

        val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }

        val info = BleDeviceInfo(
            macAddress = result.device.address,
            deviceName = record?.deviceName ?: result.device.name,
            rssi = result.rssi,
            txPowerLevel = txPower,
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
            iBeaconMajor = iBeacon?.major,
            iBeaconMinor = iBeacon?.minor,
            iBeaconUuid = iBeacon?.uuid,
            lastSeenMillis = System.currentTimeMillis(),
            manufacturerFromAdv = parseManufacturerData(record),
            rawBytes = record?.bytes,
            isConnectable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                result.isConnectable
            } else {
                record?.advertiseFlags?.let { flags -> (flags and 0x02) != 0 } ?: false
            }
        )

        _devices.value = _devices.value.toMutableMap().apply {
            put(info.macAddress, info)
        }
    }

    private fun parseManufacturerData(record: android.bluetooth.le.ScanRecord?): String? {
        if (record == null) return null
        val bytes = record.bytes ?: return null
        
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + 1 >= bytes.size) break
            
            val type = bytes[offset + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 3 && offset + 2 + length - 1 <= bytes.size) {
                val companyId = (bytes[offset + 2].toInt() and 0xFF) or 
                               ((bytes[offset + 3].toInt() and 0xFF) shl 8)
                return BleUuidResolver.companyName(companyId)
            }
            offset += 1 + length
        }
        return null
    }

    /** Caller must have already confirmed BLUETOOTH_SCAN (API31+) / ACCESS_FINE_LOCATION (pre-31). */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (_isScanning.value) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            Log.e("BleScanManager", "Missing BLE scan permission", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // permission revoked mid-scan
        } finally {
            _isScanning.value = false
        }
    }

    fun clearResults() {
        _devices.value = emptyMap()
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
}
