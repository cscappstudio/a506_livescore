package com.football.livescores.matchlive.data.remote.gsm

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.football.livescores.matchlive.data.local.RequestLimitManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GsmManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val limitManager: RequestLimitManager,
    private val gsmApiServiceProvider: Provider<GsmApiService>
) {
    private val prefs = context.getSharedPreferences("gsm_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "GsmManager"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_GRANTED_GEMS = "gsm_granted_gems"
    }

    init {
        // Hydrate the memory cache from persistent storage upon startup
        val savedToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        Constants.gsmAccessToken = savedToken
        Log.d(TAG, "Initialized GsmManager, loaded cached token: ${if (savedToken.isNotEmpty()) "present" else "empty"}")
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private fun getAppVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getAdjustAdid(): String {
        return try {
            val adjustClass = Class.forName("com.adjust.sdk.Adjust")
            val getAdidMethod = adjustClass.getMethod("getAdid")
            getAdidMethod.invoke(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun loginGSM(): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId()
            val packageName = context.packageName
            val versionName = getAppVersionName()

            val request = GsmLoginRequest(
                appId = GsmConfig.GSM_APP_ID,
                deviceId = deviceId,
                pkName = packageName,
                version = versionName
            )

            Log.d(TAG, "Initiating GSM login request: $request")
            val response = gsmApiServiceProvider.get().loginGSM(request)

            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                val token = loginResponse.token
                if (!token.isNullOrEmpty()) {
                    prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
                    Constants.gsmAccessToken = token
                    Log.d(TAG, "GSM login successful. Access Token cached successfully.")
                    return@withContext true
                } else {
                    Log.e(TAG, "GSM login response did not contain an access token.")
                }
            } else {
                Log.e(TAG, "GSM login failed: Code = ${response.code()}, Msg = ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during GSM login request", e)
        }
        return@withContext false
    }

    suspend fun verify(productId: String, productType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId()
            val request = GsmVerifyRequest(
                productId = productId,
                productType = productType
            )

            Log.d(TAG, "Initiating  purchase verification: $request")
            val response = gsmApiServiceProvider.get().verify(deviceId, request)

            if (response.isSuccessful && response.body() != null) {
                val verifyResponse = response.body()!!
                if (verifyResponse.success) {
                    Log.d(TAG, " transaction verified successfully.")
                    
                    // Parse and store gems
                    val grantedGems = verifyResponse.grantedGems ?: verifyResponse.user?.gems ?: 0
                    if (grantedGems > 0) {
                        val currentGems = prefs.getInt(KEY_GRANTED_GEMS, 0)
                        prefs.edit().putInt(KEY_GRANTED_GEMS, currentGems + grantedGems).apply()
                        Log.d(TAG, "Credited $grantedGems Gems to user. Total gems: ${currentGems + grantedGems}")
                    }

                    // Update premium state
                    limitManager.setPremium(true)
                    return@withContext true
                } else {
                    Log.w(TAG, " transaction verification returned success = false.")
                }
            } else {
                Log.e(TAG, " verification failed: Code = ${response.code()}, Msg = ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during  transaction verification", e)
        }
        return@withContext false
    }

    suspend fun verifyLegacy(
        packageName: String,
        purchaseToken: String,
        productId: String,
        productName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val adid = getAdjustAdid()
            val request = GsmVerifyLegacyRequest(
                packageName = packageName,
                purchaseToken = purchaseToken,
                productId = productId,
                productName = productName,
                adid = adid
            )

            Log.d(TAG, "Initiating Legacy GSM subscription check: $request")
            val response = gsmApiServiceProvider.get().verifyLegacy(request)

            if (response.isSuccessful && response.body() != null) {
                val verifyResponse = response.body()!!
                // Success is indicated if response status is OK (usually 200/201 or success == true)
                val isSuccess = verifyResponse.success == true || verifyResponse.status == 200
                if (isSuccess) {
                    Log.d(TAG, "Legacy GSM transaction verified successfully.")
                    limitManager.setPremium(true)
                    return@withContext true
                } else {
                    Log.w(TAG, "Legacy GSM transaction verification returned negative status: ${verifyResponse.message}")
                }
            } else {
                Log.e(TAG, "Legacy GSM check failed: Code = ${response.code()}, Msg = ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Legacy GSM transaction verification", e)
        }
        return@withContext false
    }

    fun getGemsCount(): Int {
        return prefs.getInt(KEY_GRANTED_GEMS, 0)
    }
}
