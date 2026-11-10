package com.football.livescores.matchlive.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Helper object to log events and set user properties in Firebase Analytics centrally.
 */
object LogEvent {
    private const val TAG = "LogEvent"

    /**
     * Log an event with a Map of parameters.
     * Automatically converts types (String, Int, Long, Double, Boolean) to Bundle values.
     */
    fun log(context: Context, eventName: String, params: Map<String, Any?>? = null) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            val bundle = Bundle().apply {
                params?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        null -> putString(key, null)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics.logEvent(eventName, bundle)
            Log.d(TAG, "Logged event: $eventName, params: $params")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging event $eventName", e)
        }
    }

    /**
     * Log an event using a standard Android Bundle directly.
     */
    fun log(context: Context, eventName: String, bundle: Bundle) {
        try {
            FirebaseAnalytics.getInstance(context.applicationContext).logEvent(eventName, bundle)
            Log.d(TAG, "Logged event: $eventName, bundle: $bundle")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging bundle event $eventName", e)
        }
    }

    /**
     * Set a User Property for reports filtering and analytics targeting.
     */
    fun setUserProperty(context: Context, propertyName: String, value: String?) {
        try {
            FirebaseAnalytics.getInstance(context.applicationContext).setUserProperty(propertyName, value)
            Log.d(TAG, "Set User Property: $propertyName = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user property $propertyName", e)
        }
    }
}
