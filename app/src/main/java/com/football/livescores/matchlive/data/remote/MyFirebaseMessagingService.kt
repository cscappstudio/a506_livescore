package com.football.livescores.matchlive.data.remote

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.utils.PushDataParser
import com.football.livescores.matchlive.utils.PushNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceRegistrationManager: DeviceRegistrationManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        CoroutineScope(Dispatchers.IO).launch {
            deviceRegistrationManager.registerDevice(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isEmpty() && message.notification == null) return

        Log.d(TAG, "Message received: data=${message.data}")
        val rawPushType = message.data["push_type"]
        Log.d(TAG, "Push type: $rawPushType")

        val payload = PushDataParser.parse(message.data)
        Log.d(TAG, "Parsed push type/navigation: ${payload.navigation}")
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.push_notification_default_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: ""

        PushNotificationHelper.show(applicationContext, title, body, payload)
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
