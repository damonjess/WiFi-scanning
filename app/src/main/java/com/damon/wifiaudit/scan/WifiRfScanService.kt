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

    private val coordinator by lazy { ScanCycleCoordinator(WardrivingRepository(db)) }

    @Volatile private var lastKnownLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { lastKnownLocation = it }
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
            .build()

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, mainLooper
        )
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

        val loc = lastKnownLocation ?: return

        serviceScope.launch {
            coordinator.commitCycle(
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
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        bleScanManager.stopScan()
        unregisterReceiver(scanResultsReceiver)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}
