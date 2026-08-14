package com.damon.wifiaudit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.damon.wifiaudit.data.dao.BleGattSnapshotDao
import com.damon.wifiaudit.data.entity.BleGattSnapshot
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ApScanEntity::class, LocationFix::class, WifiSighting::class,
        BleSighting::class, ScanSession::class, ApiQueueItem::class,
        BleGattSnapshot::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apScanDao(): ApScanDao
    abstract fun locationFixDao(): LocationFixDao
    abstract fun wifiSightingDao(): WifiSightingDao
    abstract fun bleSightingDao(): BleSightingDao
    abstract fun sightingHistoryDao(): SightingHistoryDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun apiQueueDao(): ApiQueueDao
    abstract fun bleGattSnapshotDao(): BleGattSnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context)
                val passphrase = SecureKeyProvider.getOrCreateDbPassphrase(context)
                val factory = SupportFactory(passphrase)

                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_audit_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
