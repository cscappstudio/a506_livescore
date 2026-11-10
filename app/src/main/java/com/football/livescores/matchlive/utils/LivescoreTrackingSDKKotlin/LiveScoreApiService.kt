package com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface LiveScoreApiService {
    
    @POST("api/v1/users/log-ad-event")
    suspend fun logAdEvent(
        @Body request: AdEventRequest
    ): LogResponse

    @POST("api/v1/users/log-screen-view")
    suspend fun logScreenView(
        @Body request: ScreenViewRequest
    ): LogResponse

    @POST("api/v1/users/log-action")
    suspend fun logUserAction(
        @Query("action_type") actionType: String,
        @Query("device_id") deviceId: String,
        @Body metadata: Map<String, Any>
    ): LogResponse
}

data class AdEventRequest(
    val package_name: String? = null,
    val device_id: String,
    val session_id: String,
    val event_type: String, // request, load_success, load_failed, show
    val ad_type: String,    // banner, interstitial, native, rewarded
    val ad_unit_id: String,
    val screen_name: String,
    val impression_index: Int? = null,
    val ecpm: Double? = null,
    val error_code: Int? = null,
    val network: String = "admob"
)

data class ScreenViewRequest(
    val package_name: String? = null,
    val device_id: String,
    val session_id: String,
    val screen_name: String,
    val previous_screen: String,
    val duration_seconds: Int
)

data class LogResponse(
    val status: String
)
