package com.football.livescores.matchlive.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.R
import com.cscmobi.libraryads.ads.inter_ads.CSCInter
import com.cscmobi.libraryads.commons.adjust.trackingRevenueAd
import java.lang.ref.WeakReference

fun NativeAd.setOnPaidEventListener(listener: (AdValue) -> Unit) {
    val existingCallback = this.adEventCallback
    this.adEventCallback = object : NativeAdEventCallback {
        override fun onAdPaid(value: AdValue) {
            existingCallback?.onAdPaid(value)
            listener(value)
        }
        override fun onAdClicked() {
            existingCallback?.onAdClicked()
        }
        override fun onAdImpression() {
            existingCallback?.onAdImpression()
        }
        override fun onAdDismissedFullScreenContent() {
            existingCallback?.onAdDismissedFullScreenContent()
        }
        override fun onAdShowedFullScreenContent() {
            existingCallback?.onAdShowedFullScreenContent()
        }
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
            existingCallback?.onAdFailedToShowFullScreenContent(fullScreenContentError)
        }
        override fun onAdSwipeGestureClicked() {
            existingCallback?.onAdSwipeGestureClicked()
        }
        override fun onCustomMuteThisAdReported() {
            existingCallback?.onCustomMuteThisAdReported()
        }
    }
}

abstract class NativeCallback {
    open fun onNativeAdLoaded(nativeAd: NativeAd?) {}
    open fun onAdFailedToLoad() {}
}

abstract class InterCallback {
    open fun onInterstitialLoad(interstitialAd: InterstitialAd) {}
    open fun onAdClosed() {}
    open fun onAdClosedByUser() {}
    open fun onAdFailedToLoad(error: LoadAdError?) {}
}

object CscAdHelper {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadingDialogRef: WeakReference<Dialog>? = null

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun showLoadingDialog(activity: Activity) {
        runOnMainThread {
            try {
                if (activity.isFinishing || activity.isDestroyed) return@runOnMainThread
                dismissLoadingDialog()

                val dialog = Dialog(activity).apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setContentView(R.layout.dialog_loading_ads)
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    window?.apply {
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setLayout(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setGravity(Gravity.CENTER)
                        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setDimAmount(0.55f)
                    }
                }
                dialog.show()
                loadingDialogRef = WeakReference(dialog)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissLoadingDialog() {
        runOnMainThread {
            try {
                val dialog = loadingDialogRef?.get()
                if (dialog != null && dialog.isShowing) {
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingDialogRef = null
            }
        }
    }

    fun loadNativeAd(context: Context, adId: String?, callback: NativeCallback) {
        if (adId.isNullOrBlank()) {
            runOnMainThread { callback.onAdFailedToLoad() }
            return
        }
        try {
            val request = NativeAdRequest.Builder(adId, listOf(NativeAd.NativeAdType.NATIVE))
                .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
                .build()
            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    try {
                        trackingRevenueAd(nativeAd)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    runOnMainThread {
                        callback.onNativeAdLoaded(nativeAd)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMainThread {
                        callback.onAdFailedToLoad()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            runOnMainThread { callback.onAdFailedToLoad() }
        }
    }

    fun loadNativeAds(context: Context, adId: String?, count: Int = 1, callback: NativeCallback) {
        loadNativeAd(context, adId, callback)
    }

    fun pushAdsToViewCustom(nativeAd: NativeAd?, adView: NativeAdView?) {
        if (nativeAd == null || adView == null) return

        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            adView.headlineView = headlineView
        }

        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        if (bodyView != null) {
            if (nativeAd.body == null) {
                bodyView.visibility = View.INVISIBLE
            } else {
                bodyView.visibility = View.VISIBLE
                bodyView.text = nativeAd.body
            }
            adView.bodyView = bodyView
        }

        val ctaView = adView.findViewById<TextView>(R.id.ad_call_to_action)
        if (ctaView != null) {
            if (nativeAd.callToAction == null) {
                ctaView.visibility = View.INVISIBLE
            } else {
                ctaView.visibility = View.VISIBLE
                ctaView.text = nativeAd.callToAction
            }
            adView.callToActionView = ctaView
        }

        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        if (iconView != null) {
            if (nativeAd.icon == null) {
                iconView.visibility = View.GONE
            } else {
                iconView.setImageDrawable(nativeAd.icon?.drawable)
                iconView.visibility = View.VISIBLE
            }
            adView.iconView = iconView
        }

        val mediaView = adView.findViewById<MediaView>(R.id.media_view)
            ?: adView.findViewById<MediaView>(R.id.ad_media)

        // Delegate to library binder to ensure all library-specific bindings and registerNativeAd are executed
        try {
            com.cscmobi.libraryads.ads.native_ads.renderer.DefaultNativeViewBinder().bind(adView, nativeAd)
        } catch (_: Exception) {
            try {
                if (mediaView != null) {
                    nativeAd.mediaContent?.let { mediaView.mediaContent = it }
                }
                adView.registerNativeAd(nativeAd, mediaView)
            } catch (_: Exception) {}
        }

        // Ensure content is visible and shimmer is hidden
        adView.findViewById<View>(R.id.ad_content_view)?.visibility = View.VISIBLE
        adView.findViewById<View>(R.id.shimmer_view)?.visibility = View.GONE
    }

    fun loadAndShowInter(
        activity: Activity,
        adId: String,
        delay: Long = 0L,
        timeout: Long = 30000L,
        callback: InterCallback
    ) {
        if (adId.isBlank() || activity.isFinishing || activity.isDestroyed || !SystemUtil.isNetworkConnected(activity)) {
            callback.onAdFailedToLoad(null)
            return
        }

        showLoadingDialog(activity)

        CSCInter.loadAndShowInter(
            activity = activity,
            adId = adId,
            timeDelay = delay,
            timeOut = timeout,
            canShowId = true,
            showLoading = false,
            onShown = {
                dismissLoadingDialog()
            },
            nextAction = { isSuccess ->
                dismissLoadingDialog()
                if (isSuccess) {
                    callback.onAdClosed()
                    callback.onAdClosedByUser()
                } else {
                    callback.onAdFailedToLoad(null)
                }
            }
        )
    }

    fun onCheckShowSplashWhenFail(activity: Activity, callback: InterCallback?, timeout: Long = 5000L) {
        // Handled directly via CSCInter.loadAndShowInterSplash timeout
    }
}

/**
 * Compatibility facade that transparently maps legacy Admob calls to CscAdHelper / CSC Library.
 */
object Admob {
    fun getInstance(): Admob = this

    fun loadNativeAd(context: Context, adId: String?, callback: NativeCallback) {
        CscAdHelper.loadNativeAd(context, adId, callback)
    }

    fun loadNativeAds(context: Context, adId: String?, count: Int = 1, callback: NativeCallback) {
        CscAdHelper.loadNativeAds(context, adId, count, callback)
    }

    fun pushAdsToViewCustom(nativeAd: NativeAd?, adView: NativeAdView?) {
        CscAdHelper.pushAdsToViewCustom(nativeAd, adView)
    }

    fun loadAndShowInter(
        activity: Activity,
        adId: String,
        delay: Long = 0L,
        timeout: Long = 30000L,
        callback: InterCallback
    ) {
        CscAdHelper.loadAndShowInter(activity, adId, delay, timeout, callback)
    }

    fun dismissLoadingDialog() {
        CscAdHelper.dismissLoadingDialog()
    }

    fun onCheckShowSplashWhenFail(activity: Activity, callback: InterCallback?, timeout: Long = 5000L) {
        CscAdHelper.onCheckShowSplashWhenFail(activity, callback, timeout)
    }
}
