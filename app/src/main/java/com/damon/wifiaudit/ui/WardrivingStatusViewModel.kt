package com.damon.wifiaudit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.scan.ScanCycleSnapshot
import com.damon.wifiaudit.scan.ScanStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WardrivingStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    
    val snapshot: StateFlow<ScanCycleSnapshot> = ScanStatusRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanCycleSnapshot())

    val pendingUploads: StateFlow<Int> = db.apiQueueDao().getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isServiceRunning: StateFlow<Boolean> = ScanStatusRepository.isServiceRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
