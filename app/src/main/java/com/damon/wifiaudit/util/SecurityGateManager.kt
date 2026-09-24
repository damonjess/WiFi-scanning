package com.damon.wifiaudit.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds lockdown state for the security-key feature.
 *
 * Lock: fired when a SECURITY_KEY rule triggers (key out of range) or on
 * absence-timeout. While locked, the whole UI is gated by [com.damon.wifiaudit.ui.LockScreen].
 *
 * Unlock: a lockdown can only be lifted by an explicit authentication event
 * (biometric prompt / device credential) — see LockScreen. Merely tapping a
 * button is never enough, otherwise the lock is decorative.
 */
object SecurityGateManager {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun lock() {
        _isLocked.value = true
    }

    fun unlock() {
        _isLocked.value = false
    }
}
