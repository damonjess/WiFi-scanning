package com.damon.wifiaudit.ble

import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.damon.wifiaudit.MainActivity
import com.damon.wifiaudit.R
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.entity.ProximityRule
import com.damon.wifiaudit.util.SecurityGateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ProximityMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val activeConnections = ConcurrentHashMap<String, LightGattManager>()
    private val triggeredRules = ConcurrentHashMap.newKeySet<String>() // debounce

    // mac -> last wall-clock millis a usable (in-range) advertisement was seen
    private val lastSeenInRange = ConcurrentHashMap<String, Long>()

    private lateinit var db: AppDatabase
    private lateinit var wakeLock: PowerManager.WakeLock

    @Volatile
    private var cachedRulesSnapshot: List<ProximityRule> = emptyList()

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiAudit::ProximityMonitor")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring proximity rules…"))
        // Long-running monitor: hold indefinitely while the service lives.
        // App standby may reclaim it; re-acquired on every onStartCommand.
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        serviceScope.launch {
            // Live reload: whenever rules change, restart the scan config.
            // Poll cheaply instead of a DB Flow so filters can include MACs.
            var lastRulesSignature: String? = null
            while (isActive) {
                val rules = db.proximityRuleDao().getActiveRules().first()
                val signature = rules.joinToString("|") { "${it.id}:${it.targetMac}:${it.rssiThreshold}:${it.rssiThresholdMax}:${it.lockAppOnExit}" }
                if (signature != lastRulesSignature) {
                    lastRulesSignature = signature
                    if (rules.isEmpty()) {
                        stopSelf()
                        return@launch
                    }
                    cachedRulesSnapshot = rules
                    restartScan(rules)
                }
                runAbsenceTimeoutCheck(rules)
                delay(2_000L)
            }
        }
    }

    private fun restartScan(rules: List<ProximityRule>) {
        scanCallback?.let { cb ->
            try {
                scanner?.stopScan(cb)
            } catch (_: SecurityException) {
            }
        }
        scanCallback = null

        val targetMacs = rules.map { it.targetMac.uppercase() }.filter { it != "ANY" }
        val filters = if (targetMacs.isNotEmpty()) {
            targetMacs.map { mac ->
                ScanFilter.Builder()
                    .setDeviceAddress(mac)
                    .build()
            }
        } else null

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false) // include BLE 5 extended advertisements of tracked devices
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                handleScanResult(result ?: return)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it) }
            }
        }

        try {
            scanner?.startScan(filters, settings, scanCallback!!)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting proximity scan", e)
        }
    }

    private fun handleScanResult(res: ScanResult) {
        val mac = res.device.address.uppercase()
        val rssi = res.rssi

        if (isInRange(rssi, rulesFor(mac))) {
            lastSeenInRange[mac] = System.currentTimeMillis()
        }

        serviceScope.launch {
            evaluateRules(mac, rssi, res.device)
        }
    }

    private fun rulesFor(mac: String): List<ProximityRule> {
        // Cheap in-range heuristic uses the snapshot taken at scan start.
        return cachedRulesSnapshot.filter { it.targetMac.uppercase() == mac || it.targetMac == "ANY" }
    }

    /**
     * SECURITY_KEY lockdown must also fire when the key goes fully out of
     * range — at that point the OS stops delivering advertisements entirely,
     * so a "weaker than threshold" RSSI callback never arrives. Track the last
     * time each target was seen at a usable signal; if a lock-enabled target
     * stays silent past the timeout, lock.
     */
    private fun runAbsenceTimeoutCheck(rules: List<ProximityRule>) {
        val now = System.currentTimeMillis()
        for (rule in rules) {
            if (!rule.lockAppOnExit || rule.ruleType != "SECURITY_KEY") continue
            val mac = rule.targetMac.uppercase()
            if (mac == "ANY") continue // no single MAC to track for generic rules

            val lastSeen = lastSeenInRange[mac] ?: continue // never seen: don't lock on cold start
            val silentFor = now - lastSeen
            if (silentFor > ABSENCE_TIMEOUT_MS) {
                val key = "${rule.id}_absent"
                if (triggeredRules.add(key)) {
                    Log.i(TAG, "SECURITY_KEY '${rule.name}' absent for ${silentFor / 1000}s — locking")
                    executeSecurityKey(rule, rssi = Int.MIN_VALUE)
                    updateNotification("🔒 Security Key: ${rule.name} out of range — locked")
                }
            } else {
                triggeredRules.remove("${rule.id}_absent")
            }
        }
    }

    private fun isInRange(rssi: Int, applicableRules: List<ProximityRule>): Boolean {
        // "In range" = passes every enabled rule's window, i.e. not weaker
        // than the lowest (closest) threshold configured for this device.
        val thresholds = applicableRules.mapNotNull { it.rssiThreshold }
        if (thresholds.isEmpty()) return true
        return thresholds.none { rssi < it }
    }

    private suspend fun evaluateRules(mac: String, rssi: Int, device: BluetoothDevice) {
        val rules = db.proximityRuleDao().getRulesForMac(mac)
            .filter { it.isEnabled }

        for (rule in rules) {
            val shouldTrigger = when {
                rule.rssiThreshold != null && rssi < rule.rssiThreshold -> true
                rule.rssiThresholdMax != null && rssi > rule.rssiThresholdMax -> true
                else -> false
            }

            if (shouldTrigger) {
                val key = "${rule.id}_$mac"
                if (triggeredRules.add(key)) { // debounce
                    executeRule(rule, device, rssi)
                }
            } else {
                triggeredRules.remove("${rule.id}_$mac")
                if (rule.ruleType == "SECURITY_KEY") {
                    // Back in range after an RSSI trigger — re-arm both paths.
                    triggeredRules.remove("${rule.id}_absent")
                }
            }
        }
    }

    private suspend fun executeRule(rule: ProximityRule, device: BluetoothDevice, rssi: Int) {
        when (rule.ruleType) {
            "TIME_SYNC" -> executeTimeSync(device, rule)
            "PROXIMITY_ACTION" -> executeSmartHomeAction(device, rule, rssi)
            "SECURITY_KEY" -> executeSecurityKey(rule, rssi)
        }
    }

    private suspend fun executeTimeSync(device: BluetoothDevice, rule: ProximityRule) {
        val manager = getOrCreateGatt(device)
        manager.state.first { it is LightGattManager.State.Ready }

        val payload = TimeSyncManager.buildCurrentTimePayload()
        manager.writeCharacteristic(
            TimeSyncManager.CTS_SERVICE,
            TimeSyncManager.CURRENT_TIME_CHAR,
            payload
        )

        updateNotification("Synced time to ${device.address}")
    }

    private suspend fun executeSmartHomeAction(device: BluetoothDevice, rule: ProximityRule, rssi: Int) {
        if (rule.serviceUuid != null && rule.characteristicUuid != null && rule.writePayloadHex != null) {
            val manager = getOrCreateGatt(device)
            manager.state.first { it is LightGattManager.State.Ready }

            val bytes = rule.writePayloadHex.replace(" ", "").chunked(2)
                .mapNotNull { it.toIntOrNull(16)?.toByte() }
                .toByteArray()

            manager.writeCharacteristic(
                UUID.fromString(rule.serviceUuid),
                UUID.fromString(rule.characteristicUuid),
                bytes
            )
        }

        if (rule.showNotification) {
            sendActionNotification(rule.name, "RSSI ${rssi}dBm — action triggered")
        }
    }

    private fun executeSecurityKey(rule: ProximityRule, rssi: Int) {
        if (rule.lockAppOnExit) {
            SecurityGateManager.lock()
            sendBroadcast(Intent(ACTION_LOCKDOWN).apply {
                setPackage(packageName)
                putExtra("rule_name", rule.name)
            })
        }
    }

    private fun getOrCreateGatt(device: BluetoothDevice): LightGattManager {
        return activeConnections.getOrPut(device.address) {
            LightGattManager(this, device).apply {
                connect()
            }
        }
    }

    private fun sendActionNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proximity Monitor")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop scanning first; never early-return before the rest of cleanup.
        try {
            scanCallback?.let { cb -> scanner?.stopScan(cb) }
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
            // Already stopped
        }
        scanCallback = null

        // Release every GATT connection.
        activeConnections.values.forEach { manager ->
            runCatching { manager.release() }
        }
        activeConnections.clear()

        serviceScope.cancel()

        if (wakeLock.isHeld) {
            runCatching { wakeLock.release() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ProximityMonitor"
        const val CHANNEL_ID = "proximity_monitor"
        const val NOTIFICATION_ID = 1001
        const val ACTION_LOCKDOWN = "com.damon.wifiaudit.ACTION_LOCKDOWN"

        /** No usable advertisement from a security key for this long => locked. */
        const val ABSENCE_TIMEOUT_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, ProximityMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProximityMonitorService::class.java))
        }
    }
}
