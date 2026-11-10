package com.football.livescores.matchlive.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushClickTracker @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun trackClick(pushId: String) {
        if (pushId.isBlank()) return
        try {
            val response = apiService.trackPushClick(pushId)
            Log.d(TAG, "Push click tracked: pushId=$pushId, code=${response.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track push click: pushId=$pushId, error=${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PushClickTracker"
    }
}
