package com.football.livescores.matchlive.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestLimitManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: com.football.livescores.matchlive.data.remote.RemoteConfigManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("livescore_request_limits_prefs", Context.MODE_PRIVATE)

    private val _limitExceededFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val limitExceededFlow: SharedFlow<Unit> = _limitExceededFlow.asSharedFlow()

    private val _isPremiumState = MutableStateFlow(isPremium())
    val isPremiumFlow: StateFlow<Boolean> = _isPremiumState.asStateFlow()

    companion object {
        private const val KEY_LAST_DATE = "last_request_date"
        private const val KEY_REQ_COUNT = "request_count"
        private const val KEY_IS_PREMIUM = "is_premium_user"
        
        // Manage all request rate properties in one single place
        const val DAILY_LIMIT = 20
        const val PREMIUM_POLLING_INTERVAL = 30000L
        const val NEAR_LIMIT_POLLING_INTERVAL = 30000L
        const val NORMAL_POLLING_INTERVAL = 60000L
    }

    fun getPollingInterval(): Long {
        return when {
            isPremium() -> PREMIUM_POLLING_INTERVAL
            isNearQuotaLimit() -> NEAR_LIMIT_POLLING_INTERVAL
            else -> NORMAL_POLLING_INTERVAL
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }.format(Date())
    }

    fun getDailyLimit(): Int {
        return remoteConfigManager.getDailyRequestLimit()
    }

    @Synchronized
    fun isLimitExceeded(): Boolean {
        if (isPremium()) return false

        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (lastDate != today) {
            // New day, reset the counter
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_REQ_COUNT, 0)
                .apply()
            return false
        }

        val count = prefs.getInt(KEY_REQ_COUNT, 0)
        return count >= getDailyLimit()
    }

    @Synchronized
    fun incrementCount() {
        if (isPremium()) return

        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        val currentCount = if (lastDate == today) {
            prefs.getInt(KEY_REQ_COUNT, 0)
        } else {
            0
        }

        prefs.edit()
            .putString(KEY_LAST_DATE, today)
            .putInt(KEY_REQ_COUNT, currentCount + 1)
            .apply()
    }

    fun triggerLimitExceeded() {
        _limitExceededFlow.tryEmit(Unit)
    }

    fun getRemainingRequests(): Int {
        if (isPremium()) return 999999
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val count = if (lastDate == today) prefs.getInt(KEY_REQ_COUNT, 0) else 0
        return (getDailyLimit() - count).coerceAtLeast(0)
    }

    fun isNearQuotaLimit(): Boolean {
        if (isPremium()) return false
        return getRemainingRequests() <= 5
    }

    fun isPremium(): Boolean {
        return prefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun setPremium(isPremium: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply()
        try { com.cscmobi.libraryads.commons.sharepreference.CSCSPFHolder.get().is_app_pro = isPremium } catch (_: Exception) {}
        _isPremiumState.value = isPremium
        // Instantly notify observers of quota changes when premium is acquired
        triggerLimitExceeded() 
    }

    fun getFeatureUsedCount(featureKey: String): Int {
        val today = getTodayDateString()
        return prefs.getInt("feature_used_${featureKey}_$today", 0)
    }

    fun incrementFeatureUsedCount(featureKey: String) {
        val today = getTodayDateString()
        val current = getFeatureUsedCount(featureKey)
        prefs.edit().putInt("feature_used_${featureKey}_$today", current + 1).apply()
    }

    fun isFeatureLocked(featureKey: String): Boolean {
        if (isPremium()) return false
        val limit = remoteConfigManager.getFeatureFreeLimit(featureKey)
        val used = getFeatureUsedCount(featureKey)
        return used >= limit
    }

    fun canUseFeatureFree(featureKey: String): Boolean {
        if (isPremium()) return false
        val limit = remoteConfigManager.getFeatureFreeLimit(featureKey)
        val used = getFeatureUsedCount(featureKey)
        return used < limit
    }
}
