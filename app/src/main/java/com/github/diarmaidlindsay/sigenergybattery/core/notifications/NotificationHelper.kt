package com.github.diarmaidlindsay.sigenergybattery.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.diarmaidlindsay.sigenergybattery.MainActivity
import com.github.diarmaidlindsay.sigenergybattery.R
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MonitorConfig

/** Creates notification channels and builds the ongoing/alert notifications. */
object NotificationHelper {

    const val CHANNEL_MONITORING = "hermes_monitoring"
    const val CHANNEL_ALERTS = "hermes_alerts"
    const val NOTIF_ID_ONGOING = 1
    const val NOTIF_ID_ALERT = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val monitoring = NotificationChannel(
            CHANNEL_MONITORING,
            context.getString(R.string.channel_monitoring),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Ongoing battery polling indicator" }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Battery SOC threshold alerts"
            setSound(null, null)
        }
        manager.createNotificationChannel(monitoring)
        manager.createNotificationChannel(alerts)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun ongoing(
        context: Context,
        config: MonitorConfig,
        lastSoc: Double?,
        etaLabel: String?,
        transientError: String?,
    ): Notification {
        val socText = lastSoc?.let { "%.1f%%".format(it) } ?: "N/A"
        val body = buildString {
            append("Checking every ${config.intervalMinutes}m")
            append("\nLast SOC: $socText")
            if (transientError != null) {
                append("\nLast check failed: $transientError")
            } else if (etaLabel != null) {
                append("\n$etaLabel")
            }
        }
        val contentText = buildString {
            append("Last SOC: $socText")
            etaLabel?.let { append(" · $it") }
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Monitoring battery SOC")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun alert(
        context: Context,
        soc: Double,
        config: MonitorConfig,
    ): Notification = buildAlert(context, soc, config, extraLine = null)

    fun alertWithError(
        context: Context,
        soc: Double,
        config: MonitorConfig,
        error: String,
    ): Notification = buildAlert(context, soc, config, extraLine = "Action failed: $error")

    private fun buildAlert(
        context: Context,
        soc: Double,
        config: MonitorConfig,
        extraLine: String?,
    ): Notification {
        val directionText = when (config.direction) {
            com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_ABOVE ->
                "at or above"
            com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction.AT_OR_BELOW ->
                "at or below"
        }
        val title = "Battery SOC is %.1f%%".format(soc)
        val text = buildString {
            append("Battery is $directionText ${config.thresholdSoc.toInt()}%. Monitoring stopped.")
            extraLine?.let { append("\n$it") }
        }
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; skip silently.
        }
    }
}
