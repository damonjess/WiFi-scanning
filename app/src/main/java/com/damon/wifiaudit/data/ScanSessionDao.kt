package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class SessionSummary(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val label: String?,
    val wifiCount: Int,
    val bleCount: Int,
    val fixCount: Int
)

@Dao
interface ScanSessionDao {

    @Insert
    suspend fun insert(session: ScanSession): Long

    @Query("UPDATE scan_sessions SET endTime = :endTime WHERE id = :sessionId")
    suspend fun markEnded(sessionId: Long, endTime: Long)

    @Query("""
        SELECT
            s.id, s.startTime, s.endTime, s.label,
            (SELECT COUNT(*) FROM wifi_sightings w
                JOIN location_fixes l ON w.locationId = l.id
                WHERE l.sessionId = s.id) AS wifiCount,
            (SELECT COUNT(*) FROM ble_sightings b
                JOIN location_fixes l ON b.locationId = l.id
                WHERE l.sessionId = s.id) AS bleCount,
            (SELECT COUNT(*) FROM location_fixes l WHERE l.sessionId = s.id) AS fixCount
        FROM scan_sessions s
        ORDER BY s.startTime DESC
    """)
    suspend fun getAllSessionSummaries(): List<SessionSummary>

    @Query("SELECT * FROM location_fixes WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getFixesForSession(sessionId: Long): List<LocationFix>
}
