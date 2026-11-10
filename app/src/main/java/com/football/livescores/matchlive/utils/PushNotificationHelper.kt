package com.football.livescores.matchlive.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.football.livescores.matchlive.R

object PushNotificationHelper {

    private const val CHANNEL_ID = "live_score_push_channel"

    fun show(
        context: Context,
        title: String,
        body: String,
        payload: PushPayload
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, notificationManager)

        val contentIntent = PushNavigationExecutor.createSplashIntent(context, payload)
        val requestCode = PushNavigationExecutor.notificationId(payload)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            flags
        )

        val displayBody = body.ifBlank { title }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(requestCode, notification)
    }

    private fun ensureChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.push_notification_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
