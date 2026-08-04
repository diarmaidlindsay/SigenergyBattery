package com.github.diarmaidlindsay.sigenergybattery.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.github.diarmaidlindsay.sigenergybattery.SigenergyBatteryApp
import com.github.diarmaidlindsay.sigenergybattery.core.notifications.NotificationHelper
import com.github.diarmaidlindsay.sigenergybattery.data.api.ApiClientFactory
import com.github.diarmaidlindsay.sigenergybattery.data.api.toSnapshot
import com.github.diarmaidlindsay.sigenergybattery.data.local.SettingsStore
import com.github.diarmaidlindsay.sigenergybattery.domain.BatteryMonitor
import com.github.diarmaidlindsay.sigenergybattery.domain.SocEtaCalculator
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that polls the Hermes bridge on the configured interval,
 * updates its ongoing notification, and fires the alert notification the first
 * time the SOC reaches the threshold. It then stops itself (one-shot, per spec).
 */
class PollingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val appContainer get() = (application as SigenergyBatteryApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.ongoing(
            context = this,
            config = MonitorConfig(1, 0.0, com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_ABOVE),
            lastSoc = null,
            etaLabel = null,
            transientError = null,
        )
        startForeground(NotificationHelper.NOTIF_ID_ONGOING, notification)
        if (job == null) {
            job = scope.launch { runMonitoringLoop() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runMonitoringLoop() {
        val store: SettingsStore = appContainer.settingsStore
        val bridgeConfig = store.bridgeConfig.first()
        val monitorConfig = store.monitorConfig.first()
        val api = appContainer.createApi(bridgeConfig)

        PollingState.reset()
        PollingState.active.value = true
        try {
            while (isActive()) {
                val pollStarted = SystemClock.elapsedRealtime()
                var transientError: String? = null
                try {
                    val snapshot = api.solarNow().toSnapshot()
                    PollingState.lastSoc.value = snapshot.socPct
                    PollingState.lastCheckedAt.value = System.currentTimeMillis()
                    val soc = snapshot.socPct
                    PollingState.etaMinutes.value = computeEta(soc, snapshot, monitorConfig)
                    if (BatteryMonitor.shouldNotify(soc, monitorConfig.thresholdSoc, monitorConfig.direction)) {
                        if (soc != null) {
                            NotificationHelper.notify(
                                this,
                                NotificationHelper.NOTIF_ID_ALERT,
                                NotificationHelper.alert(this, soc, monitorConfig),
                            )
                        }
                        PollingState.alertFired.value = true
                        break
                    }
                } catch (e: Exception) {
                    transientError = e.message?.take(80) ?: "network error"
                }

                val notification = NotificationHelper.ongoing(
                    context = this,
                    config = monitorConfig,
                    lastSoc = PollingState.lastSoc.value,
                    etaLabel = buildEtaLabel(monitorConfig, PollingState.etaMinutes.value),
                    transientError = transientError,
                )
                NotificationHelper.notify(this, NotificationHelper.NOTIF_ID_ONGOING, notification)

                // Sleep in small steps so stop() is responsive.
                val elapsed = SystemClock.elapsedRealtime() - pollStarted
                val remaining = monitorConfig.intervalMillis - elapsed
                if (remaining > 0) delay(remaining)
            }
        } finally {
            PollingState.active.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun computeEta(
        soc: Double?,
        snapshot: com.github.diarmaidlindsay.sigenergybattery.domain.model.SolarSnapshot,
        monitorConfig: MonitorConfig,
    ): Long? {
        val currentSoc = soc ?: return null
        val batteryKw = snapshot.batteryKw ?: return null
        val capacityKwh = snapshot.capacityKwh ?: return null
        return SocEtaCalculator.minutesToTarget(
            currentSoc = currentSoc,
            targetSoc = monitorConfig.thresholdSoc,
            batteryKw = batteryKw,
            capacityKwh = capacityKwh,
        )
    }

    private fun buildEtaLabel(monitorConfig: MonitorConfig, etaMinutes: Long?): String {
        val target = monitorConfig.thresholdSoc.toInt()
        val eta = etaMinutes ?: return "$target% — not approaching at current rate"
        return "$target% in ${SocEtaCalculator.formatMinutes(eta)}"
    }

    private fun isActive(): Boolean =
        PollingState.active.value

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        PollingState.active.value = false
        super.onDestroy()
    }

    companion object {
        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PollingService::class.java),
            )
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, PollingService::class.java))
        }
    }
}
