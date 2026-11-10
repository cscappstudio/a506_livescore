package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object AdTrackingHelper {
    fun logAdRequest(
        apiService: LiveScoreApiService,
        context: Context,
        adType: String,
        adUnitId: String,
        screenName: String
    ) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        val sessionId = ScreenTracker.getSessionId()
        val packageName = context.packageName
        trackingScope.launch {
            try {
                apiService.logAdEvent(
                    AdEventRequest(
                        package_name = packageName,
                        device_id = deviceId,
                        session_id = sessionId,
                        event_type = "request",
                        ad_type = adType,
                        ad_unit_id = adUnitId,
                        screen_name = screenName
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAdLoadSuccess(
        apiService: LiveScoreApiService,
        context: Context,
        adType: String,
        adUnitId: String,
        screenName: String
    ) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        val sessionId = ScreenTracker.getSessionId()
        val packageName = context.packageName
        trackingScope.launch {
            try {
                apiService.logAdEvent(
                    AdEventRequest(
                        package_name = packageName,
                        device_id = deviceId,
                        session_id = sessionId,
                        event_type = "load_success",
                        ad_type = adType,
                        ad_unit_id = adUnitId,
                        screen_name = screenName
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAdLoadFailed(
        apiService: LiveScoreApiService,
        context: Context,
        adType: String,
        adUnitId: String,
        screenName: String,
        errorCode: Int?
    ) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        val sessionId = ScreenTracker.getSessionId()
        val packageName = context.packageName
        trackingScope.launch {
            try {
                apiService.logAdEvent(
                    AdEventRequest(
                        package_name = packageName,
                        device_id = deviceId,
                        session_id = sessionId,
                        event_type = "load_failed",
                        ad_type = adType,
                        ad_unit_id = adUnitId,
                        screen_name = screenName,
                        error_code = errorCode
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAdShow(
        apiService: LiveScoreApiService,
        context: Context,
        adType: String,
        adUnitId: String,
        screenName: String,
        ecpm: Double? = null
    ) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        val sessionId = ScreenTracker.getSessionId()
        val packageName = context.packageName
        val impressionIndex = AdSessionTracker.incrementAndGet(adType)
        trackingScope.launch {
            try {
                apiService.logAdEvent(
                    AdEventRequest(
                        package_name = packageName,
                        device_id = deviceId,
                        session_id = sessionId,
                        event_type = "show",
                        ad_type = adType,
                        ad_unit_id = adUnitId,
                        screen_name = screenName,
                        impression_index = impressionIndex,
                        ecpm = ecpm
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
