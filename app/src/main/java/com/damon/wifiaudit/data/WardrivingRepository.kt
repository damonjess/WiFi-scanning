package com.damon.wifiaudit.data

import androidx.room.Transaction

class WardrivingRepository(
    private val db: AppDatabase
) {
    private val locationDao = db.locationFixDao()
    private val wifiDao = db.wifiSightingDao()
    private val bleDao = db.bleSightingDao()

    /**
     * Atomically writes one location fix plus all Wi-Fi/BLE sightings captured
     * at that fix. If any part fails, the whole batch rolls back — you never
     * end up with a location fix that has partial/missing sighting data.
     */
    @Transaction
    suspend fun recordFix(
        location: LocationFix,
        wifiSightings: List<WifiSighting>,
        bleSightings: List<BleSighting>
    ) {
        val locationId = locationDao.insert(location)

        if (wifiSightings.isNotEmpty()) {
            wifiDao.insertAll(wifiSightings.map { it.copy(locationId = locationId) })
        }
        if (bleSightings.isNotEmpty()) {
            bleDao.insertAll(bleSightings.map { it.copy(locationId = locationId) })
        }
    }

    suspend fun startSession(): Long {
        return db.scanSessionDao().insert(ScanSession(startTime = System.currentTimeMillis(), endTime = null))
    }

    suspend fun endSession(sessionId: Long) {
        db.scanSessionDao().markEnded(sessionId, System.currentTimeMillis())
    }

    suspend fun deleteFix(locationId: Long) {
        // Cascade delete handles wifi_sightings + ble_sightings automatically
        locationDao.deleteById(locationId)
    }
}
