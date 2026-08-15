package com.damon.wifiaudit.ble

import com.damon.wifiaudit.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

object TimeSyncManager {
    val CTS_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    val CURRENT_TIME_CHAR = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")

    /**
     * Builds the Current Time characteristic payload per Bluetooth SIG spec.
     */
    fun buildCurrentTimePayload(): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)

        // Date-Time (7 bytes): Year(2), Month, Day, Hours, Minutes, Seconds
        buf.putShort(cal.get(Calendar.YEAR).toShort())
        buf.put((cal.get(Calendar.MONTH) + 1).toByte()) // 1-12
        buf.put(cal.get(Calendar.DAY_OF_MONTH).toByte())
        buf.put(cal.get(Calendar.HOUR_OF_DAY).toByte())
        buf.put(cal.get(Calendar.MINUTE).toByte())
        buf.put(cal.get(Calendar.SECOND).toByte())

        // Day of Week (1 = Monday ... 7 = Sunday)
        var dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (dow == 0) dow = 7
        buf.put(dow.toByte())

        // Fractions256 (1/256 of a second)
        buf.put((cal.get(Calendar.MILLISECOND) * 256 / 1000).toByte())

        // Adjust Reason: 0x01 = Manual time update
        buf.put(0x01.toByte())

        return buf.array()
    }

    suspend fun findAndSync(mac: String, gattManager: LightGattManager, db: AppDatabase): Boolean {
        val rules = db.proximityRuleDao().getRulesForMac(mac)
        if (rules.any { it.ruleType == "TIME_SYNC" && it.isEnabled }) {
             val payload = buildCurrentTimePayload()
             gattManager.writeCharacteristic(CTS_SERVICE, CURRENT_TIME_CHAR, payload)
             return true
        }
        return false
    }
}
