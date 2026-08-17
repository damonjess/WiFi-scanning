package com.damon.wifiaudit.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
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
import com.damon.wifiaudit.ble.BleScanManager
import com.damon.wifiaudit.data.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class WifiRfScanService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wifiManager: WifiManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase
    private lateinit var scanScheduler: ThrottleAwareScanScheduler
    private lateinit var bleScanManager: BleScanManager

    private var currentSessionId: Long = -1L
    private val coordinator by lazy { ScanCycleCoordinator(WardrivingRepository(db)) }

    @Volatile private var lastKnownLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastKnownLocation = it
                ScanStatusRepository.updateLocation(it)
            }
        }
    }

    private val scanResultsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scanScheduler.onScanResultsReceived()
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            handleScanResults(success)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getInstance(applicationContext)
        bleScanManager = BleScanManager(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                scanResultsReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                scanResultsReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        ScanStatusRepository.setServiceRunning(true)

        serviceScope.launch {
            currentSessionId = coordinator.repository.startSession()
            android.util.Log.i("WifiRfScanService", "Started session $currentSessionId")
        }

        startHighAccuracyLocationUpdates()
        bleScanManager.startScan()
        startScanLoop()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startHighAccuracyLocationUpdates() {
        if (!hasLocationPermission()) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(2f)
            .setWaitForAccurateLocation(true)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, mainLooper
        )
    }

    private fun currentUsableLocation(): Location? {
        val location = lastKnownLocation ?: return null
        val ageMillis = System.currentTimeMillis() - location.time
        return location.takeIf {
            it.hasAccuracy() &&
                it.accuracy <= MAXIMUM_COMMIT_ACCURACY_METERS &&
                ageMillis in 0..MAX_LOCATION_AGE_MILLIS
        }
    }

    private fun startScanLoop() {
        scanScheduler = ThrottleAwareScanScheduler(applicationContext, wifiManager)
        serviceScope.launch {
            while (isActive) {
                if (hasLocationPermission()) {
                    when (scanScheduler.requestScan()) {
                        is ThrottleAwareScanScheduler.ScanOutcome.BudgetExhausted -> {
                            val waitMs = scanScheduler.nextAvailableSlotMs().coerceAtLeast(1000L)
                            delay(waitMs)
                            continue
                        }
                        is ThrottleAwareScanScheduler.ScanOutcome.LikelyThrottledByOs -> {
                            // Fall back to whatever is already cached in scanResults —
                            // still useful, just staler
                            handleScanResults(triggeredByUs = false)
                            delay(30_000L)
                        }
                        is ThrottleAwareScanScheduler.ScanOutcome.Requested -> {
                            delay(30_000L)
                        }
                    }
                } else {
                    delay(5_000L)
                }
            }
        }
    }

    private fun handleScanResults(triggeredByUs: Boolean) {
        if (!hasLocationPermission()) return

        val results = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            return
        }

        val loc = currentUsableLocation() ?: return
        if (currentSessionId < 0) return

        serviceScope.launch {
            coordinator.commitCycle(
                sessionId = currentSessionId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                wifiResults = results,
                bleResults = bleScanManager.devices.value.values.toList()
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val channelId = "wifi_audit_scan_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "WiFi Audit Scanning", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("WiFi audit scan active")
            .setContentText("Logging APs + GPS to encrypted local database")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        val flushJob = serviceScope.launch {
            if (currentSessionId >= 0) {
                coordinator.repository.endSession(currentSessionId)
            }
        }

        runBlocking {
            withTimeoutOrNull(500) { flushJob.join() }
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        bleScanManager.stopScan()
        unregisterReceiver(scanResultsReceiver)

        ScanStatusRepository.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val MAXIMUM_COMMIT_ACCURACY_METERS = 75f
        private const val MAX_LOCATION_AGE_MILLIS = 15_000L
    }
}
