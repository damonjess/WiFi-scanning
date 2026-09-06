package com.damon.wifiaudit.ble

import android.bluetooth.le.ScanSettings

/**
 * Builds permissive [ScanSettings] that maximise beacon detection coverage.
 *
 * Key fix: [ScanSettings.Builder.setLegacy] is `false`, which enables scanning
 * of BLE 5 extended advertisements — without it, modern beacons that only use
 * extended (non-legacy) advertising are silently dropped by the OS.
 *
 * Two profiles:
 *  - [foreground]: LOW_LATENCY + ALL_MATCHES — fastest detection, used while
 *    the user is actively watching the scan screen.
 *  - [wardriving]: BALANCED + ALL_MATCHES — continuous background scanning
 *    that catches every beacon sighting (no de-dup) without the battery cost
 *    of LOW_LATENCY while driving.
 *
 * minSdk is 32, so all BLE 5 scan APIs are unconditionally available.
 */
object BleScanSettings {

    fun foreground(): ScanSettings = base(ScanSettings.SCAN_MODE_LOW_LATENCY)

    fun wardriving(): ScanSettings = base(ScanSettings.SCAN_MODE_BALANCED)

    private fun base(mode: Int): ScanSettings = ScanSettings.Builder()
        .setScanMode(mode)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setLegacy(false)                                  // include BLE 5 extended ads (legacy + extended)
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // report duplicates
        .build()
}
