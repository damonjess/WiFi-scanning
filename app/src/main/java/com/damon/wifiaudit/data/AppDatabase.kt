package com.damon.wifiaudit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [ApScanEntity::class, LocationFix::class, WifiSighting::class, BleSighting::class, ScanSession::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apScanDao(): ApScanDao
    abstract fun locationFixDao(): LocationFixDao
    abstract fun wifiSightingDao(): WifiSightingDao
    abstract fun bleSightingDao(): BleSightingDao
    abstract fun sightingHistoryDao(): SightingHistoryDao
    abstract fun scanSessionDao(): ScanSessionDao

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
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
