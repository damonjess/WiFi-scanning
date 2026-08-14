package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "api_queue")
data class ApiQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val isPending: Boolean = true
)

@Dao
interface ApiQueueDao {
    @Insert
    suspend fun insert(item: ApiQueueItem): Long

    @Query("SELECT * FROM api_queue WHERE isPending = 1 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getPendingItems(): List<ApiQueueItem>

    @Update
    suspend fun update(item: ApiQueueItem)

    @Query("DELETE FROM api_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE api_queue SET isPending = 0 WHERE id = :id")
    suspend fun markProcessed(id: Long)

    @Query("SELECT COUNT(*) FROM api_queue WHERE isPending = 1")
    fun getPendingCount(): kotlinx.coroutines.flow.Flow<Int>
}
