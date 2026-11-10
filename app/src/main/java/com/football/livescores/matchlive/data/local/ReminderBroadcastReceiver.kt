package com.football.livescores.matchlive.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.R

import android.util.Log

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getIntExtra("MATCH_ID", 0)
        val matchTitle = intent.getStringExtra("MATCH_TITLE") ?: context.getString(R.string.reminder_default_match_title)
        val leagueName = intent.getStringExtra("LEAGUE_NAME") ?: context.getString(R.string.reminder_default_league_name)

        Log.d("ReminderBroadcast", "onReceive: Triggered alarm broadcast receiver for matchId: $matchId, title: $matchTitle")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "match_reminders_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Match Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for match reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, matchId, openIntent, flags)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_live) // Reusing existing white system icon
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text, matchTitle, leagueName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(matchId, notification)
        Log.d("ReminderBroadcast", "onReceive: Posted notification successfully for match $matchId")
    }
}
