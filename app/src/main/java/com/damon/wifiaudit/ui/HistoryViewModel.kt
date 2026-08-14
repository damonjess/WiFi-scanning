package com.damon.wifiaudit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.damon.wifiaudit.ble.GattSnapshotSerializer
import com.damon.wifiaudit.ble.GattUuidResolver
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryTab { WIFI, BLE }
enum class SortMode { RECENT, SIGNAL_STRONGEST }

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).sightingHistoryDao()

    val selectedTab = MutableStateFlow(HistoryTab.WIFI)
    val searchQuery = MutableStateFlow("")
    val sortMode = MutableStateFlow(SortMode.RECENT)
    val encryptionFilter = MutableStateFlow<String?>(null)

    val availableEncryptionTypes = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            availableEncryptionTypes.value = dao.getDistinctEncryptionTypes()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val wifiPagingFlow: Flow<PagingData<WifiSightingRecord>> =
        combine(searchQuery, sortMode, encryptionFilter) { q, sort, enc -> Triple(q, sort, enc) }
            .flatMapLatest { (q, sort, enc) ->
                Pager(
                    config = PagingConfig(pageSize = 30, enablePlaceholders = false),
                    pagingSourceFactory = { dao.pagedWifiHistory(q, enc, sort == SortMode.SIGNAL_STRONGEST) }
                ).flow
            }
            .cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val blePagingFlow: Flow<PagingData<BleSightingRecord>> =
        combine(searchQuery, sortMode) { q, sort -> q to sort }
            .flatMapLatest { (q, sort) ->
                Pager(
                    config = PagingConfig(pageSize = 30, enablePlaceholders = false),
                    pagingSourceFactory = { dao.pagedBleHistory(q, sort == SortMode.SIGNAL_STRONGEST) }
                ).flow
            }
            .map { pagingData ->
                pagingData.map { record ->
                    if (record.hasGatt) {
                        val resolved = resolvePrimaryGattService(record.macAddress)
                        record.copy(primaryGattService = resolved)
                    } else record
                }
            }
            .cachedIn(viewModelScope)

    private val database = AppDatabase.getInstance(application)

    private suspend fun resolvePrimaryGattService(mac: String): String? = withContext(Dispatchers.IO) {
        val snap = database.bleGattSnapshotDao().getLatestForMac(mac) ?: return@withContext null
        val services = GattSnapshotSerializer.fromJson(snap.servicesJson)
        val firstService = services.firstOrNull() ?: return@withContext null
        GattUuidResolver.resolveServiceName(firstService.uuid, database) ?: firstService.name
    }
}
