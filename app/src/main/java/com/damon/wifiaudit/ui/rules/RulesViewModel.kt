package com.damon.wifiaudit.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.ble.ProximityMonitorService
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.entity.ProximityRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    val rules = db.proximityRuleDao().getActiveRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggle(rule: ProximityRule) {
        viewModelScope.launch {
            db.proximityRuleDao().update(rule.copy(isEnabled = !rule.isEnabled))
            // Restart service to pick up changes
            ProximityMonitorService.stop(getApplication())
            if (!rule.isEnabled || db.proximityRuleDao().getActiveRules().first().any { it.isEnabled }) {
                ProximityMonitorService.start(getApplication())
            }
        }
    }

    fun delete(rule: ProximityRule) {
        viewModelScope.launch {
            db.proximityRuleDao().delete(rule)
        }
    }
}
