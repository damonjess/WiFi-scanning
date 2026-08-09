package com.damon.wifiaudit.scan

import android.content.Context
import android.net.wifi.WifiManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ThrottleAwareScanScheduler(
    private val context: Context,
    private val wifiManager: WifiManager
) {
    // Android 9+ foreground budget: 4 scans per rolling 2-minute window
    private val scanTimestamps = ArrayDeque<Long>()
    private val windowMs = 2 * 60 * 1000L
    private val maxScansPerWindow = 4

    private val awaitingResult = AtomicBoolean(false)
    private val lastRequestTime = AtomicLong(0L)

    sealed class ScanOutcome {
        data object Requested : ScanOutcome()
        data object BudgetExhausted : ScanOutcome()
        data object LikelyThrottledByOs : ScanOutcome()
    }

    fun requestScan(): ScanOutcome {
        val now = System.currentTimeMillis()
        pruneOldTimestamps(now)

        if (scanTimestamps.size >= maxScansPerWindow) {
            return ScanOutcome.BudgetExhausted
        }

        @Suppress("DEPRECATION")
        val accepted = wifiManager.startScan()
        if (!accepted) {
            return ScanOutcome.LikelyThrottledByOs
        }

        scanTimestamps.addLast(now)
        lastRequestTime.set(now)
        awaitingResult.set(true)
        return ScanOutcome.Requested
    }

    /** Call this from the SCAN_RESULTS_AVAILABLE_ACTION receiver. */
    fun onScanResultsReceived() {
        awaitingResult.set(false)
    }

    /**
     * Heuristic: if a scan was requested >8s ago and we never got the
     * broadcast back, the OS almost certainly silently dropped it —
     * common when the app is backgrounded or the 30-min background
     * throttle window hasn't elapsed.
     */
    fun isLikelyThrottled(): Boolean {
        val elapsed = System.currentTimeMillis() - lastRequestTime.get()
        return awaitingResult.get() && elapsed > 8_000L
    }

    private fun pruneOldTimestamps(now: Long) {
        while (scanTimestamps.isNotEmpty() && now - scanTimestamps.first() > windowMs) {
            scanTimestamps.removeFirst()
        }
    }

    fun nextAvailableSlotMs(): Long {
        if (scanTimestamps.size < maxScansPerWindow) return 0L
        val oldest = scanTimestamps.first()
        return (oldest + windowMs) - System.currentTimeMillis()
    }
}
