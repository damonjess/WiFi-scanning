package com.damon.wifiaudit.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BleScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.ble.IBeaconParser
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WardrivingRepository
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class WardrivingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var wifiManager: WifiManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var scanScheduler: ThrottleAwareScanScheduler
    private lateinit var coordinator: ScanCycleCoordinator
    private var bluetoothManager: BluetoothManager? = null

    private var currentSessionId: Long = -1L
    @Volatile private var lastKnownLocation: Location? = null
    private var lastCommittedLocation: Location? = null
    private val bleDeviceMap = mutableMapOf<String, BleDeviceInfo>()
    private val alertedDevices = mutableSetOf<String>()

    // ---- Wi-Fi ----
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scanScheduler.onScanResultsReceived()
            if (!hasFineLocation()) {
                android.util.Log.w("WardrivingService", "WiFi scan received but FINE_LOCATION missing")
                return
            }
            val results = try { 
                wifiManager.scanResults 
            } catch (e: SecurityException) { 
                android.util.Log.e("WardrivingService", "SecurityException reading WiFi results", e)
                return 
            }
            android.util.Log.d("WardrivingService", "WiFi scan results: ${results.size} APs found")
            ScanStatusRepository.updateWifiResults(results)
        }
    }

    // ---- BLE ----
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BleScanResult) {
            upsertBle(result)
        }
        override fun onBatchScanResults(results: MutableList<BleScanResult>) {
            results.forEach { upsertBle(it) }
        }
        override fun onScanFailed(errorCode: Int) { /* log if desired */ }
    }

    private fun upsertBle(result: BleScanResult) {
        val record = result.scanRecord
        val iBeacon = IBeaconParser.parse(record)
        
        val deviceName = try {
            record?.deviceName ?: result.device.name
        } catch (_: SecurityException) {
            null
        }

        val info = BleDeviceInfo(
            macAddress = result.device.address,
            deviceName = deviceName,
            rssi = result.rssi,
            txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
            iBeaconMajor = iBeacon?.major,
            iBeaconMinor = iBeacon?.minor,
            iBeaconUuid = iBeacon?.uuid,
            lastSeenMillis = System.currentTimeMillis(),
        )
        bleDeviceMap[info.macAddress] = info
        ScanStatusRepository.updateBleDevices(bleDeviceMap.values.toList())
    }

    // ---- GPS ----
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastKnownLocation = it
                ScanStatusRepository.updateLocation(it.latitude, it.longitude)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        scanScheduler = ThrottleAwareScanScheduler(applicationContext, wifiManager)

        val db = AppDatabase.getInstance(applicationContext)
        coordinator = ScanCycleCoordinator(WardrivingRepository(db))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wifiReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                RECEIVER_NOT_EXPORTED,
            )
        } else {
            registerReceiver(
                wifiReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            currentSessionId = coordinator.repository.startSession()
            android.util.Log.i("WardrivingService", "Started session $currentSessionId")
        }

        startLocationUpdates()
        startBleScan()
        startWifiScanLoop()
        startCommitLoop()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasFineLocation()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val hasBlePermission = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasBlePermission) return
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        try {
            scanner.startScan(null, settings, bleScanCallback)
        } catch (_: SecurityException) { /* permission revoked mid-flight */ }
    }

    private fun startWifiScanLoop() {
        serviceScope.launch {
            while (isActive) {
                when (scanScheduler.requestScan()) {
                    is ThrottleAwareScanScheduler.ScanOutcome.BudgetExhausted -> {
                        delay(scanScheduler.nextAvailableSlotMs().coerceAtLeast(1000L))
                    }
                    else -> delay(30_000L)
                }
            }
        }
    }

    /**
     * This is the actual "scan cycle" cadence — every 15s, whatever WiFi/BLE
     * results are currently buffered in ScanStatusRepository get committed as
     * ONE atomic transaction tied to the most recent GPS fix. Radios keep
     * streaming independently between commits; this loop just periodically
     * snapshots and flushes to encrypted storage.
     */
    private fun startCommitLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(15_000L)
                val snapshot = ScanStatusRepository.snapshot.value
                val loc = lastKnownLocation ?: continue
                if (snapshot.wifiResults.isEmpty() && snapshot.bleDevices.isEmpty()) continue
                if (currentSessionId < 0) continue

                // 50m Distance Filter: Only commit if we've moved significantly
                val lastLoc = lastCommittedLocation
                if (lastLoc != null && loc.distanceTo(lastLoc) < 50f) {
                    continue
                }

                // Watchdog alerts
                val newMatches = snapshot.wifiResults.mapNotNull { r ->
                    val vendor = OuiVendorLookup.lookup(r.BSSID)
                    SurveillanceDeviceWatchdog.classifyWifi(r.SSID, vendor)?.let { r.SSID to it }
                } + snapshot.bleDevices.mapNotNull { d ->
                    val vendor = OuiVendorLookup.lookup(d.macAddress)
                    SurveillanceDeviceWatchdog.classifyBle(d.deviceName, vendor)?.let { (d.deviceName ?: d.macAddress) to it }
                }

                newMatches.forEach { (label, match) ->
                    if (alertedDevices.add(label)) {
                        showWatchdogNotification(match.category.label, label)
                    }
                }

                android.util.Log.d("WardrivingService", "Committing cycle: ${snapshot.wifiResults.size} WiFi, ${snapshot.bleDevices.size} BLE at ${loc.latitude}, ${loc.longitude}")
                coordinator.commitCycle(
                    sessionId = currentSessionId,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    wifiResults = snapshot.wifiResults,
                    bleResults = snapshot.bleDevices
                )
                UploadWorker.enqueue(applicationContext)
                lastCommittedLocation = loc
                
                ScanStatusRepository.markCommitted()
                ScanStatusRepository.clearWifiResults()
                ScanStatusRepository.clearBleDevices()
                bleDeviceMap.clear()
            }
        }
    }

    private fun showWatchdogNotification(category: String, deviceLabel: String) {
        val channelId = "watchdog_alerts"
        val channel = NotificationChannel(channelId, "Watchdog Alerts", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("$category detected")
            .setContentText(deviceLabel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(deviceLabel.hashCode(), notification)
    }

    private fun hasFineLocation() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val channelId = "wardriving_channel"
        val channel = NotificationChannel(channelId, "Wardriving Active", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Wardriving scan active")
            .setContentText("Logging Wi-Fi, BLE, and GPS to encrypted storage")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        bluetoothManager?.adapter?.bluetoothLeScanner?.let {
            try { it.stopScan(bleScanCallback) } catch (e: SecurityException) {}
        }
        unregisterReceiver(wifiReceiver)

        // Flush whatever's still buffered before the service dies
        val snapshot = ScanStatusRepository.snapshot.value
        val loc = lastKnownLocation
        if (loc != null && currentSessionId >= 0 && (snapshot.wifiResults.isNotEmpty() || snapshot.bleDevices.isNotEmpty())) {
            runBlocking {
                android.util.Log.d("WardrivingService", "Final flush on stop: ${snapshot.wifiResults.size} WiFi, ${snapshot.bleDevices.size} BLE")
                coordinator.commitCycle(
                    sessionId = currentSessionId,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    wifiResults = snapshot.wifiResults,
                    bleResults = snapshot.bleDevices
                )
                UploadWorker.enqueue(applicationContext)
            }
        }

        if (currentSessionId >= 0) {
            runBlocking {
                coordinator.repository.endSession(currentSessionId)
            }
        }

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4202
    }
}
