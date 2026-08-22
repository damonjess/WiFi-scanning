package com.damon.wifiaudit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.damon.wifiaudit.data.dao.BleGattSnapshotDao
import com.damon.wifiaudit.data.dao.OuiVendorDao
import com.damon.wifiaudit.data.dao.RssiHeatmapDao
import com.damon.wifiaudit.data.dao.StandardGattUuidDao
import com.damon.wifiaudit.data.dao.ProximityRuleDao
import com.damon.wifiaudit.data.entity.BleGattSnapshot
import com.damon.wifiaudit.data.entity.OuiVendor
import com.damon.wifiaudit.data.entity.ProximityRule
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import com.damon.wifiaudit.data.entity.StandardGattUuid
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ApScanEntity::class, LocationFix::class, WifiSighting::class,
        BleSighting::class, ScanSession::class, ApiQueueItem::class,
        BleGattSnapshot::class, OuiVendor::class, StandardGattUuid::class,
        RssiHeatmapPoint::class, ProximityRule::class
    ],
    version = 11,
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
    abstract fun ouiVendorDao(): OuiVendorDao
    abstract fun standardGattUuidDao(): StandardGattUuidDao
    abstract fun rssiHeatmapDao(): RssiHeatmapDao
    abstract fun proximityRuleDao(): ProximityRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private var libraryLoaded = false

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (!libraryLoaded) {
                    try {
                        // sqlcipher-android ships 16 KB-page-compatible native libraries.
                        // Loading it explicitly must happen before Room opens the encrypted database.
                        System.loadLibrary("sqlcipher")
                        libraryLoaded = true
                    } catch (e: UnsatisfiedLinkError) {
                        throw RuntimeException(
                            "Failed to load SQLCipher native library. " +
                            "Ensure you are using net.zetetic:sqlcipher-android:4.6.1+ " +
                            "and that the APK includes the native .so for this device's ABI.",
                            e
                        )
                    }
                }
                val passphrase = SecureKeyProvider.getOrCreateDbPassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_audit_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                    )
                    .fallbackToDestructiveMigration()

                val db = builder.build()
                
                // Validate encryption key by attempting to open the database.
                // If the key is wrong (e.g. after Keystore reset or corruption),
                // we must delete the unreadable file to allow a fresh start.
                try {
                    db.openHelper.writableDatabase
                } catch (e: Exception) {
                    if (e.message?.contains("file is not a database") == true ||
                        e.message?.contains("file is encrypted") == true) {
                        context.deleteDatabase("wifi_audit_encrypted.db")
                        // Return a new instance after cleanup
                        return Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "wifi_audit_encrypted.db"
                        )
                            .openHelperFactory(factory)
                            .fallbackToDestructiveMigration()
                            .build()
                            .also { INSTANCE = it }
                    }
                    throw e
                }

                db.also { INSTANCE = it }
            }
        }
    }
}
