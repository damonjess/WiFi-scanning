package com.damon.wifiaudit.data.dao

import androidx.room.*
import com.damon.wifiaudit.data.entity.ProximityRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ProximityRuleDao {
    @Query("SELECT * FROM proximity_rules WHERE isEnabled = 1")
    fun getActiveRules(): Flow<List<ProximityRule>>

    @Query("SELECT * FROM proximity_rules WHERE targetMac = :mac")
    suspend fun getRulesForMac(mac: String): List<ProximityRule>

    @Insert
    suspend fun insert(rule: ProximityRule): Long

    @Update
    suspend fun update(rule: ProximityRule)

    @Delete
    suspend fun delete(rule: ProximityRule)
}
