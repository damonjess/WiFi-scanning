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
            CREATE TABLE IF NOT EXISTS ble_gatt_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                macAddress TEXT NOT NULL,
                deviceName TEXT,
                servicesJson TEXT NOT NULL,
                serviceCount INTEGER NOT NULL,
                characteristicCount INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_gatt_mac ON ble_gatt_snapshots(macAddress)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS oui_vendors (
                oui TEXT NOT NULL PRIMARY KEY,
                vendorName TEXT NOT NULL,
                country TEXT,
                address TEXT
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_oui ON oui_vendors(oui)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS standard_gatt_uuids (
                uuid TEXT NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY(uuid, type)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_gatt_uuid ON standard_gatt_uuids(uuid)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add scanRecord to ble_sightings
        db.execSQL("ALTER TABLE ble_sightings ADD COLUMN scanRecord BLOB")
        
        // Create heatmap points table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS rssi_heatmap_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                macAddress TEXT NOT NULL,
                rssi INTEGER NOT NULL,
                latitude REAL,
                longitude REAL,
                accuracy REAL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_heatmap_mac ON rssi_heatmap_points(macAddress, timestamp)")
    }
}

