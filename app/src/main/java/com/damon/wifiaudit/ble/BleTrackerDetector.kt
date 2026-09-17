package com.damon.wifiaudit.ble

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE Tracker Detector.
 *
 * Detects Bluetooth tracking beacons (AirTags, Tiles, Samsung SmartTags, and
 * unknown trackers) by monitoring device presence across multiple scan sessions
 * and locations. A device that appears in multiple sessions at different times
 * and/or locations may be a tracker following the user.
 *
 * Detection signals:
 * - Device seen in 3+ separate scan sessions over 30+ minutes
 * - Device seen at 2+ different GPS locations
 * - Known tracker beacon types (Tile, AirTag iBeacon, Samsung SmartTag)
 * - Device name matches known tracker patterns
 * - Device has no name but persistent MAC across sessions
 *
 * This is heuristic — it flags potential trackers, not confirmed ones.
 */
class BleTrackerDetector {

    private val tag = "BleTrackerDetector"

    data class TrackerAlert(
        val macAddress: String,
        val deviceName: String?,
        val beaconType: String?,
        val reason: String,
        val sessionsSeen: Int,
        val firstSeen: Long,
        val lastSeen: Long,
        val locationsSeen: Int
    )

    data class DeviceSession(
        val macAddress: String,
        val deviceName: String?,
        val manufacturerFromAdv: String?,
        val serviceUuids: List<String>,
        val iBeaconUuid: String?,
        val iBeaconMajor: Int?,
        val iBeaconMinor: Int?,
        val beaconType: String?,
        val timestamp: Long,
        val latitude: Double?,
        val longitude: Double?,
        val rssi: Int
    )

    // Known tracker device names (case-insensitive substring match)
    private val trackerNamePatterns = listOf(
        "airtag", "tile", "smarttag", "nut", "chipolo", "air badge",
        "find my", "findmy", "google tag"
    )

    // Known tracker beacon types
    private val trackerBeaconTypes = setOf(
        "Tile Tracker", "iBeacon", "AirTag", "Samsung SmartTag"
    )

    // Known tracker service UUIDs
    private val trackerServiceUuids = setOf(
        "FEED", // Tile
        "FD43", "FD44", "FD4D" // Apple Find My / AirTag
    )

    // In-memory tracking store
    private val deviceHistory = ConcurrentHashMap<String, MutableList<DeviceSession>>()

    /**
     * Record a device sighting during a scan session.
     */
    fun recordSighting(
        macAddress: String,
        deviceName: String?,
        manufacturerFromAdv: String?,
        serviceUuids: List<String>,
        iBeaconUuid: String?,
        iBeaconMajor: Int?,
        iBeaconMinor: Int?,
        beaconType: String?,
        timestamp: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        rssi: Int = -60
    ) {
        val session = DeviceSession(
            macAddress = macAddress,
            deviceName = deviceName,
            manufacturerFromAdv = manufacturerFromAdv,
            serviceUuids = serviceUuids,
            iBeaconUuid = iBeaconUuid,
            iBeaconMajor = iBeaconMajor,
            iBeaconMinor = iBeaconMinor,
            beaconType = beaconType,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            rssi = rssi
        )

        val history = deviceHistory.getOrPut(macAddress) { mutableListOf() }
        synchronized(history) {
            // Avoid duplicate entries within the same minute
            if (history.isEmpty() || timestamp - history.last().timestamp > 60_000) {
                history.add(session)
                if (history.size > 50) {
                    history.removeAt(0)
                }
            }
        }

        // Periodic cleanup when total tracked devices grow large to prevent memory leaks during long scans
        if (deviceHistory.size > 1000) {
            cleanupOldEntries(24 * 60 * 60_000L)
            if (deviceHistory.size > 1000) {
                val cutoff = System.currentTimeMillis() - 4 * 60 * 60_000L
                deviceHistory.entries.removeIf { (_, sessions) ->
                    sessions.isEmpty() || (sessions.lastOrNull()?.timestamp ?: 0L) < cutoff
                }
            }
        }
    }

    /**
     * Analyze device history and return alerts for potential trackers.
     */
    fun detectTrackers(): List<TrackerAlert> {
        val alerts = mutableListOf<TrackerAlert>()

        for ((mac, sessions) in deviceHistory) {
            if (sessions.size < 2) continue

            val firstSession = sessions.first()
            val lastSession = sessions.last()
            val timeSpan = lastSession.timestamp - firstSession.timestamp

            // Count distinct GPS locations
            val distinctLocations = sessions.mapNotNull {
                if (it.latitude != null && it.longitude != null) {
                    // Round to ~100m precision to avoid GPS jitter
                    Pair(
                        Math.round(it.latitude * 1000) / 1000.0,
                        Math.round(it.longitude * 1000) / 1000.0
                    )
                } else null
            }.distinct()

            val locationsCount = distinctLocations.size
            val sessionCount = sessions.size
            val name = sessions.mapNotNull { it.deviceName }.firstOrNull()
            val beaconType = sessions.mapNotNull { it.beaconType }.firstOrNull()
            val manufacturer = sessions.mapNotNull { it.manufacturerFromAdv }.firstOrNull()
            val serviceUuids = sessions.flatMap { it.serviceUuids }.distinct()
            val iBeaconUuid = sessions.mapNotNull { it.iBeaconUuid }.firstOrNull()

            val reasons = mutableListOf<String>()

            // Signal 1: Known tracker beacon type
            if (beaconType != null && beaconType in trackerBeaconTypes) {
                reasons.add("Known tracker type: $beaconType")
            }

            // Signal 2: Known tracker service UUID
            if (serviceUuids.any { it.uppercase() in trackerServiceUuids }) {
                val matched = serviceUuids.filter { it.uppercase() in trackerServiceUuids }
                reasons.add("Tracker service UUID: ${matched.joinToString()}")
            }

            // Signal 3: Device name matches tracker pattern
            if (name != null && trackerNamePatterns.any { name.contains(it, ignoreCase = true) }) {
                reasons.add("Tracker device name: '$name'")
            }

            // Signal 4: Seen across 3+ sessions over 30+ minutes
            if (sessionCount >= 3 && timeSpan >= 30 * 60_000) {
                val minutes = timeSpan / 60_000
                reasons.add("Seen in $sessionCount sessions over ${minutes}min")
            }

            // Signal 5: Seen at 2+ different locations
            if (locationsCount >= 2) {
                reasons.add("Seen at $locationsCount different locations")
            }

            // Signal 6: Unknown device with persistent MAC over 1+ hour
            if (sessionCount >= 4 && timeSpan >= 60 * 60_000 && name == null && manufacturer == null) {
                val minutes = timeSpan / 60_000
                reasons.add("Unnamed device persisting for ${minutes}min")
            }

            // Signal 7: Apple device (possible AirTag) with iBeacon
            if (iBeaconUuid != null && (manufacturer?.contains("Apple") == true || serviceUuids.any { it.uppercase().startsWith("FD") })) {
                reasons.add("Apple iBeacon — possible AirTag")
            }

            if (reasons.isNotEmpty()) {
                alerts.add(TrackerAlert(
                    macAddress = mac,
                    deviceName = name,
                    beaconType = beaconType,
                    reason = reasons.joinToString("; "),
                    sessionsSeen = sessionCount,
                    firstSeen = firstSession.timestamp,
                    lastSeen = lastSession.timestamp,
                    locationsSeen = locationsCount
                ))
            }
        }

        return alerts.sortedByDescending { it.sessionsSeen }
    }

    /**
     * Get the full sighting history for a specific device.
     */
    fun getDeviceHistory(macAddress: String): List<DeviceSession> {
        return deviceHistory[macAddress]?.toList() ?: emptyList()
    }

    /**
     * Get all tracked devices.
     */
    fun getAllTrackedDevices(): Map<String, List<DeviceSession>> {
        return deviceHistory.toMap()
    }

    /**
     * Clear all tracking history.
     */
    fun clearHistory() {
        deviceHistory.clear()
    }

    /**
     * Remove devices that haven't been seen in the specified time window.
     */
    fun cleanupOldEntries(maxAgeMs: Long = 24 * 60 * 60_000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        deviceHistory.entries.removeIf { (_, sessions) ->
            sessions.all { it.timestamp < cutoff }
        }
    }
}
