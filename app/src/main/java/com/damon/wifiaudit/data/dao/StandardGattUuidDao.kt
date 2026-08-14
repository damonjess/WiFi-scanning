package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damon.wifiaudit.data.entity.StandardGattUuid

@Dao
interface StandardGattUuidDao {
    @Query("SELECT name FROM standard_gatt_uuids WHERE uuid = :uuid AND type = :type LIMIT 1")
    suspend fun lookupName(uuid: String, type: String): String?

    @Query("SELECT * FROM standard_gatt_uuids WHERE uuid = :uuid LIMIT 1")
    suspend fun lookup(uuid: String): StandardGattUuid?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(uuids: List<StandardGattUuid>)

    @Query("SELECT COUNT(*) FROM standard_gatt_uuids")
    suspend fun count(): Int
}
