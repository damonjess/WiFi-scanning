package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damon.wifiaudit.data.entity.OuiVendor
import kotlinx.coroutines.flow.Flow

@Dao
interface OuiVendorDao {
    @Query("SELECT vendorName FROM oui_vendors WHERE oui = :oui LIMIT 1")
    suspend fun lookupVendor(oui: String): String?

    @Query("SELECT * FROM oui_vendors WHERE oui = :oui LIMIT 1")
    suspend fun lookup(oui: String): OuiVendor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vendors: List<OuiVendor>)

    @Query("SELECT COUNT(*) FROM oui_vendors")
    suspend fun count(): Int

    @Query("SELECT * FROM oui_vendors WHERE vendorName LIKE '%' || :query || '%' LIMIT 50")
    fun searchByVendor(query: String): Flow<List<OuiVendor>>
}
