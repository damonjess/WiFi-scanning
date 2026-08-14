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

