package com.github.diarmaidlindsay.sigenergybattery.push

import android.content.Context
import com.github.diarmaidlindsay.sigenergybattery.SigenergyBatteryApp
import com.github.diarmaidlindsay.sigenergybattery.core.notifications.NotificationHelper
import com.github.diarmaidlindsay.sigenergybattery.data.api.DeviceRegisterDto
import com.github.diarmaidlindsay.sigenergybattery.service.PollingState
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives trigger-fired push notifications from the bridge. The bridge runs
 * the SOC scheduling; this service only renders the alert and keeps the
 * in-app state in sync when the app is open.
 */
class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val soc = data["soc"]?.toDoubleOrNull()
        val threshold = data["threshold"]?.toDoubleOrNull()
        val direction = data["direction"]
        val notification = NotificationHelper.alertFromPush(
            context = this,
            soc = soc,
            threshold = threshold,
            direction = direction,
            fallbackBody = message.notification?.body,
        )
        NotificationHelper.notify(this, NotificationHelper.NOTIF_ID_ALERT, notification)
        PollingState.alertFired.value = true
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        registerToken(this, token)
    }

    companion object {
        /** Best-effort re-registration when the FCM token rotates. */
        fun registerToken(context: Context, token: String) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                runCatching {
                    val container = (context.applicationContext as SigenergyBatteryApp).container
                    val bridge = container.settingsStore.bridgeConfig.first()
                    container.createApi(bridge).registerDevice(DeviceRegisterDto(token))
                }
            }
        }
    }
}
