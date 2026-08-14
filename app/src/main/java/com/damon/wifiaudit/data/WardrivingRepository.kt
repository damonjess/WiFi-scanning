package com.damon.wifiaudit.data

import androidx.room.Transaction
import org.json.JSONArray
import org.json.JSONObject

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

        val updatedWifi = wifiSightings.map { it.copy(locationId = locationId) }
        val updatedBle = bleSightings.map { it.copy(locationId = locationId) }

        if (updatedWifi.isNotEmpty()) {
            wifiDao.insertAll(updatedWifi)
        }
        if (updatedBle.isNotEmpty()) {
            bleDao.insertAll(updatedBle)
        }

        // Add to API Queue for batch upload
        val payload = createPayload(location, updatedWifi, updatedBle)
        db.apiQueueDao().insert(ApiQueueItem(payload = payload))
    }

    private fun createPayload(
        location: LocationFix,
        wifi: List<WifiSighting>,
        ble: List<BleSighting>
    ): String {
        val json = JSONObject()
        json.put("lat", location.latitude)
        json.put("lon", location.longitude)
        json.put("time", location.timestamp)

        val wifiArray = JSONArray()
        wifi.forEach {
            val w = JSONObject()
            w.put("bssid", it.bssid)
            w.put("ssid", it.ssid)
            w.put("rssi", it.rssi)
            w.put("model", it.deviceModel ?: JSONObject.NULL)
            wifiArray.put(w)
        }
        json.put("wifi", wifiArray)

        val bleArray = JSONArray()
        ble.forEach {
            val b = JSONObject()
            b.put("mac", it.macAddress)
            b.put("name", it.deviceName ?: JSONObject.NULL)
            b.put("rssi", it.rssi)
            b.put("model", it.deviceModel ?: JSONObject.NULL)
            bleArray.put(b)
        }
        json.put("ble", bleArray)

        return json.toString()
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
