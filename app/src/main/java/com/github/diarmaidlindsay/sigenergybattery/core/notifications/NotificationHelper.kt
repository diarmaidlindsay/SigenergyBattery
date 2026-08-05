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

/** Creates the alert notification channel and builds trigger-fired alerts. */
object NotificationHelper {

    const val CHANNEL_ALERTS = "hermes_alerts"
    const val NOTIF_ID_ALERT = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Battery SOC threshold alerts"
            setSound(null, null)
        }
        manager.createNotificationChannel(alerts)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Builds the threshold alert notification from the FCM push payload. The
     * bridge already decided the trigger fired; here we render it in-app.
     */
    fun alertFromPush(
        context: Context,
        soc: Double?,
        threshold: Double?,
        direction: String?,
        fallbackBody: String?,
    ): Notification {
        val title = soc?.let { "Battery SOC is %.1f%%".format(it) } ?: "Battery SOC alert"
        val dirText = when (direction) {
            "AT_OR_ABOVE" -> "at or above"
            "AT_OR_BELOW" -> "at or below"
            else -> null
        }
        val text = if (dirText != null && threshold != null) {
            "Battery is $dirText ${threshold.toInt()}%. Monitoring stopped."
        } else {
            fallbackBody ?: "Battery SOC threshold reached. Monitoring stopped."
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
