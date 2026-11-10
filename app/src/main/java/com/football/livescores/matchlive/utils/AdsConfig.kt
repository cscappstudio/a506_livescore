package com.football.livescores.matchlive.utils

import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.cscmobi.libraryads.ads.inter_ads.CSCInter
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

object AdsConfig {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AdsConfigEntryPoint {
        fun requestLimitManager(): RequestLimitManager
    }

    var nativeIntro1: NativeAd? = null
    var nativeLanguage: NativeAd? = null

    var lastInterAdShowTime: Long = 0L
    var isInterAdLoading: Boolean = false

    var isPreloadingLanguageAd: Boolean = false
    var onLanguageAdLoaded: ((NativeAd?) -> Unit)? = null

    fun showInterClickAd(activity: AppCompatActivity, onAdClosedAction: () -> Unit) {
        val limitManager = try {
            EntryPoints.get(
                activity.applicationContext,
                AdsConfigEntryPoint::class.java
            ).requestLimitManager()
        } catch (e: Exception) {
            null
        }

        if (limitManager != null && limitManager.isPremium()) {
            onAdClosedAction()
            return
        }

        if (isInterAdLoading || activity.isFinishing || activity.isDestroyed || !SystemUtil.isNetworkConnected(activity)) {
            onAdClosedAction()
            return
        }

        val currentTime = System.currentTimeMillis()
        val isEnabled = try {
            RemoteConfigManager.getInstance().isInterClickEnabled()
        } catch (e: Exception) {
            true
        }

        val interClickId = try {
            RemoteConfigManager.getInstance()
                .getAdId("inter_click", activity.getString(R.string.inter_click))
        } catch (e: Exception) {
            activity.getString(R.string.inter_click)
        }

        val nativeAllId = try {
            RemoteConfigManager.getInstance()
                .getAdId("native_all", activity.getString(R.string.native_all))
        } catch (e: Exception) {
            activity.getString(R.string.native_all)
        }

        if (isEnabled && interClickId.isNotBlank() && currentTime - lastInterAdShowTime >= 35000L) {
            isInterAdLoading = true
            CscAdHelper.showLoadingDialog(activity)
            CSCInter.loadAndShowInter(
                activity = activity,
                adId = interClickId,
                timeDelay = 0L,
                timeOut = 30000L,
                canShowId = true,
                showLoading = false,
                onShown = {
                    CscAdHelper.dismissLoadingDialog()
                    isInterAdLoading = false
                    lastInterAdShowTime = System.currentTimeMillis()
                },
                nextAction = { _ ->
                    CscAdHelper.dismissLoadingDialog()
                    isInterAdLoading = false
                    lastInterAdShowTime = System.currentTimeMillis()
                    if (!SharePreferenceUtils.isOrganic(activity)) {
                        ActivityLoadNativeFullV2.open(
                            activity,
                            nativeAllId,
                            object : ActivityFullCallback {
                                override fun onResultFromActivityFull() {
                                    onAdClosedAction()
                                }
                            }
                        )
                    } else {
                        onAdClosedAction()
                    }
                }
            )
        } else {
            onAdClosedAction()
        }
    }
}
