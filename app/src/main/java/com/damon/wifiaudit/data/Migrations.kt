package com.damon.wifiaudit.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scan_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                label TEXT
            )
        """)

        db.execSQL("""
            INSERT INTO scan_sessions (startTime, endTime, label)
            SELECT IFNULL(MIN(timestamp), 0), MAX(timestamp), 'Legacy data'
            FROM location_fixes
        """)

        db.execSQL("ALTER TABLE location_fixes ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            UPDATE location_fixes
            SET sessionId = (SELECT id FROM scan_sessions ORDER BY id DESC LIMIT 1)
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS index_location_fixes_sessionId ON location_fixes(sessionId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wifi_sightings ADD COLUMN deviceModel TEXT")
        db.execSQL("ALTER TABLE ble_sightings ADD COLUMN deviceModel TEXT")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS api_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                payload TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                isPending INTEGER NOT NULL DEFAULT 1
            )
        """)
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE tracked_devices (
                macAddress TEXT PRIMARY KEY NOT NULL,
                displayName TEXT,
                vendor TEXT,
                deviceType TEXT NOT NULL DEFAULT 'UNKNOWN',
                isFavorite INTEGER NOT NULL DEFAULT 0,
                firstSeenAt INTEGER NOT NULL,
                lastSeenAt INTEGER NOT NULL,
                detectCount INTEGER NOT NULL DEFAULT 1,
                tags TEXT NOT NULL DEFAULT '',
                rawAdvData TEXT,
                connectionState TEXT NOT NULL DEFAULT 'SCANNED'
            )
        """)
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE ble_gatt_services (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                deviceMac TEXT NOT NULL,
                uuid TEXT NOT NULL,
                serviceType INTEGER NOT NULL,
                instanceId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE ble_gatt_characteristics (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serviceId INTEGER NOT NULL,
                uuid TEXT NOT NULL,
                properties INTEGER NOT NULL,
                permissions INTEGER NOT NULL,
                instanceId INTEGER NOT NULL,
                descriptorsJson TEXT NOT NULL DEFAULT '[]'
            )
        """)
        db.execSQL("""
            CREATE TABLE ble_raw_fragments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                deviceMac TEXT NOT NULL,
                hexData TEXT NOT NULL,
                rssi INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
    }
}
