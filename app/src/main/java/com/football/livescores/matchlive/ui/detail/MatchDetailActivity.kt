package com.football.livescores.matchlive.ui.detail

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob
import com.football.livescores.matchlive.base.BaseActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.databinding.ActivityMatchDetailBinding
import com.football.livescores.matchlive.ui.custom.PremiumPaywallDialog
import com.football.livescores.matchlive.ui.custom.TimelineEvent
import com.football.livescores.matchlive.ui.iap.IAPActivity
import com.football.livescores.matchlive.utils.AdsConfig
import com.football.livescores.matchlive.utils.SharePreferenceUtils
import com.football.livescores.matchlive.utils.LogEvent
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.football.livescores.matchlive.utils.InterCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class MatchDetailActivity : BaseActivity() {

    @Inject
    lateinit var limitManager: RequestLimitManager

    private lateinit var binding: ActivityMatchDetailBinding
    private val viewModel: MatchDetailViewModel by viewModels()

    private lateinit var eventAdapter: EventAdapter
    private lateinit var lineupAdapter: LineupAdapter

    override fun bind() {
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup onBackPressed frequency capped interstitial ad (35s)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsConfig.showInterClickAd(this@MatchDetailActivity) {
                    isEnabled = false
                    if (isTaskRoot) {
                        val intent = Intent(this@MatchDetailActivity, com.football.livescores.matchlive.ui.main.MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        val matchId = intent.getIntExtra("MATCH_ID", intent.getIntExtra("fixture_id", -1))
        if (matchId == -1) {
            finish()
            return
        }

        setupUI(matchId)
        setupLockOverlays()
        observeViewModel()
        viewModel.startDetailPolling(matchId)
        if(!SharePreferenceUtils.isOrganic(baseContext)) {
            loadNativeAd()
        } else {
            binding.frAdsDetail.removeAllViews()
            binding.frAdsDetail.visibility = View.GONE
        }
    }

    private fun setupUI(matchId: Int) {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnShare.setOnClickListener {
            shareCombinedMatchView(matchId)
        }

        // Subtabs selection setup
        binding.btnTabStats.setOnClickListener { switchTab(0) }
        binding.btnTabTimeline.setOnClickListener { switchTab(1) }
        binding.btnTabLineups.setOnClickListener { switchTab(2) }
        
        // Initialize default sub-tab selection state
        switchTab(0)

        // Setup Event list
        eventAdapter = EventAdapter()
        binding.layoutTimeline.rvEventsList.layoutManager = LinearLayoutManager(this)
        binding.layoutTimeline.rvEventsList.adapter = eventAdapter

        // Setup Lineup list
        lineupAdapter = LineupAdapter()
        binding.layoutLineups.rvLineupPlayers.layoutManager = LinearLayoutManager(this)
        binding.layoutLineups.rvLineupPlayers.adapter = lineupAdapter

        // Main tabs selection setup
        binding.btnTabMatch.setOnClickListener { switchMainTab(0, matchId) }
        binding.btnTabPrediction.setOnClickListener {
            if (binding.layoutPredictionContent.isVisible) {
                return@setOnClickListener
            }
            loadInterPrediction(matchId)
        }

        // Setup prediction lock overlay button click and localization
        binding.layoutPredictionLockOverlay.tvLockTitle.text = getString(R.string.prediction_locked_title)
        binding.layoutPredictionLockOverlay.tvLockSubtitle.text = getString(R.string.prediction_locked_desc)
        binding.layoutPredictionLockOverlay.btnUnlockNow.setOnClickListener {
            startActivity(Intent(this, com.football.livescores.matchlive.ui.iap.IAPActivity::class.java))
        }

        // Select the default main tab
        val selectPrediction = intent.getBooleanExtra("SELECT_PREDICTION_TAB", false)
        if (selectPrediction) {
            loadInterPrediction(matchId)
        } else {
            switchMainTab(0, matchId)
        }
    }

    private fun captureViewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(this, R.color.background))
        view.draw(canvas)
        return bitmap
    }

    private fun shareMatchView(view: View, matchId: Int) {
        try {
            val bitmap = captureViewToBitmap(view)
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_match.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val detail = viewModel.uiState.value.detail
            val homeName = detail?.teams?.home?.name ?: "Home"
            val awayName = detail?.teams?.away?.name ?: "Away"
            val caption = getString(R.string.share_caption_template, homeName, awayName, matchId)

            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    type = "image/png"
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(this, getString(R.string.toast_share_match_failed), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCombinedMatchView(matchId: Int) {
        try {
            val activeContentView = when {
                binding.containerStats.isVisible -> binding.containerStats
                binding.layoutTimeline.root.isVisible -> binding.layoutTimeline.root
                binding.layoutLineups.root.isVisible -> binding.layoutLineups.root
                else -> null
            }

            val headerWidth = binding.matchHeader.root.width
            val headerHeight = binding.matchHeader.root.height

            if (activeContentView == null) {
                shareMatchView(binding.matchHeader.root, matchId)
                return
            }

            val contentWidth = activeContentView.width
            val contentHeight = activeContentView.height

            val totalWidth = maxOf(headerWidth, contentWidth)
            val totalHeight = headerHeight + contentHeight

            if (totalWidth <= 0 || totalHeight <= 0) {
                shareMatchView(binding.matchHeader.root, matchId)
                return
            }

            val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(ContextCompat.getColor(this, R.color.background))

            // 1. Draw Match Header
            binding.matchHeader.root.draw(canvas)

            // 2. Draw Active Content View below Match Header
            canvas.save()
            canvas.translate(0f, headerHeight.toFloat())
            activeContentView.draw(canvas)
            canvas.restore()

            // Save combined bitmap to cache directory
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_match.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            // Generate localized dynamic caption
            val detail = viewModel.uiState.value.detail
            val homeName = detail?.teams?.home?.name ?: "Home"
            val awayName = detail?.teams?.away?.name ?: "Away"
            val caption = getString(R.string.share_caption_template, homeName, awayName, matchId)

            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    type = "image/png"
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            shareMatchView(binding.root, matchId)
        }
    }

    private fun switchTab(tabIndex: Int) {
        if (!limitManager.isPremium() && (tabIndex == 0 || tabIndex == 2)) {
            if (limitManager.isLimitExceeded() || limitManager.isNearQuotaLimit()) {
                showPremiumPaywall()
                return
            }
        }

        val activeBlueColor = ContextCompat.getColor(this, R.color.primaryBlue)
        val whiteColor = Color.WHITE

        val statsSelected = (tabIndex == 0)
        val timelineSelected = (tabIndex == 1)
        val lineupsSelected = (tabIndex == 2)

        binding.btnTabStats.apply {
            setTextColor(if (statsSelected) activeBlueColor else whiteColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(if (statsSelected) R.drawable.bg_sub_tab_selected else 0)
        }

        binding.btnTabTimeline.apply {
            setTextColor(if (timelineSelected) activeBlueColor else whiteColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(if (timelineSelected) R.drawable.bg_sub_tab_selected else 0)
        }

        binding.btnTabLineups.apply {
            setTextColor(if (lineupsSelected) activeBlueColor else whiteColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(if (lineupsSelected) R.drawable.bg_sub_tab_selected else 0)
        }

        binding.containerStats.isVisible = tabIndex == 0
        binding.layoutTimeline.root.isVisible = tabIndex == 1
        binding.layoutLineups.root.isVisible = tabIndex == 2
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe UI State containing remote response models
                launch {
                    viewModel.uiState.collect { state ->
                        state.detail?.let { detail ->
                            binding.matchHeader.tvDetailLeague.text = detail.league.name.uppercase()
                            binding.matchHeader.tvDetailHomeName.text = detail.teams.home.name
                            binding.matchHeader.tvDetailAwayName.text = detail.teams.away.name

                            binding.matchHeader.tvDetailScore.text =
                                "${detail.goals.home ?: 0} - ${detail.goals.away ?: 0}"
                            
                            val statusShort = detail.fixture.status.short
                            if (statusShort == "NS" || statusShort == "TBD") {
                                 val matchDate = java.util.Date(detail.fixture.timestamp * 1000)
                                 val sdfToday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                                     timeZone = java.util.TimeZone.getDefault()
                                 }
                                 val isToday = sdfToday.format(matchDate) == sdfToday.format(java.util.Date())
                                 val locale = java.util.Locale.getDefault()
                                 
                                 val statusText = if (isToday) {
                                     val timeSdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
                                         timeZone = java.util.TimeZone.getDefault()
                                     }
                                     getString(R.string.today) + ", " + timeSdf.format(matchDate)
                                 } else {
                                     val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply {
                                         timeZone = java.util.TimeZone.getDefault()
                                     }
                                     sdf.format(matchDate)
                                 }
                                 binding.matchHeader.tvDetailStatus.text = statusText
                            } else {
                                binding.matchHeader.tvDetailStatus.text =
                                    detail.fixture.status.elapsed?.let { "${it}' LIVE" } ?: detail.fixture.status.long
                            }

                             Glide.with(this@MatchDetailActivity).load(detail.teams.home.logo).into(binding.matchHeader.ivDetailHomeLogo)
                             Glide.with(this@MatchDetailActivity).load(detail.teams.away.logo).into(binding.matchHeader.ivDetailAwayLogo)
 
                             // Setup dynamic values in Football Pitch View
                             binding.pitchTracker.pitchView.setTeamNames(detail.teams.home.name, detail.teams.away.name)
                             
                             val rawHome = detail.teams.home.name.uppercase()
                             binding.pitchTracker.tvHomeLegend.text = if (rawHome.length > 12) {
                                 "${rawHome.take(12)}..."
                             } else {
                                 rawHome
                             }
                             
                             val rawAway = detail.teams.away.name.uppercase()
                             binding.pitchTracker.tvAwayLegend.text = if (rawAway.length > 12) {
                                 "${rawAway.take(12)}..."
                             } else {
                                 rawAway
                             }
                            
                            val elapsed = detail.fixture.status.elapsed ?: 0
                            val period = when (detail.fixture.status.long.uppercase()) {
                                "FIRST HALF", "1H" -> "1st Half"
                                "SECOND HALF", "2H" -> "2nd Half"
                                "HALFTIME", "HT" -> "Halftime"
                                "MATCH FINISHED", "FT" -> "Full Time"
                                else -> "LIVE"
                            }
                            val matchStatusStr = if (elapsed > 45 && detail.fixture.status.long.uppercase().contains("FIRST")) {
                                "$period | 45:00 +00:${String.format("%02d", elapsed - 45)}"
                            } else if (elapsed > 90 && detail.fixture.status.long.uppercase().contains("SECOND")) {
                                "$period | 90:00 +00:${String.format("%02d", elapsed - 90)}"
                            } else {
                                "$period | ${String.format("%02d", elapsed)}:00"
                            }
                            binding.pitchTracker.pitchView.setMatchStatus(matchStatusStr, "${elapsed}'")

                            // Setup visual timeline events list
                            binding.layoutTimeline.timelineVisualView.setMatchMinute(detail.fixture.status.elapsed ?: 90)
                        }

                        // Set statistics values in UI
                        state.stats.let { list ->
                            if (list.isNotEmpty()) {
                                val homeTeamId = state.detail?.teams?.home?.id
                                val awayTeamId = state.detail?.teams?.away?.id

                                val homeStatsItem = list.find { it.team.id == homeTeamId } ?: list.getOrNull(0)
                                val awayStatsItem = list.find { it.team.id == awayTeamId } ?: list.getOrNull(1)

                                val homeStats = homeStatsItem?.statistics ?: emptyList()
                                val awayStats = awayStatsItem?.statistics ?: emptyList()

                                fun getStatValue(stats: List<com.football.livescores.matchlive.data.remote.model.StatEntryDto>, vararg keys: String): Int {
                                    val entry = stats.find { s ->
                                        keys.any { k ->
                                            s.type.equals(k, ignoreCase = true) ||
                                            s.type.contains(k, ignoreCase = true) ||
                                            k.contains(s.type, ignoreCase = true)
                                        }
                                    }?.value
                                    return when (entry) {
                                        is Double -> entry.toInt()
                                        is Float -> entry.toInt()
                                        is String -> entry.replace("%", "").trim().toIntOrNull() ?: 0
                                        else -> (entry as? Number)?.toInt() ?: 0
                                    }
                                }

                                val hp = getStatValue(homeStats, "Ball Possession", "Possession", "possession_pct")
                                val ap = getStatValue(awayStats, "Ball Possession", "Possession", "possession_pct")
                                binding.layoutStats.tvStatHomePossession.text = "$hp%"
                                binding.layoutStats.tvStatAwayPossession.text = "$ap%"
                                binding.layoutStats.progressHomePossession.progress = hp
                                binding.layoutStats.progressAwayPossession.progress = ap

                                val hs = getStatValue(homeStats, "Total Shots", "Shots", "shots_total")
                                val asShots = getStatValue(awayStats, "Total Shots", "Shots", "shots_total")
                                binding.layoutStats.tvStatHomeShots.text = hs.toString()
                                binding.layoutStats.tvStatAwayShots.text = asShots.toString()
                                val totalShots = hs + asShots
                                binding.layoutStats.progressHomeShots.progress = if (totalShots > 0) (hs * 100) / totalShots else 50
                                binding.layoutStats.progressAwayShots.progress = if (totalShots > 0) (asShots * 100) / totalShots else 50

                                val hst = getStatValue(homeStats, "Shots on Target", "Shots on Goal", "shots_on_target")
                                val ast = getStatValue(awayStats, "Shots on Target", "Shots on Goal", "shots_on_target")
                                binding.layoutStats.tvStatHomeShotsTarget.text = hst.toString()
                                binding.layoutStats.tvStatAwayShotsTarget.text = ast.toString()
                                val totalShotsTarget = hst + ast
                                binding.layoutStats.progressHomeShotsTarget.progress = if (totalShotsTarget > 0) (hst * 100) / totalShotsTarget else 50
                                binding.layoutStats.progressAwayShotsTarget.progress = if (totalShotsTarget > 0) (ast * 100) / totalShotsTarget else 50

                                val hso = getStatValue(homeStats, "Shots off Goal", "Shots off Target", "shots_off_target")
                                val aso = getStatValue(awayStats, "Shots off Goal", "Shots off Target", "shots_off_target")
                                binding.layoutStats.tvStatHomeShotsOffTarget.text = hso.toString()
                                binding.layoutStats.tvStatAwayShotsOffTarget.text = aso.toString()
                                val totalSo = hso + aso
                                binding.layoutStats.progressHomeShotsOffTarget.progress = if (totalSo > 0) (hso * 100) / totalSo else 50
                                binding.layoutStats.progressAwayShotsOffTarget.progress = if (totalSo > 0) (aso * 100) / totalSo else 50

                                val hbs = getStatValue(homeStats, "Blocked Shots", "blocked_shots")
                                val abs = getStatValue(awayStats, "Blocked Shots", "blocked_shots")
                                binding.layoutStats.tvStatHomeBlockedShots.text = hbs.toString()
                                binding.layoutStats.tvStatAwayBlockedShots.text = abs.toString()
                                val totalBs = hbs + abs
                                binding.layoutStats.progressHomeBlockedShots.progress = if (totalBs > 0) (hbs * 100) / totalBs else 50
                                binding.layoutStats.progressAwayBlockedShots.progress = if (totalBs > 0) (abs * 100) / totalBs else 50

                                val hsib = getStatValue(homeStats, "Shots insidebox", "inside_box")
                                val asib = getStatValue(awayStats, "Shots insidebox", "inside_box")
                                binding.layoutStats.tvStatHomeShotsInsideBox.text = hsib.toString()
                                binding.layoutStats.tvStatAwayShotsInsideBox.text = asib.toString()
                                val totalSib = hsib + asib
                                binding.layoutStats.progressHomeShotsInsideBox.progress = if (totalSib > 0) (hsib * 100) / totalSib else 50
                                binding.layoutStats.progressAwayShotsInsideBox.progress = if (totalSib > 0) (asib * 100) / totalSib else 50

                                val hsob = getStatValue(homeStats, "Shots outsidebox", "outside_box")
                                val asob = getStatValue(awayStats, "Shots outsidebox", "outside_box")
                                binding.layoutStats.tvStatHomeShotsOutsideBox.text = hsob.toString()
                                binding.layoutStats.tvStatAwayShotsOutsideBox.text = asob.toString()
                                val totalSob = hsob + asob
                                binding.layoutStats.progressHomeShotsOutsideBox.progress = if (totalSob > 0) (hsob * 100) / totalSob else 50
                                binding.layoutStats.progressAwayShotsOutsideBox.progress = if (totalSob > 0) (asob * 100) / totalSob else 50

                                val hc = getStatValue(homeStats, "Corner Kicks", "Corners", "corner_kicks")
                                val ac = getStatValue(awayStats, "Corner Kicks", "Corners", "corner_kicks")
                                binding.layoutStats.tvStatHomeCorners.text = hc.toString()
                                binding.layoutStats.tvStatAwayCorners.text = ac.toString()
                                val totalCorners = hc + ac
                                binding.layoutStats.progressHomeCorners.progress = if (totalCorners > 0) (hc * 100) / totalCorners else 50
                                binding.layoutStats.progressAwayCorners.progress = if (totalCorners > 0) (ac * 100) / totalCorners else 50

                                val ho = getStatValue(homeStats, "Offsides", "offsides")
                                val ao = getStatValue(awayStats, "Offsides", "offsides")
                                binding.layoutStats.tvStatHomeOffsides.text = ho.toString()
                                binding.layoutStats.tvStatAwayOffsides.text = ao.toString()
                                val totalO = ho + ao
                                binding.layoutStats.progressHomeOffsides.progress = if (totalO > 0) (ho * 100) / totalO else 50
                                binding.layoutStats.progressAwayOffsides.progress = if (totalO > 0) (ao * 100) / totalO else 50

                                val hf = getStatValue(homeStats, "Fouls", "fouls")
                                val af = getStatValue(awayStats, "Fouls", "fouls")
                                binding.layoutStats.tvStatHomeFouls.text = hf.toString()
                                binding.layoutStats.tvStatAwayFouls.text = af.toString()
                                val totalF = hf + af
                                binding.layoutStats.progressHomeFouls.progress = if (totalF > 0) (hf * 100) / totalF else 50
                                binding.layoutStats.progressAwayFouls.progress = if (totalF > 0) (af * 100) / totalF else 50

                                val hy = getStatValue(homeStats, "Yellow Cards", "yellow_cards")
                                val ay = getStatValue(awayStats, "Yellow Cards", "yellow_cards")
                                binding.layoutStats.tvStatHomeYellowCards.text = hy.toString()
                                binding.layoutStats.tvStatAwayYellowCards.text = ay.toString()
                                val totalY = hy + ay
                                binding.layoutStats.progressHomeYellowCards.progress = if (totalY > 0) (hy * 100) / totalY else 50
                                binding.layoutStats.progressAwayYellowCards.progress = if (totalY > 0) (ay * 100) / totalY else 50

                                val hr = getStatValue(homeStats, "Red Cards", "red_cards")
                                val ar = getStatValue(awayStats, "Red Cards", "red_cards")
                                binding.layoutStats.tvStatHomeRedCards.text = hr.toString()
                                binding.layoutStats.tvStatAwayRedCards.text = ar.toString()
                                val totalR = hr + ar
                                binding.layoutStats.progressHomeRedCards.progress = if (totalR > 0) (hr * 100) / totalR else 0
                                binding.layoutStats.progressAwayRedCards.progress = if (totalR > 0) (ar * 100) / totalR else 0

                                val hgs = getStatValue(homeStats, "Goalkeeper Saves", "Saves", "goalkeeper_saves")
                                val ags = getStatValue(awayStats, "Goalkeeper Saves", "Saves", "goalkeeper_saves")
                                binding.layoutStats.tvStatHomeSaves.text = hgs.toString()
                                binding.layoutStats.tvStatAwaySaves.text = ags.toString()
                                val totalS = hgs + ags
                                binding.layoutStats.progressHomeSaves.progress = if (totalS > 0) (hgs * 100) / totalS else 50
                                binding.layoutStats.progressAwaySaves.progress = if (totalS > 0) (ags * 100) / totalS else 50

                                val hpPasses = getStatValue(homeStats, "Total Passes", "Passes", "total_passes")
                                val apPasses = getStatValue(awayStats, "Total Passes", "Passes", "total_passes")
                                binding.layoutStats.tvStatHomePasses.text = hpPasses.toString()
                                binding.layoutStats.tvStatAwayPasses.text = apPasses.toString()
                                val totalP = hpPasses + apPasses
                                binding.layoutStats.progressHomePasses.progress = if (totalP > 0) (hpPasses * 100) / totalP else 50
                                binding.layoutStats.progressAwayPasses.progress = if (totalP > 0) (apPasses * 100) / totalP else 50

                                val hpa = getStatValue(homeStats, "Passes accurate", "Accurate Passes", "passes_accurate")
                                val apa = getStatValue(awayStats, "Passes accurate", "Accurate Passes", "passes_accurate")
                                binding.layoutStats.tvStatHomePassesAccurate.text = hpa.toString()
                                binding.layoutStats.tvStatAwayPassesAccurate.text = apa.toString()
                                val totalPa = hpa + apa
                                binding.layoutStats.progressHomePassesAccurate.progress = if (totalPa > 0) (hpa * 100) / totalPa else 50
                                binding.layoutStats.progressAwayPassesAccurate.progress = if (totalPa > 0) (apa * 100) / totalPa else 50
                            }
                        }

                        // Submit lineups lists
                        state.lineups.let { lineups ->
                            if (lineups.isNotEmpty()) {
                                val homeTeamId = state.detail?.teams?.home?.id
                                val awayTeamId = state.detail?.teams?.away?.id

                                val homeLineup = lineups.find { it.team.id == homeTeamId } ?: lineups.getOrNull(0)
                                val awayLineup = lineups.find { it.team.id == awayTeamId } ?: lineups.getOrNull(1)

                                binding.layoutLineups.tvHomeFormation.text = homeLineup?.formation ?: ""
                                binding.layoutLineups.tvAwayFormation.text = awayLineup?.formation ?: ""
                                lineupAdapter.submitLineups(homeLineup?.startXI ?: emptyList(), awayLineup?.startXI ?: emptyList())
                            }
                        }

                        // Set events
                        state.events.let { events ->
                            val homeTeamId = state.detail?.teams?.home?.id
                            eventAdapter.submitList(events, homeTeamId)

                            val canvasEvents = events.map {
                                TimelineEvent(
                                    minute = it.time.elapsed,
                                    type = when (it.type.uppercase()) {
                                        "GOAL" -> "GOAL"
                                        "CARD" -> if (it.detail.contains("Red", true)) "CARD_RED" else "CARD_YELLOW"
                                        "SUBST" -> "SUBST"
                                        else -> "GOAL"
                                    },
                                    isHome = it.team.id == homeTeamId
                                )
                            }
                            binding.layoutTimeline.timelineVisualView.setEvents(canvasEvents)
                        }

                        // Bind prediction data and loading state
                        binding.progressPredictionLoading.isVisible = state.isPredictionLoading
                        if (state.isPredictionLoading) {
                            binding.layoutMatchPrediction.root.visibility = View.GONE
                        } else {
                            binding.layoutMatchPrediction.root.visibility = if (state.prediction != null) View.VISIBLE else View.GONE
                            state.prediction?.let { prediction ->
                                bindPredictionData(prediction)
                            }
                        }
                    }
                }

                // Observe Canvas simulation parameters
                launch {
                    viewModel.ballPosition.collect { position ->
                        binding.pitchTracker.pitchView.updateBallPosition(position.first, position.second)
                    }
                }

                launch {
                    viewModel.attackState.collect { side ->
                        when (side) {
                            1 -> binding.pitchTracker.pitchView.triggerHomeAttack()
                            2 -> binding.pitchTracker.pitchView.triggerAwayAttack()
                            else -> binding.pitchTracker.pitchView.clearAttack()
                        }
                    }
                }

                launch {
                    viewModel.momentumData.collect { data ->
                        binding.pitchTracker.momentumView.setMomentumData(data)
                    }
                }

                // Observe request limit exceeds inside detail screen and popup paywall instantly
                launch {
                    limitManager.limitExceededFlow.collect {
                        if (!limitManager.isPremium()) {
                            showPremiumPaywall(isOutOfQuota = true)
                        }
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
    }

    private fun setupLockOverlays() {
        // Local in-view lock card overlays are permanently hidden as per user request
        binding.layoutPitchLockOverlay.root.visibility = View.GONE
        binding.layoutStatsLockOverlay.root.visibility = View.GONE

        // Pop up the Bottom Sheet directly if near limit
        if (limitManager.isNearQuotaLimit() || limitManager.isLimitExceeded()) {
            showPremiumPaywall()
        }
    }

    private fun showPremiumPaywall(isOutOfQuota: Boolean = false) {
        if (supportFragmentManager.isStateSaved) return
        val existing = supportFragmentManager.findFragmentByTag(PremiumPaywallDialog.TAG)
        if (existing == null) {
            val paywall = PremiumPaywallDialog.newInstance(isOutOfQuota)
            paywall.show(supportFragmentManager, PremiumPaywallDialog.TAG)
        }
    }

    private fun switchMainTab(tabIndex: Int, matchId: Int) {
        val whiteColor = Color.WHITE

        binding.btnTabMatch.setTextColor(whiteColor)
        binding.btnTabMatch.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnTabMatch.backgroundTintList = null
        binding.btnTabMatch.setBackgroundResource(
            if (tabIndex == 0) R.drawable.bg_tab_nav_selected else R.drawable.bg_tab_nav_unselected
        )

        binding.btnTabPrediction.setTextColor(whiteColor)
        binding.btnTabPrediction.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnTabPrediction.backgroundTintList = null
        binding.btnTabPrediction.setBackgroundResource(
            if (tabIndex == 1) R.drawable.bg_tab_nav_selected else R.drawable.bg_tab_nav_unselected
        )

        binding.layoutMatchContent.isVisible = tabIndex == 0
        binding.layoutPredictionContent.isVisible = tabIndex == 1
        binding.matchHeader.root.isVisible = tabIndex == 0

        if (tabIndex == 1) {
            binding.layoutMatchPrediction.root.isVisible = true
            binding.layoutPredictionLockOverlay.root.isVisible = false

            val savedLang = com.football.livescores.matchlive.utils.SystemUtil.getPreLanguage(this)
            val lang = if (savedLang == "vi") "vi" else "en"
            viewModel.fetchAiPrediction(matchId, lang)
        }
    }

    private fun loadInterPrediction(matchId: Int) {
        if (limitManager.isPremium()) {
            switchMainTab(1, matchId)
            return
        }

        if (!SharePreferenceUtils.isOrganic(this)) {
            val adId = try {
                RemoteConfigManager.getInstance().getAdId("inter_click_prediction", getString(R.string.inter_click_prediction))
            } catch (e: Exception) {
                getString(R.string.inter_click_prediction)
            }

            if (adId.isNotEmpty()) {
                LogEvent.log(this, "inter_click_prediction")

                Admob.getInstance().loadAndShowInter(
                    this,
                    adId,
                    0,
                    30000,
                    object : InterCallback() {
                        override fun onAdClosed() {
                            super.onAdClosed()
                            switchMainTab(1, matchId)
                        }

                        override fun onAdClosedByUser() {
                            super.onAdClosedByUser()
                            switchMainTab(1, matchId)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError?) {
                            super.onAdFailedToLoad(error)
                            switchMainTab(1, matchId)
                        }
                    }
                )
            } else {
                switchMainTab(1, matchId)
            }
        } else {
            switchMainTab(1, matchId)
        }
    }

    private fun bindPredictionData(prediction: com.football.livescores.matchlive.data.remote.model.PredictionDataDto) {
        val detail = viewModel.uiState.value.detail

        val isPremium = limitManager.isPremium()
        
        // Adjust locked section layout height and overlay visibility depending on premium state
        val params = binding.layoutMatchPrediction.layoutPremiumLockedSection.layoutParams
        if (isPremium) {
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            binding.layoutMatchPrediction.layoutPremiumBlurOverlay.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.layoutMatchPrediction.layoutPremiumLockedSection.setRenderEffect(null)
            }
        } else {
            // Show a portion of the content (250dp) with strong blur so text is unreadable
            params.height = (350 * resources.displayMetrics.density).toInt()
            binding.layoutMatchPrediction.layoutPremiumBlurOverlay.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEffect = RenderEffect.createBlurEffect(
                    16f,
                    16f,
                    Shader.TileMode.CLAMP
                )
                binding.layoutMatchPrediction.layoutPremiumLockedSection.setRenderEffect(blurEffect)
            }
            
            // Set up click listener on "See More" button to display the custom Dialog popup
            binding.layoutMatchPrediction.btnGoPremium.setOnClickListener {
                val intent = Intent(applicationContext, IAPActivity::class.java)
                startActivity(intent)
            }
        }
        binding.layoutMatchPrediction.layoutPremiumLockedSection.layoutParams = params

        // 1. Team Logos & Names
        val homeTeamName = detail?.teams?.home?.name ?: prediction.home_team.name
        val awayTeamName = detail?.teams?.away?.name ?: prediction.away_team.name

        if (detail != null) {
            Glide.with(this).load(detail.teams.home.logo).into(binding.layoutMatchPrediction.ivPredictHomeLogo)
            Glide.with(this).load(detail.teams.away.logo).into(binding.layoutMatchPrediction.ivPredictAwayLogo)
            Glide.with(this).load(detail.teams.home.logo).into(binding.layoutMatchPrediction.ivHomeTeamSWLogo)
            Glide.with(this).load(detail.teams.away.logo).into(binding.layoutMatchPrediction.ivAwayTeamSWLogo)
            binding.layoutMatchPrediction.tvPredictHomeName.text = detail.teams.home.name
            binding.layoutMatchPrediction.tvPredictAwayName.text = detail.teams.away.name
        } else {
            Glide.with(this).load(prediction.home_team.logo).into(binding.layoutMatchPrediction.ivPredictHomeLogo)
            Glide.with(this).load(prediction.away_team.logo).into(binding.layoutMatchPrediction.ivPredictAwayLogo)
            Glide.with(this).load(prediction.home_team.logo).into(binding.layoutMatchPrediction.ivHomeTeamSWLogo)
            Glide.with(this).load(prediction.away_team.logo).into(binding.layoutMatchPrediction.ivAwayTeamSWLogo)
            binding.layoutMatchPrediction.tvPredictHomeName.text = prediction.home_team.name
            binding.layoutMatchPrediction.tvPredictAwayName.text = prediction.away_team.name
        }

        // Highlight Predicted Winner Name
        val homeWinner = prediction.winner_prediction == "home"
        val awayWinner = prediction.winner_prediction == "away"

        binding.layoutMatchPrediction.tvPredictHomeName.setTextColor(
            if (homeWinner) ContextCompat.getColor(this, R.color.accent_green) else ContextCompat.getColor(this, R.color.text_white)
        )
        binding.layoutMatchPrediction.tvPredictAwayName.setTextColor(
            if (awayWinner) ContextCompat.getColor(this, R.color.accent_green) else ContextCompat.getColor(this, R.color.text_white)
        )

        // 2. Score Prediction & Confidence
        val homeScore = prediction.score_prediction?.home ?: 0
        val awayScore = prediction.score_prediction?.away ?: 0
        binding.layoutMatchPrediction.tvPredictScore.text = "$homeScore - $awayScore"
        binding.layoutMatchPrediction.tvPredictConfidence.text = "${prediction.confidence_score ?: 0}%"
        binding.layoutMatchPrediction.progressConfidence.progress = prediction.confidence_score ?: 0

        // 3. Over / Under & BTTS
        binding.layoutMatchPrediction.tvPredictOverUnder.text = prediction.over_under_prediction ?: ""

        val isBtts = prediction.btts_prediction == true
        binding.layoutMatchPrediction.tvPredictBtts.text = if (isBtts) getString(R.string.ai_yes) else getString(R.string.ai_no)
        binding.layoutMatchPrediction.tvPredictBtts.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (isBtts) R.color.accent_green else R.color.primaryRed)
        )
        binding.layoutMatchPrediction.tvPredictBtts.setTextColor(
            ContextCompat.getColor(this, if (isBtts) R.color.bg_light else R.color.text_white)
        )

        // 4. Prominent Players
        binding.layoutMatchPrediction.layoutGoalscorersContainer.removeAllViews()
        val prominentPlayers = prediction.prominent_players ?: emptyList()
        if (prominentPlayers.isEmpty()) {
            binding.layoutMatchPrediction.cardGoalscorers.visibility = View.GONE
        } else {
            binding.layoutMatchPrediction.cardGoalscorers.visibility = View.VISIBLE
            for (player in prominentPlayers) {
                val scorerBinding = com.football.livescores.matchlive.databinding.ItemPredictedGoalscorerBinding.inflate(
                    layoutInflater,
                    binding.layoutMatchPrediction.layoutGoalscorersContainer,
                    false
                )
                scorerBinding.tvPlayerName.text = player.player_name
                val teamName = if (player.team == "home") homeTeamName else awayTeamName
                val teamLogoUrl = if (player.team == "home") {
                    detail?.teams?.home?.logo ?: prediction.home_team.logo
                } else {
                    detail?.teams?.away?.logo ?: prediction.away_team.logo
                }
                scorerBinding.tvTeamBadge.text = teamName
                Glide.with(this@MatchDetailActivity).load(teamLogoUrl).into(scorerBinding.ivTeamLogo)
                scorerBinding.tvProbability.text = "${player.probability}%"
                scorerBinding.progressProbability.progress = player.probability

                if (!player.reason.isNullOrEmpty()) {
                    scorerBinding.tvPlayerReason.text = player.reason
                    scorerBinding.tvPlayerReason.visibility = View.VISIBLE
                } else {
                    scorerBinding.tvPlayerReason.visibility = View.GONE
                }

                binding.layoutMatchPrediction.layoutGoalscorersContainer.addView(scorerBinding.root)
            }
        }

        // 5. Match Analysis & Squad Impact
        binding.layoutMatchPrediction.tvAnalysisText.text = prediction.form_overview ?: ""

        val hasSquadImpact = !prediction.squad_impact.isNullOrEmpty()
        binding.layoutMatchPrediction.layoutSquadImpact.isVisible = hasSquadImpact
        if (hasSquadImpact) {
            binding.layoutMatchPrediction.tvSquadImpactText.text = prediction.squad_impact
        }

        // 6. Tactical Analysis & Strengths/Weaknesses
        binding.layoutMatchPrediction.tvTacticalAnalysisText.text = prediction.tactical_analysis ?: ""
        
        binding.layoutMatchPrediction.tvTacticalAnalysisText.maxLines = Integer.MAX_VALUE
        binding.layoutMatchPrediction.tvReadMoreTactics.visibility = View.GONE

        // Populate Strengths & Weaknesses side-by-side
        binding.layoutMatchPrediction.tvHomeTeamSWLabel.text = homeTeamName.uppercase()
        binding.layoutMatchPrediction.tvAwayTeamSWLabel.text = awayTeamName.uppercase()

        binding.layoutMatchPrediction.layoutHomeSWContainer.removeAllViews()
        binding.layoutMatchPrediction.layoutAwaySWContainer.removeAllViews()

        val sw = prediction.strengths_weaknesses
        if (sw != null) {
            fun addBulletPoints(container: android.widget.LinearLayout, items: List<String>?, isStrength: Boolean) {
                if (items.isNullOrEmpty()) return
                for (item in items) {
                    val tv = android.widget.TextView(this).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 8.dpToPx())
                        }
                        text = item
                        setTextColor(ContextCompat.getColor(this@MatchDetailActivity, R.color.text_white))
                        textSize = 12.5f
                        setLineSpacing(0f, 1.2f)
                        
                        val iconRes = if (isStrength) R.drawable.ic_check_circle_filled else R.drawable.ic_close_circle
                        val drawable = ContextCompat.getDrawable(this@MatchDetailActivity, iconRes)?.apply {
                            setBounds(0, 0, 16.dpToPx(), 0)
                        }
                        setCompoundDrawables(drawable, null, null, null)
                        compoundDrawablePadding = 8.dpToPx()
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    container.addView(tv)
                }
            }

            addBulletPoints(binding.layoutMatchPrediction.layoutHomeSWContainer, sw.home_strengths, true)
            addBulletPoints(binding.layoutMatchPrediction.layoutHomeSWContainer, sw.home_weaknesses, false)
            addBulletPoints(binding.layoutMatchPrediction.layoutAwaySWContainer, sw.away_strengths, true)
            addBulletPoints(binding.layoutMatchPrediction.layoutAwaySWContainer, sw.away_weaknesses, false)
        }

        // 7. Key Stats, Corners & Cards
        val cornersHome = prediction.corners_prediction?.home ?: 0
        val cornersAway = prediction.corners_prediction?.away ?: 0
        binding.layoutMatchPrediction.tvPredictCorners.text = "$cornersHome - $cornersAway"

        val cardsHome = prediction.yellow_cards_prediction?.home ?: 0
        val cardsAway = prediction.yellow_cards_prediction?.away ?: 0
        binding.layoutMatchPrediction.tvPredictYellowCards.text = "$cardsHome - $cardsAway"

        // Key Stats bullets with ball icon emoji
        binding.layoutMatchPrediction.layoutKeyStatsContainer.removeAllViews()
        val keyStats = prediction.key_stats ?: emptyList()
        for (stat in keyStats) {
            val tv = android.widget.TextView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dpToPx())
                }
                text = "⚽  $stat"
                setTextColor(ContextCompat.getColor(this@MatchDetailActivity, R.color.text_white))
                textSize = 12f
                setLineSpacing(0f, 1.15f)
            }
            binding.layoutMatchPrediction.layoutKeyStatsContainer.addView(tv)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun loadNativeAd() {
        if (limitManager.isPremium()) {
            binding.frAdsDetail.visibility = View.GONE
            return
        }

        val adId = try {
            RemoteConfigManager.getInstance().getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        if (adId.isNotEmpty()) {
            binding.frAdsDetail.visibility = View.VISIBLE
            // Inflate and show shimmer layout while loading
            val shimmerView = LayoutInflater.from(this).inflate(R.layout.layout_shimmer_league, binding.frAdsDetail, false)
            binding.frAdsDetail.removeAllViews()
            binding.frAdsDetail.addView(shimmerView)

            Admob.getInstance().loadNativeAds(
                this,
                adId,
                1,
                object : NativeCallback() {
                    override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                        super.onNativeAdLoaded(nativeAd)

                        val adView = LayoutInflater.from(this@MatchDetailActivity)
                            .inflate(R.layout.layout_native_league, null) as NativeAdView
                        
                        binding.frAdsDetail.removeAllViews()
                        binding.frAdsDetail.addView(adView)
                        
                        Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    }

                    override fun onAdFailedToLoad() {
                        super.onAdFailedToLoad()
                        binding.frAdsDetail.visibility = View.GONE
                    }
                }
            )
        } else {
            binding.frAdsDetail.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopDetailPolling()
    }
}
