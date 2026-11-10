package com.football.livescores.matchlive.ui.team

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.base.BaseActivity
import com.football.livescores.matchlive.databinding.ActivityTeamFixturesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.view.LayoutInflater
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob
import com.football.livescores.matchlive.ui.detail.MatchDetailActivity
import com.football.livescores.matchlive.utils.AdsConfig

@AndroidEntryPoint
class TeamFixturesActivity : BaseActivity() {

    companion object {
        private const val EXTRA_TEAM_ID = "extra_team_id"
        private const val EXTRA_TEAM_NAME = "extra_team_name"
        private const val EXTRA_TEAM_LOGO = "extra_team_logo"

        fun startActivity(context: Context, teamId: Int, teamName: String = "", teamLogo: String = "") {
            val intent = Intent(context, TeamFixturesActivity::class.java).apply {
                putExtra(EXTRA_TEAM_ID, teamId)
                putExtra(EXTRA_TEAM_NAME, teamName)
                putExtra(EXTRA_TEAM_LOGO, teamLogo)
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var limitManager: RequestLimitManager

    private lateinit var binding: ActivityTeamFixturesBinding
    private val viewModel: TeamFixturesViewModel by viewModels()

    private var teamId: Int = -1
    private var teamName: String = ""
    private var teamLogo: String = ""
    private lateinit var fixturesAdapter: TeamFixturesAdapter

    override fun bind() {
        binding = ActivityTeamFixturesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teamId = intent.getIntExtra(EXTRA_TEAM_ID, -1)
        teamName = intent.getStringExtra(EXTRA_TEAM_NAME) ?: ""
        teamLogo = intent.getStringExtra(EXTRA_TEAM_LOGO) ?: ""

        setupHeader()
        setupRecyclerView()
        setupFilterListeners()
        observeViewModel()
        loadNativeAd()

        if (teamId > 0) {
            viewModel.loadTeamFixtures(teamId = teamId, teamName = teamName, last = 5)
        } else {
            binding.layoutError.isVisible = true
            binding.tvErrorMessage.text = getString(R.string.team_fixtures_invalid_id)
        }
    }

    private fun loadNativeAd() {
        if (::limitManager.isInitialized && limitManager.isPremium()) {
            binding.frAdsTeamFixtures.visibility = View.GONE
            return
        }

        val adId = try {
            RemoteConfigManager.getInstance().getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        if (adId.isNotEmpty()) {
            binding.frAdsTeamFixtures.visibility = View.VISIBLE
            val shimmerView = LayoutInflater.from(this).inflate(R.layout.layout_shimmer_league, binding.frAdsTeamFixtures, false)
            binding.frAdsTeamFixtures.removeAllViews()
            binding.frAdsTeamFixtures.addView(shimmerView)

            Admob.getInstance().loadNativeAds(
                this,
                adId,
                1,
                object : NativeCallback() {
                    override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                        super.onNativeAdLoaded(nativeAd)

                        val adView = LayoutInflater.from(this@TeamFixturesActivity)
                            .inflate(R.layout.layout_native_league, null) as NativeAdView
                        
                        binding.frAdsTeamFixtures.removeAllViews()
                        binding.frAdsTeamFixtures.addView(adView)
                        
                        Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    }

                    override fun onAdFailedToLoad() {
                        super.onAdFailedToLoad()
                        binding.frAdsTeamFixtures.visibility = View.GONE
                    }
                }
            )
        } else {
            binding.frAdsTeamFixtures.visibility = View.GONE
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        binding.tvTeamNameHeader.text = teamName.ifEmpty { getString(R.string.team_fixtures_title) }
        if (teamLogo.isNotEmpty()) {
            binding.ivTeamLogoHeader.isVisible = true
            Glide.with(this)
                .load(teamLogo)
                .placeholder(R.mipmap.ic_launcher)
                .into(binding.ivTeamLogoHeader)
        } else {
            binding.ivTeamLogoHeader.isVisible = false
        }
    }

    private fun setupRecyclerView() {
        fixturesAdapter = TeamFixturesAdapter(
            currentTeamId = teamId,
            onMatchClick = { fixtureItem ->
                val fId = fixtureItem.getRealFixtureId
                if (fId > 0) {
                    val intent = Intent(this@TeamFixturesActivity, MatchDetailActivity::class.java).apply {
                        putExtra("MATCH_ID", fId)
                        putExtra("fixture_id", fId)
                    }
                    AdsConfig.showInterClickAd(this@TeamFixturesActivity) {
                        startActivity(intent)
                    }
                }
            }
        )
        binding.rvFixtures.apply {
            layoutManager = LinearLayoutManager(this@TeamFixturesActivity)
            adapter = fixturesAdapter
        }
    }

    private fun setupFilterListeners() {
        val filterButtons = mapOf(
            binding.btnLast5 to 5,
            binding.btnLast10 to 10,
            binding.btnLast15 to 15,
            binding.btnLast20 to 20
        )

        filterButtons.forEach { (view, lastCount) ->
            view.setOnClickListener {
                updateFilterStyle(view)
                viewModel.selectLastFilter(lastCount)
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            val currentLast = when {
                binding.btnLast10.isSelected -> 10
                binding.btnLast15.isSelected -> 15
                binding.btnLast20.isSelected -> 20
                else -> 5
            }
            viewModel.loadTeamFixtures(teamId, teamName, currentLast)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadTeamFixtures(teamId, teamName, 5)
        }
    }

    private fun updateFilterStyle(selectedView: TextView) {
        val buttons = listOf(
            binding.btnLast5,
            binding.btnLast10,
            binding.btnLast15,
            binding.btnLast20
        )

        val selectedBg = R.drawable.bg_team_filter_selected
        val unselectedBg = R.drawable.bg_team_filter_unselected

        buttons.forEach { button ->
            if (button == selectedView) {
                button.isSelected = true
                button.setBackgroundResource(selectedBg)
                button.setTextColor(ContextCompat.getColor(this, R.color.primaryGreen))
                button.setTypeface(null, Typeface.BOLD)
            } else {
                button.isSelected = false
                button.setBackgroundResource(unselectedBg)
                button.setTextColor(ContextCompat.getColor(this, R.color.textSecondary))
                button.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = false
                    when (state) {
                        is TeamFixturesUiState.Loading -> {
                            binding.progressBar.isVisible = true
                            binding.rvFixtures.isVisible = false
                            binding.layoutError.isVisible = false
                        }
                        is TeamFixturesUiState.Success -> {
                            binding.progressBar.isVisible = false
                            binding.layoutError.isVisible = false
                            binding.rvFixtures.isVisible = true

                            binding.tvWins.text = state.wins.toString()
                            binding.tvDraws.text = state.draws.toString()
                            binding.tvLosses.text = state.losses.toString()

                            fixturesAdapter.submitList(state.fixtures)

                            if (state.fixtures.isEmpty()) {
                                binding.layoutError.isVisible = true
                                binding.tvErrorMessage.text = getString(R.string.team_fixtures_no_data)
                            }
                        }
                        is TeamFixturesUiState.Error -> {
                            binding.progressBar.isVisible = false
                            binding.rvFixtures.isVisible = false
                            binding.layoutError.isVisible = true
                            binding.tvErrorMessage.text = if (state.message.contains("HTTP", ignoreCase = true) || 
                                state.message.contains("429") || 
                                state.message.contains("Exception", ignoreCase = true)) {
                                getString(R.string.team_fixtures_no_data)
                            } else {
                                state.message
                            }
                        }
                    }
                }
            }
        }
    }
}
