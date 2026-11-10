package com.football.livescores.matchlive.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.football.livescores.matchlive.base.BaseActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.databinding.ActivityMainBinding
import com.football.livescores.matchlive.ui.custom.PremiumPaywallDialog
import com.football.livescores.matchlive.ui.home.HomeFragment
import com.football.livescores.matchlive.ui.leagues.LeaguesFragment
import com.football.livescores.matchlive.ui.news.NewsFragment
import com.football.livescores.matchlive.ui.iap.IAPActivity
import com.football.livescores.matchlive.ui.profile.ProfileActivity
import com.football.livescores.matchlive.ui.search.SearchActivity
import com.football.livescores.matchlive.utils.AdsConfig
import com.football.livescores.matchlive.utils.LogEvent
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob
import com.football.livescores.matchlive.utils.setOnPaidEventListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_LEAGUE_ID = "LEAGUE_ID"
    }

    @Inject
    lateinit var limitManager: RequestLimitManager

    @Inject
    lateinit var liveScoreApiService: com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.LiveScoreApiService

    private lateinit var binding: ActivityMainBinding
    private var isLimitDialogShowing = false
    private var activeTabId: Int = R.id.nav_live
    private var isHandlingDeepLink = false

    private val handlerADS = Handler(Looper.getMainLooper())
    private var isFirstLoad = true
    private var delayedLoadExpandTask: Runnable? = null
    private var savedState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        savedState = savedInstanceState
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("active_tab_id", activeTabId)
    }

    override fun bind() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        LogEvent.log(this, "main_view")

        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            val toolbarParams = binding.toolbar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            binding.toolbar.layoutParams = toolbarParams
            
            // Adjust bottom navigation margin and banner ads padding to fit navigation bar
            val baseBottomMargin = (10 * resources.displayMetrics.density).toInt()
            val navParams = binding.bottomNavigation.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            navParams.bottomMargin = baseBottomMargin
            navParams.goneBottomMargin = baseBottomMargin + systemBars.bottom
            binding.bottomNavigation.layoutParams = navParams

            binding.frAdsBanner.setPadding(0, 0, 0, systemBars.bottom)
            binding.frAdsCollap.setPadding(0, 0, 0, systemBars.bottom)
            
            insets
        }

        binding.frAdsBanner.bringToFront()
        binding.frAdsCollap.bringToFront()

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val isDeepLinkHandled = handleDeepLinkIntent(intent)

        if (!isDeepLinkHandled) {
            if (savedState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment.Companion.newInstance(false))
                    .commit()
                updateLogoText(R.id.nav_live)
                updateBottomNavUI(R.id.nav_live)
            } else {
                activeTabId = savedState?.getInt("active_tab_id", R.id.nav_live) ?: R.id.nav_live
                updateLogoText(activeTabId)
                updateBottomNavUI(activeTabId)
            }
        }

        setupBottomNavigation()

        // Observe request limit exceeds safely in RESUMED state to prevent StateLoss crashes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                limitManager.limitExceededFlow.collect {
                    if (!limitManager.isPremium()) {
                        showPremiumPaywall(isOutOfQuota = true)
                    }
                }
            }
        }

        // Observe premium status changes across the entire activity lifecycle to recreate when purchased
        lifecycleScope.launch {
            var wasPremium = limitManager.isPremium()
            limitManager.isPremiumFlow.collect { isPremium ->
                if (isPremium && !wasPremium) {
                    recreate()
                }
                wasPremium = isPremium
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeTabId != R.id.nav_live) {
                    switchToTab(R.id.nav_live)
                } else {
                    showExitConfirmDialog()
                }
            }
        })
    }

    private fun showExitConfirmDialog() {
        if (isFinishing || isDestroyed) return
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_exit_confirm)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        dialog.findViewById<View>(R.id.btnCancel)?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.findViewById<View>(R.id.btnExit)?.setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }
        
        dialog.show()
    }

    private fun setupBottomNavigation() {
        binding.navLive.setOnClickListener { onTabSelected(R.id.nav_live) }
        binding.navLeagues.setOnClickListener { onTabSelected(R.id.nav_leagues) }
        binding.navNews.setOnClickListener { onTabSelected(R.id.nav_news) }
    }

    private fun onTabSelected(tabId: Int) {
        if (isFinishing || isDestroyed) return
        if (isHandlingDeepLink) {
            updateBottomNavUI(tabId)
            return
        }
        if (activeTabId == tabId) return

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val isSameFragment = when (tabId) {
            R.id.nav_live -> currentFragment is HomeFragment
            R.id.nav_leagues -> currentFragment is LeaguesFragment
            R.id.nav_news -> currentFragment is NewsFragment
            else -> false
        }
        if (isSameFragment) {
            activeTabId = tabId
            updateBottomNavUI(tabId)
            return
        }

        activeTabId = tabId
        updateBottomNavUI(tabId)
        updateLogoText(tabId)
        val fragment = when (tabId) {
            R.id.nav_live -> HomeFragment.Companion.newInstance(false)
            R.id.nav_leagues -> LeaguesFragment.newInstance()
            R.id.nav_news -> NewsFragment.newInstance()
            else -> null
        }
        if (fragment != null && !isFinishing && !isDestroyed) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss()
        }

        if (!isFinishing && !isDestroyed) {
            AdsConfig.showInterClickAd(this) {
                // Done in background without blocking transitions
            }
        }
    }

    private fun updateBottomNavUI(selectedTabId: Int) {
        val activeColor = android.graphics.Color.parseColor("#26A9F5")
        val inactiveColor = android.graphics.Color.parseColor("#859DC4")

        val isLive = selectedTabId == R.id.nav_live
        binding.ivNavLive.setImageResource(if (isLive) R.drawable.ic_live_selected else R.drawable.ic_live_unselected)
        binding.tvNavLive.setTextColor(if (isLive) activeColor else inactiveColor)
        binding.indNavLive.visibility = if (isLive) View.VISIBLE else View.INVISIBLE

        val isLeagues = selectedTabId == R.id.nav_leagues
        binding.ivNavLeagues.setImageResource(if (isLeagues) R.drawable.ic_leagues_selected else R.drawable.ic_leagues_unselected)
        binding.tvNavLeagues.setTextColor(if (isLeagues) activeColor else inactiveColor)
        binding.indNavLeagues.visibility = if (isLeagues) View.VISIBLE else View.INVISIBLE

        val isNews = selectedTabId == R.id.nav_news
        binding.ivNavNews.setImageResource(if (isNews) R.drawable.ic_news_selected else R.drawable.ic_news_unselected)
        binding.tvNavNews.setTextColor(if (isNews) activeColor else inactiveColor)
        binding.indNavNews.visibility = if (isNews) View.VISIBLE else View.INVISIBLE
    }

    fun switchToTab(tabId: Int) {
        onTabSelected(tabId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?): Boolean {
        val leagueId = intent?.getIntExtra(EXTRA_LEAGUE_ID, -1) ?: -1
        if (leagueId <= 0) return false

        isHandlingDeepLink = true
        activeTabId = R.id.nav_leagues
        updateLogoText(R.id.nav_leagues)
        updateBottomNavUI(R.id.nav_leagues)
        isHandlingDeepLink = false

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LeaguesFragment.newInstance(leagueId))
            .commitAllowingStateLoss()
        intent?.removeExtra(EXTRA_LEAGUE_ID)
        return true
    }

    fun showPremiumPaywall(isOutOfQuota: Boolean = false) {
        if (supportFragmentManager.isStateSaved) return
        val existing = supportFragmentManager.findFragmentByTag(PremiumPaywallDialog.Companion.TAG)
        if (existing == null) {
            val paywall = PremiumPaywallDialog.Companion.newInstance(isOutOfQuota)
            paywall.show(supportFragmentManager, PremiumPaywallDialog.Companion.TAG)
        }
    }

    override fun onResume() {
        super.onResume()
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
        android.util.Log.d("MainActivity", "Device ID: $deviceId")
        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.ScreenTracker.trackScreenView(
            apiService = liveScoreApiService,
            deviceId = deviceId,
            newScreen = "MainActivity",
            packageName = packageName
        )

        if (::limitManager.isInitialized) {
            updateVipButtonVisibility()
        }

        if (!limitManager.isPremium()) {
            binding.frAdsCollap.visibility = View.VISIBLE
            binding.frAdsBanner.visibility = View.VISIBLE
            if (isFirstLoad) {
                loadNativeBanner {
                    delayedLoadExpandTask = Runnable {
                        if (!isFinishing && !isDestroyed && !limitManager.isPremium()) {
                            loadNativeCollapse()
                        }
                        isFirstLoad = false
                    }
                    handlerADS.postDelayed(delayedLoadExpandTask!!, 1000)
                }
            } else {
                loadNativeBanner {
                    delayedLoadExpandTask = Runnable {
                        if (!isFinishing && !isDestroyed && !limitManager.isPremium()) {
                            loadNativeCollapse()
                        }
                    }
                    handlerADS.postDelayed(delayedLoadExpandTask!!, 15000)
                }
            }
        } else {
            delayedLoadExpandTask?.let {
                handlerADS.removeCallbacks(it)
                delayedLoadExpandTask = null
            }
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.frAdsCollap.removeAllViews()
            binding.frAdsBanner.removeAllViews()
            binding.frAdsCollap.visibility = View.GONE
            binding.frAdsBanner.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        delayedLoadExpandTask?.let {
            handlerADS.removeCallbacks(it)
            delayedLoadExpandTask = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerADS.removeCallbacksAndMessages(null)
    }

    private fun loadNativeCollapse() {
        if (limitManager.isPremium() || isFinishing || isDestroyed) {
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.frAdsCollap.visibility = View.GONE
            binding.frAdsCollap.removeAllViews()
            return
        }

        val nativeAllId = try {
            RemoteConfigManager.Companion.getInstance()
                .getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdRequest(
            liveScoreApiService, this, "native", nativeAllId, "MainActivity"
        )

        Admob.getInstance().loadNativeAd(this, nativeAllId, object : NativeCallback() {
            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                if (isDestroyed || isFinishing) return
                if (limitManager.isPremium()) {
                    binding.bottomNavigation.visibility = View.VISIBLE
                    binding.frAdsCollap.visibility = View.GONE
                    binding.frAdsCollap.removeAllViews()
                    return
                }

                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadSuccess(
                    liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity"
                )

                nativeAd?.setOnPaidEventListener { adValue ->
                    val ecpm = adValue.valueMicros / 1000.0
                    com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdShow(
                        liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity", ecpm
                    )
                }

                val adView = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.layout_native_home_collapse, null) as NativeAdView

                binding.bottomNavigation.visibility = View.GONE

                binding.frAdsCollap.removeAllViews()

                val mediaView = adView.findViewById<MediaView>(R.id.media_view)
                    ?: adView.findViewById<MediaView>(R.id.ad_media)
                val closeButton = adView.findViewById<ImageView>(R.id.close)

                closeButton?.setOnClickListener {
                    binding.frAdsCollap.removeAllViews()
                    binding.bottomNavigation.visibility = View.VISIBLE
                    loadNativeBanner()
                }

                binding.frAdsCollap.visibility = View.VISIBLE
                binding.frAdsCollap.addView(adView)
                binding.frAdsCollap.bringToFront() // Force draw on top of navbar
                Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
            }

            override fun onAdFailedToLoad() {
                if (isDestroyed || isFinishing) return
                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadFailed(
                    liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity", null
                )
                binding.frAdsCollap.removeAllViews()

                // Show bottom navigation again if ad fails to load
                binding.bottomNavigation.visibility = View.VISIBLE
            }
        })
    }

    private fun loadNativeBanner(onLoaded: (() -> Unit)? = null) {
        if (limitManager.isPremium() || isFinishing || isDestroyed) {
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.frAdsBanner.visibility = View.GONE
            binding.frAdsBanner.removeAllViews()
            return
        }

        binding.frAdsCollap.removeAllViews()

        val nativeAllId = try {
            RemoteConfigManager.Companion.getInstance()
                .getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdRequest(
            liveScoreApiService, this, "native", nativeAllId, "MainActivity"
        )

        Admob.getInstance().loadNativeAd(this, nativeAllId, object : NativeCallback() {
            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                if (isDestroyed || isFinishing) return
                if (limitManager.isPremium()) {
                    binding.bottomNavigation.visibility = View.VISIBLE
                    binding.frAdsBanner.visibility = View.GONE
                    binding.frAdsBanner.removeAllViews()
                    return
                }

                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadSuccess(
                    liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity"
                )

                nativeAd?.setOnPaidEventListener { adValue ->
                    val ecpm = adValue.valueMicros / 1000.0
                    com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdShow(
                        liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity", ecpm
                    )
                }

                val adView = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.layout_native_banner, null) as NativeAdView

                binding.bottomNavigation.visibility = View.VISIBLE
                binding.frAdsBanner.visibility = View.VISIBLE

                binding.frAdsBanner.removeAllViews()
                binding.frAdsBanner.addView(adView)
                binding.frAdsBanner.bringToFront()

                Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)

                onLoaded?.invoke()
            }

            override fun onAdFailedToLoad() {
                if (isDestroyed || isFinishing) return
                com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.AdTrackingHelper.logAdLoadFailed(
                    liveScoreApiService, this@MainActivity, "native", nativeAllId, "MainActivity", null
                )
                binding.frAdsBanner.removeAllViews()

                // Ensure navbar is visible if ad failed
                binding.bottomNavigation.visibility = View.VISIBLE

                onLoaded?.invoke()
            }
        })
    }

    private fun updateVipButtonVisibility() {
        if (limitManager.isPremium()) {
            binding.btnGoVip.visibility = View.GONE
        } else {
            binding.btnGoVip.visibility = View.VISIBLE
            binding.btnGoVip.setOnClickListener {
                startActivity(Intent(this, IAPActivity::class.java))
            }
        }
    }

    private fun updateLogoText(tabId: Int) {
        if (tabId == R.id.nav_live) {
            binding.layoutHomeTitle.visibility = View.VISIBLE
            binding.logoText.visibility = View.GONE
        } else {
            binding.layoutHomeTitle.visibility = View.GONE
            binding.logoText.visibility = View.VISIBLE
            
            val title = when (tabId) {
                R.id.nav_leagues -> getString(R.string.leagues_header_title)
                R.id.nav_news -> getString(R.string.nav_news)
                R.id.btnSettings -> getString(R.string.profile_settings)
                else -> getString(R.string.splash_app_title)
            }
            binding.logoText.text = title
        }
    }
}