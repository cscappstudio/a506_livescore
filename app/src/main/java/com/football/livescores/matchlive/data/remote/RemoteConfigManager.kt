package com.football.livescores.matchlive.data.remote

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class RemoteConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    companion object {
        private const val KEY_API_KEY = "apisports_key"
        private const val DEFAULT_API_KEY = "eeb82da4384bf7352f346c9371fe3dad"

        @Volatile
        private var instance: RemoteConfigManager? = null

        fun getInstance(): RemoteConfigManager {
            val inst = instance
            if (inst != null) return inst

            return synchronized(this) {
                var currentInst = instance
                if (currentInst == null) {
                    try {
                        val activityThreadClass = Class.forName("android.app.ActivityThread")
                        val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
                        val app = currentApplicationMethod.invoke(null) as android.app.Application
                        currentInst = RemoteConfigManager(app)
                        instance = currentInst
                    } catch (e: Exception) {
                        Log.e("RemoteConfigManager", "Failed to construct fallback RemoteConfigManager: ${e.message}")
                        throw IllegalStateException("RemoteConfigManager has not been initialized.")
                    }
                }
                currentInst!!
            }
        }
    }

    init {
        instance = this
        try {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            val defaults = mapOf(
                KEY_API_KEY to DEFAULT_API_KEY,
                "inter_click_enabled" to "true",
                "daily_request_limit" to "20",
                "inter_click_prediction" to "true",
                "show_activity_iap" to "true",
                "free_stats_limit" to "0",
                "free_lineups_limit" to "0",
                "free_prediction_limit" to "0",
                "free_pitch_tracker_limit" to "0"
            )
            remoteConfig.setDefaultsAsync(defaults)
        } catch (e: Exception) {
            Log.e("RemoteConfigManager", "Failed to initialize Firebase Remote Config defaults: ${e.message}")
        }
    }

    fun showOnboardingIap(): Boolean {
        return try {
            val value = remoteConfig.getString("show_activity_iap").trim()
            if (value.equals("false", ignoreCase = true)) {
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun getFeatureFreeLimit(featureKey: String): Int {
        return try {
            val limitStr = remoteConfig.getString(featureKey).trim()
            if (limitStr.isNotEmpty()) limitStr.toIntOrNull() ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }

    fun getApiKey(): String {
        return try {
            val key = remoteConfig.getString(KEY_API_KEY)
            if (key.isNullOrEmpty()) DEFAULT_API_KEY else key
        } catch (e: Exception) {
            Log.e("RemoteConfigManager", "Error reading key from Remote Config, using fallback: ${e.message}")
            DEFAULT_API_KEY
        }
    }

    fun getDailyRequestLimit(): Int {
        return try {
            val limitStr = remoteConfig.getString("daily_request_limit").trim()
            if (limitStr.isNotEmpty()) {
                limitStr.toIntOrNull() ?: 20
            } else {
                20
            }
        } catch (e: Exception) {
            Log.e("RemoteConfigManager", "Error reading daily_request_limit: ${e.message}")
            20
        }
    }

    fun getAdId(key: String, fallback: String): String {
        return try {
            val rawValue = remoteConfig.getString(key).trim()

            if (rawValue.equals("false", ignoreCase = true)) {
                return ""
            }

            if (rawValue.equals("true", ignoreCase = true)) {
                return fallback
            }

            // 3. If empty or missing -> Use local fallback
            if (rawValue.isEmpty()) {
                return fallback
            }

            // 4. Otherwise, return the custom Ad ID string from Remote Config
            rawValue
        } catch (e: Exception) {
            Log.e("RemoteConfigManager", "Error reading ad ID key '$key' from Remote Config: ${e.message}")
            fallback
        }
    }

    fun isInterClickEnabled(): Boolean {
        return try {
            val value = remoteConfig.getString("inter_click_enabled").trim()
            if (value.equals("false", ignoreCase = true)) {
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    suspend fun fetchAndActivate(): Boolean = suspendCoroutine { continuation ->
        try {
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val updated = task.result
                        Log.d("RemoteConfigManager", "Firebase Config fetched and activated successfully. Updated: $updated")
                        continuation.resume(true)
                    } else {
                        val exception = task.exception
                        Log.w("RemoteConfigManager", "Firebase Config fetch failed: ${exception?.message}", exception)
                        continuation.resume(false)
                    }
                }
        } catch (e: Exception) {
            Log.e("RemoteConfigManager", "Firebase Remote Config SDK is not initialized, using local fallback key: ${e.message}")
            continuation.resume(false)
        }
    }
}

/**
 * Clean extension function to retrieve ad unit IDs directly from any Context (Activity, Service, etc.)
 */
fun Context.getRemoteAdId(key: String, fallbackResId: Int): String {
    return try {
        RemoteConfigManager.getInstance().getAdId(key, getString(fallbackResId))
    } catch (e: Exception) {
        getString(fallbackResId)
    }
}

/**
 * Clean extension function to retrieve ad unit IDs directly from any Fragment
 */
fun Fragment.getRemoteAdId(key: String, fallbackResId: Int): String {
    return try {
        requireContext().getRemoteAdId(key, fallbackResId)
    } catch (e: Exception) {
        getString(fallbackResId)
    }
}
