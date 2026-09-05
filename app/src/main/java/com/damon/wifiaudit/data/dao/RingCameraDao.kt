package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.damon.wifiaudit.data.RingCamera
import kotlinx.coroutines.flow.Flow

@Dao
interface RingCameraDao {
    @Query("SELECT * FROM ring_cameras ORDER BY lastSeen DESC")
    fun getAllRingCameras(): Flow<List<RingCamera>>

    @Query("SELECT * FROM ring_cameras WHERE macAddress = :mac LIMIT 1")
    suspend fun getCamera(mac: String): RingCamera?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(camera: RingCamera)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(camera: RingCamera)

    @Update
    suspend fun update(camera: RingCamera)

    @Delete
    suspend fun delete(camera: RingCamera)
}
