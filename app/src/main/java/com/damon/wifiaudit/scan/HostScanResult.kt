package com.damon.wifiaudit.scan

data class HostScanResult(
    val ipAddress: String,
    val isAlive: Boolean,
    val openPorts: List<Int>,
    val macAddress: String?,
    val responseTimeMs: Long
)
