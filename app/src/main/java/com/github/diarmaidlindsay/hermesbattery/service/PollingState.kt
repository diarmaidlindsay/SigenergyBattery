package com.github.diarmaidlindsay.hermesbattery.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny shared state bridging the [PollingService] (which runs in the
 * background) and the UI. Kept as a plain singleton to avoid extra
 * IPC/DI machinery for such a small app.
 */
object PollingState {
    val active = MutableStateFlow(false)
    val lastSoc = MutableStateFlow<Double?>(null)
    val lastCheckedAt = MutableStateFlow<Long?>(null)
    val etaMinutes = MutableStateFlow<Long?>(null)
    val alertFired = MutableStateFlow(false)

    fun reset() {
        active.value = false
        lastSoc.value = null
        lastCheckedAt.value = null
        etaMinutes.value = null
        alertFired.value = false
    }
}
