package com.football.livescores.matchlive.utils

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.base.AbsBaseActivity
import com.football.livescores.matchlive.databinding.ActivityNativeFullBinding
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActivityLoadNativeFullV1 : AbsBaseActivity() {
    var binding: ActivityNativeFullBinding? = null

    @Inject
    lateinit var liveScoreApiService: com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.LiveScoreApiService

     override fun bind() {
        SystemConfiguration.setStatusBarColor(
            this,
            R.color.transparent,
            SystemConfiguration.IconColor.ICON_DARK
        )
        binding = ActivityNativeFullBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        val adIdHigh = if (intent.hasExtra(NATIVE_FUll_AD_ID_HIGH)) {
            intent.getStringExtra(NATIVE_FUll_AD_ID_HIGH)
        } else {
            ""
        }
        val adIdLow = if (intent.hasExtra(NATIVE_FUll_AD_ID)) {
            intent.getStringExtra(NATIVE_FUll_AD_ID)
        } else {
            ""
        }
        loadNativeFull(adIdHigh, adIdLow)
    }


    private fun loadNativeFull(adIdHigh: String?, adIdLow: String?) {
        val highId = adIdHigh ?: ""
        val lowId = adIdLow ?: ""

        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdRequest(
            liveScoreApiService, this, "native", highId, "NativeSplashFull"
        )

        Admob.getInstance().loadNativeAds(this, adIdHigh, 1, object : NativeCallback() {
            override fun onAdFailedToLoad() {
                super.onAdFailedToLoad()
                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadFailed(
                    liveScoreApiService, this@ActivityLoadNativeFullV1, "native", highId, "NativeSplashFull", null
                )

                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdRequest(
                    liveScoreApiService, this@ActivityLoadNativeFullV1, "native", lowId, "NativeSplashFull"
                )

                Admob.getInstance().loadNativeAds(
                    this@ActivityLoadNativeFullV1,
                    adIdLow,
                    1,
                    object : NativeCallback() {
                        override fun onAdFailedToLoad() {
                            super.onAdFailedToLoad()
                            com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadFailed(
                                liveScoreApiService, this@ActivityLoadNativeFullV1, "native", lowId, "NativeSplashFull", null
                            )
                            binding?.frAdsFull?.visibility = View.GONE
                            callback?.onResultFromActivityFull()
                            finish()
                        }

                        override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                            super.onNativeAdLoaded(nativeAd)
                            com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadSuccess(
                                liveScoreApiService, this@ActivityLoadNativeFullV1, "native", lowId, "NativeSplashFull"
                            )

                            nativeAd?.setOnPaidEventListener { adValue ->
                                val ecpm = adValue.valueMicros / 1000.0
                                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdShow(
                                    liveScoreApiService, this@ActivityLoadNativeFullV1, "native", lowId, "NativeSplashFull", ecpm
                                )
                            }

                            val adView = LayoutInflater.from(this@ActivityLoadNativeFullV1)
                                .inflate(R.layout.native_full_language, null) as NativeAdView
                            binding?.frAdsFull?.removeAllViews()
                            binding?.frAdsFull?.addView(adView)
                            Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)

                            val closeButton = adView.findViewById<ImageView>(R.id.close)
                            closeButton.visibility = View.GONE
                            closeButton.postDelayed({
                                closeButton.visibility = View.VISIBLE
                                closeButton.setOnClickListener {
                                    callback?.onResultFromActivityFull()
                                    finish()
                                }
                            }, 2000)
                            LogEvent.log(this@ActivityLoadNativeFullV1, "native_splash_full")
                        }
                    })
            }

            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                super.onNativeAdLoaded(nativeAd)
                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadSuccess(
                    liveScoreApiService, this@ActivityLoadNativeFullV1, "native", highId, "NativeSplashFull"
                )

                nativeAd?.setOnPaidEventListener { adValue ->
                    val ecpm = adValue.valueMicros / 1000.0
                    com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdShow(
                        liveScoreApiService, this@ActivityLoadNativeFullV1, "native", highId, "NativeSplashFull", ecpm
                    )
                }

                val adView = LayoutInflater.from(this@ActivityLoadNativeFullV1)
                    .inflate(R.layout.native_full_language, null) as NativeAdView
                binding?.frAdsFull?.removeAllViews()
                binding?.frAdsFull?.addView(adView)
                Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)

                val closeButton = adView.findViewById<ImageView>(R.id.close)
                closeButton.visibility = View.GONE
                closeButton.postDelayed({
                    closeButton.visibility = View.VISIBLE
                    closeButton.setOnClickListener {
                        callback?.onResultFromActivityFull()
                        finish()
                    }
                }, 1000)
                LogEvent.log(this@ActivityLoadNativeFullV1, "native_splash_full")
            }
        })
    }

    var count: Int = 0

    override fun onResume() {
        super.onResume()
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.ScreenTracker.trackScreenView(
            apiService = liveScoreApiService,
            deviceId = deviceId,
            newScreen = "NativeSplashFull"
        )
        count++
        if (count >= 2) {
            callback?.onResultFromActivityFull()
            finish()
        }
    }

    companion object {
        const val NATIVE_FUll_AD_ID_HIGH: String = "native_full_ad_id_high"

        const val NATIVE_FUll_AD_ID: String = "native_full_ad_id"

        private var callback: ActivityFullCallback? = null

        fun open(context: Context, high: String?, low: String?, cb: ActivityFullCallback?) {
            callback = cb
            val intent = Intent(context, ActivityLoadNativeFullV1::class.java)
            intent.putExtra(NATIVE_FUll_AD_ID_HIGH, high)
            intent.putExtra(NATIVE_FUll_AD_ID, low)
            context.startActivity(intent)
        }
    }
}

