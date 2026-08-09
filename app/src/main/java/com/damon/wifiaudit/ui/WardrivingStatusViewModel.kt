package com.damon.wifiaudit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.scan.ScanCycleSnapshot
import com.damon.wifiaudit.scan.ScanStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WardrivingStatusViewModel : ViewModel() {
    val snapshot: StateFlow<ScanCycleSnapshot> = ScanStatusRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanCycleSnapshot())
}
