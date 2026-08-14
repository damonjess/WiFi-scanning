package com.damon.wifiaudit.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedDeviceDao {
    @Query("SELECT * FROM tracked_devices WHERE macAddress = :mac")
    suspend fun get(mac: String): TrackedDevice?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: TrackedDevice)

    @Query("UPDATE tracked_devices SET lastSeenAt = :time, detectCount = detectCount + 1 WHERE macAddress = :mac")
    suspend fun bumpSeen(mac: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE tracked_devices SET isFavorite = :fav WHERE macAddress = :mac")
    suspend fun setFavorite(mac: String, fav: Boolean)

    @Query("UPDATE tracked_devices SET tags = :tags WHERE macAddress = :mac")
    suspend fun setTags(mac: String, tags: String)

    @Query("SELECT * FROM tracked_devices ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<TrackedDevice>>

    @Query("SELECT * FROM tracked_devices WHERE isFavorite = 1 ORDER BY lastSeenAt DESC")
    fun observeFavorites(): Flow<List<TrackedDevice>>

    @Query("SELECT * FROM tracked_devices WHERE tags LIKE '%' || :tag || '%'")
    suspend fun byTag(tag: String): List<TrackedDevice>
}
