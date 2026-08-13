package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,          // null while a scan is actively running
    val label: String? = null    // optional user-given name, e.g. "Tuesday drive"
)
