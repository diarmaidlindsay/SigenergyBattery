package com.github.diarmaidlindsay.sigenergybattery.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared state bridging the bridge-side trigger status and the UI. The
 * scheduling now runs on the Hermes bridge; the app syncs this state from
 * `GET /api/trigger` and from incoming FCM pushes. Kept as a plain singleton
 * to avoid extra IPC/DI machinery for such a small app.
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
