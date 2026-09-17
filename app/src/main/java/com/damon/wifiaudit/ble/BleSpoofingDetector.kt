package com.damon.wifiaudit.ble

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE Spoofing / MITM Detector.
 *
 * Detects potential Bluetooth spoofing and cloning attacks by analyzing
 * BLE scan results for suspicious patterns:
 *
 * - Same device name + same service UUIDs but different MAC addresses (device cloning)
 * - Same iBeacon UUID/major/minor from multiple MAC addresses (beacon cloning)
 * - MAC addresses that change frequently for the same device identity (MAC randomization abuse)
 * - Multiple devices advertising the same service with conflicting manufacturer data
 * - Devices advertising with name of a known device type but wrong company ID
 *
 * This is heuristic detection — it flags suspicious patterns, not confirmed attacks.
 */
class BleSpoofingDetector {

    private val tag = "BleSpoofingDetector"

    data class SpoofingAlert(
        val alertType: SpoofingType,
        val severity: SpoofingSeverity,
        val description: String,
        val deviceMacs: List<String>,
        val deviceNames: List<String?>,
        val evidence: String
    )

    enum class SpoofingType {
        DEVICE_CLONING,         // Same name+services, different MACs
        BEACON_CLONING,         // Same iBeacon UUID/major/minor, different MACs
        MAC_RANDOMIZATION,      // Device identity persists across changing MACs
        MANUFACTURER_MISMATCH,   // Name says one vendor, manufacturer data says another
        SERVICE_CONFLICT         // Multiple devices advertising same service with conflicting data
    }

    enum class SpoofingSeverity { LOW, MEDIUM, HIGH }

    data class DeviceSnapshot(
        val macAddress: String,
        val deviceName: String?,
        val serviceUuids: List<String>,
        val iBeaconUuid: String?,
        val iBeaconMajor: Int?,
        val iBeaconMinor: Int?,
        val manufacturerId: String?,
        val beaconType: String?,
        val rssi: Int,
        val timestamp: Long
    )

    // Store snapshots per device identity (name + service UUIDs hash)
    private val identityMap = ConcurrentHashMap<String, MutableList<DeviceSnapshot>>()
    // Store iBeacon identity (UUID + major + minor)
    private val ibeaconMap = ConcurrentHashMap<String, MutableList<DeviceSnapshot>>()

    /**
     * Record a device sighting for spoofing analysis.
     */
    fun recordDevice(info: BleDeviceInfo, timestamp: Long = System.currentTimeMillis()) {
        val snapshot = DeviceSnapshot(
            macAddress = info.macAddress,
            deviceName = info.deviceName,
            serviceUuids = info.serviceUuids,
            iBeaconUuid = info.iBeaconUuid,
            iBeaconMajor = info.iBeaconMajor,
            iBeaconMinor = info.iBeaconMinor,
            manufacturerId = info.manufacturerFromAdv,
            beaconType = info.beaconType,
            rssi = info.rssi,
            timestamp = timestamp
        )

        // Track by device identity (name + sorted service UUIDs)
        val identityKey = buildIdentityKey(info.deviceName, info.serviceUuids)
        val identityList = identityMap.getOrPut(identityKey) { mutableListOf() }
        synchronized(identityList) {
            identityList.add(snapshot)
            if (identityList.size > 20) identityList.removeAt(0)
        }

        // Track by iBeacon identity
        if (info.iBeaconUuid != null) {
            val beaconKey = "${info.iBeaconUuid}:${info.iBeaconMajor ?: 0}:${info.iBeaconMinor ?: 0}"
            val beaconList = ibeaconMap.getOrPut(beaconKey) { mutableListOf() }
            synchronized(beaconList) {
                beaconList.add(snapshot)
                if (beaconList.size > 20) beaconList.removeAt(0)
            }
        }

        // Periodic eviction to prevent unbounded memory growth during long scans
        if (identityMap.size > 1000 || ibeaconMap.size > 1000) {
            val cutoff = timestamp - 2 * 60 * 60_000L
            identityMap.entries.removeIf { (_, list) ->
                synchronized(list) { list.removeAll { it.timestamp < cutoff }; list.isEmpty() }
            }
            ibeaconMap.entries.removeIf { (_, list) ->
                synchronized(list) { list.removeAll { it.timestamp < cutoff }; list.isEmpty() }
            }
        }
    }

    /**
     * Analyze all recorded devices for spoofing patterns.
     */
    fun detectSpoofing(): List<SpoofingAlert> {
        val alerts = mutableListOf<SpoofingAlert>()

        // Check for device cloning (same name+services, different MACs)
        for ((identityKey, snapshots) in identityMap) {
            val distinctMacs = snapshots.map { it.macAddress }.distinct()
            if (distinctMacs.size >= 2) {
                val names = snapshots.mapNotNull { it.deviceName }.distinct()
                val services = snapshots.flatMap { it.serviceUuids }.distinct()

                // Only flag if there's a real device identity (name or services)
                if (names.isNotEmpty() || services.isNotEmpty()) {
                    val severity = if (distinctMacs.size >= 3) SpoofingSeverity.HIGH else SpoofingSeverity.MEDIUM
                    alerts.add(SpoofingAlert(
                        alertType = SpoofingType.DEVICE_CLONING,
                        severity = severity,
                        description = "${distinctMacs.size} devices with same name/services but different MACs",
                        deviceMacs = distinctMacs,
                        deviceNames = names,
                        evidence = "Identity: ${names.joinToString() ?: "unnamed"}, Services: ${services.joinToString()}"
                    ))
                }
            }

            // Check for MAC randomization abuse (same identity, many MACs over time)
            if (distinctMacs.size >= 4) {
                val timeSpan = snapshots.maxOf { it.timestamp } - snapshots.minOf { it.timestamp }
                if (timeSpan >= 10 * 60_000) { // 10+ minutes
                    alerts.add(SpoofingAlert(
                        alertType = SpoofingType.MAC_RANDOMIZATION,
                        severity = SpoofingSeverity.LOW,
                        description = "Device identity persisting across ${distinctMacs.size} MAC addresses",
                        deviceMacs = distinctMacs,
                        deviceNames = snapshots.mapNotNull { it.deviceName }.distinct(),
                        evidence = "Same device name/services seen with ${distinctMacs.size} different MACs over ${timeSpan / 60_000}min"
                    ))
                }
            }

            // Check for manufacturer mismatch
            val manufacturers = snapshots.mapNotNull { it.manufacturerId }.distinct()
            val names = snapshots.mapNotNull { it.deviceName }.distinct()
            if (manufacturers.size >= 2 && names.isNotEmpty()) {
                alerts.add(SpoofingAlert(
                    alertType = SpoofingType.MANUFACTURER_MISMATCH,
                    severity = SpoofingSeverity.MEDIUM,
                    description = "Device name matches but manufacturer data differs",
                    deviceMacs = distinctMacs,
                    deviceNames = names,
                    evidence = "Manufacturers: ${manufacturers.joinToString()}, Names: ${names.joinToString()}"
                ))
            }
        }

        // Check for iBeacon cloning (same UUID/major/minor, different MACs)
        for ((beaconKey, snapshots) in ibeaconMap) {
            val distinctMacs = snapshots.map { it.macAddress }.distinct()
            if (distinctMacs.size >= 2) {
                val severity = if (distinctMacs.size >= 3) SpoofingSeverity.HIGH else SpoofingSeverity.MEDIUM
                alerts.add(SpoofingAlert(
                    alertType = SpoofingType.BEACON_CLONING,
                    severity = severity,
                    description = "${distinctMacs.size} devices broadcasting same iBeacon identity",
                    deviceMacs = distinctMacs,
                    deviceNames = snapshots.mapNotNull { it.deviceName }.distinct(),
                    evidence = "iBeacon: $beaconKey from ${distinctMacs.size} MACs"
                ))
            }
        }

        return alerts.sortedByDescending { it.severity.ordinal }
    }

    private fun buildIdentityKey(name: String?, serviceUuids: List<String>): String {
        val sortedServices = serviceUuids.map { it.uppercase() }.sorted().joinToString(",")
        return "${name?.lowercase() ?: "unknown"}:$sortedServices"
    }

    /**
     * Clear all recorded data.
     */
    fun clear() {
        identityMap.clear()
        ibeaconMap.clear()
    }

    /**
     * Get a summary of recorded devices.
     */
    fun getDeviceCount(): Int = identityMap.size
    fun getSnapshotCount(): Int = identityMap.values.sumOf { it.size }
}
