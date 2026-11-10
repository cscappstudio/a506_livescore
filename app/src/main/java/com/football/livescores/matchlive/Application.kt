package com.football.livescores.matchlive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.cscmobi.libraryads.CSCApplication
import com.cscmobi.libraryads.commons.sharepreference.CSCSPFHolder
import com.cscmobi.libraryads.data.AdsLanguageConfig
import com.cscmobi.libraryads.data.AdsOBConfig
import com.cscmobi.libraryads.data.AdsSplashConfig
import com.cscmobi.libraryads.data.LanguageConfig
import com.cscmobi.libraryads.data.LanguageModel
import com.cscmobi.libraryads.data.LanguageSetting
import com.cscmobi.libraryads.data.OBConfig
import com.cscmobi.libraryads.data.OnActivityCallBack
import com.cscmobi.libraryads.data.SplashConfig
import com.cscmobi.libraryads.data.UiLanguageConfig
import com.cscmobi.libraryads.data.UiOBConfig
import com.cscmobi.libraryads.data.UiSplashConfig
import com.football.livescores.matchlive.data.local.BillingManager
import com.football.livescores.matchlive.data.remote.DeviceRegistrationManager
import com.football.livescores.matchlive.data.remote.adjust.AppAdjustTokens
import com.football.livescores.matchlive.data.remote.adjust.RetentionTracker
import com.football.livescores.matchlive.ui.main.MainActivity
import com.football.livescores.matchlive.utils.RemoteConfigs
import com.football.livescores.matchlive.utils.SystemUtil
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
class Application : android.app.Application() {

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var gsmManager: com.football.livescores.matchlive.data.remote.gsm.GsmManager

    @Inject
    lateinit var liveScoreApiService: com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.LiveScoreApiService

    @Inject
    lateinit var deviceRegistrationManager: DeviceRegistrationManager

    override fun onCreate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val processName = android.app.Application.getProcessName()
            if (packageName != processName) {
                val suffix = processName.replace(":", "_")
                try {
                    android.webkit.WebView.setDataDirectorySuffix(suffix)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate()

        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.GlobalCrashHandler(
            context = this,
            apiService = liveScoreApiService,
            deviceId = deviceId
        )

        initCSCAds()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetentionTracker.checkAndTrackRetention(this@Application)
                deviceRegistrationManager.registerDevice(null)
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrEmpty()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                deviceRegistrationManager.registerDevice(token)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Executors.newSingleThreadExecutor().execute {
            try {
                FirebaseApp.initializeApp(this)
                FacebookSdk.setClientToken(getString(R.string.facebook_client_token))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            try {
                gsmManager.loginGSM()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getLanguages(): ArrayList<LanguageModel> {
        return arrayListOf(
            LanguageModel(R.string.lang_english, R.drawable.ic_flag_en, "en", true),
            LanguageModel(R.string.lang_vietnamese, R.drawable.ic_flag_vi, "vi", false),
            LanguageModel(R.string.lang_spanish, R.drawable.ic_flag_es, "es", false),
            LanguageModel(R.string.lang_french, R.drawable.ic_flag_fr, "fr", false),
            LanguageModel(R.string.lang_german, R.drawable.ic_flag_de, "de", false),
            LanguageModel(R.string.lang_portuguese, R.drawable.ic_flag_pt, "pt", false),
            LanguageModel(R.string.lang_hindi, R.drawable.ic_flag_hi, "hi", false),
            LanguageModel(R.string.lang_indonesian, R.drawable.ic_flag_in, "in", false)
        )
    }

    private fun initCSCAds() {
        try {
            val cscLibrary = CSCApplication(this, RemoteConfigs, getString(R.string.app_id), BuildConfig.DEBUG)
            cscLibrary.initSdk(
                adjustAppToken = AppAdjustTokens.ADJUST_APP_TOKEN,
                gsmAppId = com.football.livescores.matchlive.data.remote.gsm.GsmConfig.GSM_APP_ID,
                splashConfig = SplashConfig(
                    uiSplashConfig = UiSplashConfig(
                        resLayout = R.layout.activity_splash,
                        showFOForever = false,
                        homeActivity = MainActivity::class.java,
                        timeout = 45_000
                    ),
                    adsSplashConfig = AdsSplashConfig(
                        bannerId = getString(R.string.banner_splash),
                        interHighId = getString(R.string.inter_splash_high),
                        interAllId = getString(R.string.inter_splash),
                        admobAOAId = getString(R.string.resume_open_app),
                        isCheckOrganicUser = true,
                        loadingAdFullLayout = R.layout.dialog_loading_ads
                    )
                ),
                languageConfig = LanguageConfig(
                    uiLanguageConfig = UiLanguageConfig(
                        resLayout = R.layout.activity_language,
                        itemLangDefault = R.layout.item_language_selection,
                        itemLangSelected = R.layout.item_language_selected,
                        listLanguage = getLanguages(),
                        languageSetting = object : LanguageSetting {
                            override fun onDone(activity: Activity) {
                                try {
                                    val spf = CSCSPFHolder.get()
                                    val code = spf.language_code_selected
                                    if (!code.isNullOrEmpty()) {
                                        SystemUtil.saveLocale(activity, code)
                                        SystemUtil.changeLang(code, activity)
                                        val tags = if (code == "id" || code == "in") "id,in" else code
                                        val appLocale = LocaleListCompat.forLanguageTags(tags)
                                        AppCompatDelegate.setApplicationLocales(appLocale)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                val mainIntent = Intent(activity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                activity.startActivity(mainIntent)
                                activity.finish()
                            }
                        }
                    ),
                    adsLanguageConfig = AdsLanguageConfig(
                        nativeLangHighId = getString(R.string.native_language_high),
                        nativeLangId = getString(R.string.native_language),
                        nativeLangClickHighId = getString(R.string.native_language_click_high),
                        nativeLangClickId = getString(R.string.native_language_click),
                        layoutNative = R.layout.layout_native_media,
                        layoutNativeClick = R.layout.layout_native_media
                    )
                ),
                obConfig = OBConfig(
                    uiOBConfig = UiOBConfig(
                        resFragmentOB1 = R.layout.fragment_intro1,
                        resFragmentOB2 = R.layout.fragment_intro2,
                        resFragmentOB3 = R.layout.fragment_intro3,
                        resFragmentOB4 = R.layout.fragment_intro4,
                        nextOBActivity = MainActivity::class.java,
                        activityCallback = object : OnActivityCallBack {
                            override fun onNextActivity(activity: Activity, inSession2: Boolean) {
                                try {
                                    val spf = CSCSPFHolder.get()
                                    val code = spf.language_code_selected
                                    if (!code.isNullOrEmpty()) {
                                        SystemUtil.saveLocale(activity, code)
                                        SystemUtil.changeLang(code, activity)
                                        val tags = if (code == "id" || code == "in") "id,in" else code
                                        val appLocale = LocaleListCompat.forLanguageTags(tags)
                                        AppCompatDelegate.setApplicationLocales(appLocale)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    ),
                    adsOBConfig = AdsOBConfig(
                        nativeOB1Id = getString(R.string.native_onboarding_1),
                        nativeOB4Id = getString(R.string.native_onboarding_4),
                        nativeOBFull12Id = getString(R.string.native_onboarding_full_1),
                        layoutNativeOB1 = R.layout.layout_native_media,
                        layoutNativeOB4 = R.layout.layout_native_media,
                        layoutNativeFullOB = R.layout.admob_layout_native_full,
                        isLoadNativeOBInLanguage = true
                    )
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
