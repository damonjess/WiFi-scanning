package com.damon.wifiaudit.ble

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.damon.wifiaudit.MainActivity
import com.damon.wifiaudit.R
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.entity.ProximityRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

class ProximityMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val activeConnections = mutableMapOf<String, LightGattManager>()
    private val triggeredRules = mutableSetOf<String>() // debounce

    private lateinit var db: AppDatabase
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiAudit::ProximityMonitor")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring proximity rules…"))
        wakeLock.acquire(10 * 60 * 1000L)
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        serviceScope.launch {
            val rules = db.proximityRuleDao().getActiveRules().first()
            if (rules.isEmpty()) {
                stopSelf()
                return@launch
            }

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
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val res = result ?: return
                    val mac = res.device.address.uppercase()
                    val rssi = res.rssi

                    serviceScope.launch {
                        evaluateRules(mac, rssi, res.device)
                    }
                }
            }

            try {
                scanner?.startScan(filters, settings, scanCallback!!)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
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
            sendBroadcast(Intent(ACTION_LOCKDOWN).apply {
                setPackage(packageName)
                putExtra("rule_name", rule.name)
            })
            updateNotification("🔒 Security Key: ${rule.name} out of range — locked")
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
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) CHANNEL_ID else ""
        return NotificationCompat.Builder(this, channelId)
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
        scanner?.stopScan(scanCallback ?: return)
        activeConnections.values.forEach { it.release() }
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "proximity_monitor"
        const val NOTIFICATION_ID = 1001
        const val ACTION_LOCKDOWN = "com.damon.wifiaudit.ACTION_LOCKDOWN"

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
