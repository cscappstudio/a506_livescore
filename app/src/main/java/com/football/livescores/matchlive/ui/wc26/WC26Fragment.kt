package com.football.livescores.matchlive.ui.wc26

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.repository.LeaguesRepository
import com.football.livescores.matchlive.databinding.FragmentWc26Binding
import com.football.livescores.matchlive.utils.bindScrollableChild
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.local.MatchReminderManager
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.ui.home.MatchFilter
import com.football.livescores.matchlive.utils.NativeCallback
import com.football.livescores.matchlive.utils.Admob
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.graphics.Rect
import android.view.ViewTreeObserver
import com.football.livescores.matchlive.utils.SharePreferenceUtils

@AndroidEntryPoint
class WC26Fragment : Fragment() {

    private var _binding: FragmentWc26Binding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var leaguesRepository: LeaguesRepository

    @Inject
    lateinit var favoriteManager: FavoriteManager

    @Inject
    lateinit var reminderManager: MatchReminderManager

    @Inject
    lateinit var limitManager: RequestLimitManager

    private var selectedTab = 1
    private var isWcBracketFullScreen = false
    private var selectedFixtureFilter = MatchFilter.LIVE
    private var cachedFixturesList = emptyList<com.football.livescores.matchlive.data.remote.model.MatchItemDto>()
    private lateinit var wcFixtureAdapter: WcFixtureAdapter
    private val BRACKET_CARD_HEIGHT = 150f
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWc26Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCountdown()
        setupListeners()
        setupFixtureFilters()
        setupRecyclerView()
        binding.swipeRefreshLayout.apply {
            bindScrollableChild { activeWcScrollView() }
            setOnRefreshListener { populateWcTournamentData() }
        }
        updateTabUI()
        setupAdScrollListeners()
        populateWcTournamentData()
        if(!SharePreferenceUtils.isOrganic(context)) {
            loadNativeAd()
        } else {
            binding.frAdsWc.removeAllViews()
            binding.frAdsWc.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        wcFixtureAdapter = WcFixtureAdapter(
            context = requireContext(),
            onMatchClick = { match, selectPredictionTab ->
                val intent = android.content.Intent(requireContext(), com.football.livescores.matchlive.ui.detail.MatchDetailActivity::class.java).apply {
                    putExtra("MATCH_ID", match.fixture.id)
                    putExtra("HOME_TEAM", match.teams.home.name)
                    putExtra("AWAY_TEAM", match.teams.away.name)
                    putExtra("SELECT_PREDICTION_TAB", selectPredictionTab)
                }
                startActivity(intent)
            },
            onReminderClick = { match, ivReminder ->
                val matchEntity = CachedMatchEntity(
                    id = match.fixture.id,
                    leagueId = match.league.id,
                    leagueName = match.league.name,
                    leagueLogo = match.league.logo,
                    homeTeamId = match.teams.home.id,
                    homeTeamName = match.teams.home.name,
                    homeTeamLogo = match.teams.home.logo,
                    awayTeamId = match.teams.away.id,
                    awayTeamName = match.teams.away.name,
                    awayTeamLogo = match.teams.away.logo,
                    statusShort = match.fixture.status.short,
                    elapsed = match.fixture.status.elapsed,
                    goalsHome = match.goals.home,
                    goalsAway = match.goals.away,
                    dateTimestamp = match.fixture.timestamp,
                    statusLong = match.fixture.status.long,
                    queryDate = ""
                )
                val newRemind = reminderManager.toggleReminder(matchEntity)
                ivReminder.setImageResource(if (newRemind) R.drawable.ic_bell_active else R.drawable.ic_bell)
                ivReminder.setColorFilter(ContextCompat.getColor(requireContext(), if (newRemind) R.color.accent_green else R.color.text_muted))
                val msg = if (newRemind) getString(R.string.reminder_set_toast) else getString(R.string.reminder_cancelled_toast)
                android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
            },
            onFavoriteClick = { match, ivFavorite ->
                if (favoriteManager.canAddFavoriteFixture(match.fixture.id)) {
                    val newFav = favoriteManager.toggleFixtureFavorite(match.fixture.id)
                    ivFavorite.setImageResource(if (newFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
                    ivFavorite.setColorFilter(ContextCompat.getColor(requireContext(), if (newFav) R.color.primaryRed else R.color.text_muted))
                } else {
                    val paywall = com.football.livescores.matchlive.ui.custom.PremiumPaywallDialog.newInstance()
                    paywall.show(parentFragmentManager, com.football.livescores.matchlive.ui.custom.PremiumPaywallDialog.TAG)
                }
            },
            isReminderSet = { reminderManager.isReminderSet(it) },
            isFavoriteSet = { favoriteManager.isFixtureFavorite(it) },
            onAdViewReady = { adViewWrapper, adId ->
                val existing = pendingAdLoads.find { it.adViewWrapper == adViewWrapper }
                if (existing == null) {
                    val pending = PendingAdLoad(adViewWrapper, adId)
                    pendingAdLoads.add(pending)
                    triggerAdLoad(pending)
                }
            }
        )
        binding.rvWcFixtures.adapter = wcFixtureAdapter
    }

    private fun activeWcScrollView(): View? = when (selectedTab) {
        0 -> binding.rvWcFixtures
        1 -> binding.scrollWcGroups
        2 -> binding.scrollWcBracketVertical
        else -> null
    }

    private fun populateWcTournamentData() {
        pendingAdLoads.clear()
        viewLifecycleOwner.lifecycleScope.launch {
            val isRefreshing = binding.swipeRefreshLayout.isRefreshing
            try {
                if (!isRefreshing) {
                    binding.layoutLoadingOverlay.isVisible = true
                    binding.btnWcZoom.isVisible = false
                    binding.layoutWcFixturesPanel.isVisible = false
                    binding.scrollWcGroups.isVisible = false
                    binding.scrollWcBracket.isVisible = false
                }

                val inflater = LayoutInflater.from(requireContext())

                // 1. Fetch and Populate Standings (Groups / BXH)
                var standingsList = emptyList<com.football.livescores.matchlive.data.remote.model.StandingRowDto>()
                
                try {
                    leaguesRepository.getStandings(1, 2026)
                        .collect { list ->
                            standingsList = list
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (standingsList.isNotEmpty()) {
                    binding.layoutWcGroupsContainer.removeAllViews()
                    
                    val grouped = standingsList.groupBy { it.group }
                    val sortedGroups = grouped.entries.sortedBy { it.key }
                    
                    sortedGroups.forEachIndexed { index, entry ->
                        val groupName = entry.key
                        val rows = entry.value.sortedBy { it.rank }
                        
                        val groupView = inflater.inflate(R.layout.item_wc_group, binding.layoutWcGroupsContainer, false)
                        
                        // Keep group name in English: e.g. "GROUP A"
                        val displayGroupName = groupName?.replace("Group", "GROUP")?.uppercase()
                        groupView.findViewById<android.widget.TextView>(R.id.tvGroupName).text = displayGroupName
                        
                        val rowsContainer = groupView.findViewById<android.widget.LinearLayout>(R.id.layoutTeamRowsContainer)
                        rowsContainer.removeAllViews()
                        
                        rows.forEach { row ->
                            val rowView = inflater.inflate(R.layout.item_wc_group_row, rowsContainer, false)
                            
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowRank).text = row.rank.toString()
                            
                            val ivFlag = rowView.findViewById<android.widget.ImageView>(R.id.ivRowFlag)
                            Glide.with(rowView.context)
                                .load(row.team.logo)
                                .placeholder(R.drawable.ic_favorite_border)
                                .into(ivFlag)
                                
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowName).text = row.team.name
                            
                            // Stats columns
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowPlayed).text = (row.all?.played ?: 0).toString()
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowWon).text = (row.all?.win ?: 0).toString()
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowDrawn).text = (row.all?.draw ?: 0).toString()
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowLost).text = (row.all?.lose ?: 0).toString()
                            
                            // GD
                            val gd = row.goalsDiff ?: 0
                            val gdText = if (gd > 0) "+$gd" else gd.toString()
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowGD).text = gdText
                            
                            // PTS
                            rowView.findViewById<android.widget.TextView>(R.id.tvRowPTS).text = row.points.toString()
                            
                            rowsContainer.addView(rowView)
                        }
                        
                        binding.layoutWcGroupsContainer.addView(groupView)
                    }
                } else {
                    binding.layoutWcGroupsContainer.removeAllViews()
                    val emptyStateView = com.football.livescores.matchlive.ui.custom.EmptyStateView(requireContext()).apply {
                        id = R.id.emptyState
                        text = getString(R.string.empty_fixtures)
                        setPadding(0, dpToPx(32f), 0, 0)
                        visibility = if (binding.swipeRefreshLayout.isRefreshing || binding.layoutLoadingOverlay.isVisible) View.GONE else View.VISIBLE
                    }
                    binding.layoutWcGroupsContainer.addView(emptyStateView)
                }

                // 2. Fetch and Populate Fixtures & Bracket (Trận đấu & VLTT)
                var fixturesList = emptyList<com.football.livescores.matchlive.data.remote.model.MatchItemDto>()
                var bracketList = emptyList<com.football.livescores.matchlive.data.remote.model.MatchItemDto>()
                try {
                    leaguesRepository.getFixturesByLeague(1, 2026)
                        .collect { list ->
                            fixturesList = list
                        }
                    
                    leaguesRepository.getWcBracket(1, 2026)
                        .collect { list ->
                            bracketList = list
                        }
                    
                    // Call API /fixtures/rounds and log the output
                    leaguesRepository.getFixturesRounds(1, 2026)
                        .collect { list ->
                            android.util.Log.d("WC26Fragment", "API /fixtures/rounds returned rounds: $list")
                        }

                    // Call API /fixtures with round=Round of 32 and log the output
                    leaguesRepository.getFixturesByLeague(1, 2026, "Round of 32")
                        .collect { list ->
                            android.util.Log.d("WC26Fragment", "API /fixtures?round=Round of 32 returned matches count: ${list.size}")
                            list.forEach { match ->
                                android.util.Log.d("WC26Fragment", "  Match: ${match.teams.home.name} vs ${match.teams.away.name}, round: ${match.league.round}")
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                cachedFixturesList = fixturesList
                renderWcFixtures(selectedFixtureFilter)

                // 3. Populate Bracket (VLTT) View
                // The World Cup 2026 knockout bracket starts with the Round of 32
                val bracketRounds = listOf("Round of 32", "Round of 16", "Quarter-finals", "Semi-finals", "Final")

                // Group our fetched bracketList by round name (lowercase keys)
                val matchesByRound = bracketList.groupBy { (it.league.round ?: "").lowercase() }

                // Render the dynamic columns and connectors starting from the Round of 32
                renderDynamicBracket(bracketRounds, matchesByRound)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (_binding != null) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.layoutLoadingOverlay.isVisible = false
                    
                    val emptyView = binding.layoutWcGroupsContainer.findViewById<View>(R.id.emptyState)
                    emptyView?.visibility = View.VISIBLE
                    
                    renderWcFixtures(selectedFixtureFilter)
                    
                    updateTabUI()
                }
            }
        }
    }

    private fun renderDynamicBracket(
        sortedRounds: List<String>, 
        matchesByRound: Map<String, List<com.football.livescores.matchlive.data.remote.model.MatchItemDto>>
    ) {
        binding.layoutWcBracketContainer.removeAllViews()
        
        val H = BRACKET_CARD_HEIGHT // Card height in dp
        val colWidth = 210f // Column width in dp
        val connWidth = 24f // Connector width in dp

        val numColumns = sortedRounds.size
        if (numColumns == 0) {
            return
        }
        
        for (c in 0 until numColumns) {
            val roundName = sortedRounds[c]
            val matches = matchesByRound[roundName.lowercase()] ?: emptyList()
            
            // Create and add match column
            val colLayout = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(colWidth), ViewGroup.LayoutParams.MATCH_PARENT)
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.TOP
            }
            
            // Number of matches expected in this column: 2^(numColumns - 1 - c)
            val expectedMatchCount = 1 shl (numColumns - 1 - c)
            
            // Spacers calculations
            val factor = (1 shl c).toFloat()
            val initialSpacerDp = (H / 2f + 8f) * (factor - 1f)
            val matchSpacerDp = factor * (H + 16f) - H

            // Add initial spacer
            if (initialSpacerDp > 0) {
                addSpacer(colLayout, initialSpacerDp)
            }
            
            // Add matches (determined matches first, sorted by timestamp; placeholders at the bottom)
            val sortedMatches = matches.sortedWith(compareBy<com.football.livescores.matchlive.data.remote.model.MatchItemDto> { 
                val isPlaceholder = it.fixture.date.isNullOrEmpty() || 
                    (it.teams.home.name.equals("TBD", ignoreCase = true) && it.teams.away.name.equals("TBD", ignoreCase = true))
                if (isPlaceholder) 1 else 0
            }.thenBy { 
                it.fixture.timestamp 
            })
            
            val defaultDate = "---,---"
 
            for (i in 0 until expectedMatchCount) {
                if (i > 0) {
                    addSpacer(colLayout, matchSpacerDp)
                }
                val match = sortedMatches.getOrNull(i)
                addBracketMatch(colLayout, match, defaultDate)
            }
            
            binding.layoutWcBracketContainer.addView(colLayout)
            
            // Create and add connector column (except for the last column)
            if (c < numColumns - 1) {
                val connLayout = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(connWidth), ViewGroup.LayoutParams.MATCH_PARENT)
                    orientation = LinearLayout.VERTICAL
                }
                
                val connInitialSpacerDp = (H / 2f) + (H / 2f + 8f) * (factor - 1f)
                val connHeightDp = factor * (H + 16f)
                val connSpacerDp = factor * (H + 16f)
                val expectedConnCount = expectedMatchCount / 2
                
                // Add initial spacer
                if (connInitialSpacerDp > 0) {
                    addSpacer(connLayout, connInitialSpacerDp)
                }
                
                // Add connectors
                for (i in 0 until expectedConnCount) {
                    if (i > 0) {
                        addSpacer(connLayout, connSpacerDp)
                    }
                    addConnector(connLayout, connHeightDp)
                }
                
                binding.layoutWcBracketContainer.addView(connLayout)
            }
        }
    }

    private fun addBracketMatch(column: ViewGroup, match: com.football.livescores.matchlive.data.remote.model.MatchItemDto?, defaultDate: String) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_wc_bracket_match, column, false)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(BRACKET_CARD_HEIGHT)
        )
        bindBracketMatch(view, match, defaultDate)
        column.addView(view)
    }

    private fun addSpacer(column: ViewGroup, heightDp: Float) {
        val spacer = View(requireContext())
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(heightDp)
        )
        spacer.layoutParams = params
        column.addView(spacer)
    }

    private fun addConnector(column: ViewGroup, heightDp: Float) {
        val view = com.football.livescores.matchlive.ui.custom.BracketConnectorView(requireContext())
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(heightDp)
        )
        view.layoutParams = params
        column.addView(view)
    }

    private fun bindBracketMatch(
        view: View, 
        match: com.football.livescores.matchlive.data.remote.model.MatchItemDto?,
        defaultDate: String
    ) {
        val tvDate = view.findViewById<android.widget.TextView>(R.id.tvMatchDate)
        val ivLogo1 = view.findViewById<android.widget.ImageView>(R.id.ivTeam1Logo)
        val tvName1 = view.findViewById<android.widget.TextView>(R.id.tvTeam1Name)
        val tvScore1 = view.findViewById<android.widget.TextView>(R.id.tvTeam1Score)
        val ivLogo2 = view.findViewById<android.widget.ImageView>(R.id.ivTeam2Logo)
        val tvName2 = view.findViewById<android.widget.TextView>(R.id.tvTeam2Name)
        val tvScore2 = view.findViewById<android.widget.TextView>(R.id.tvTeam2Score)

        if (match != null) {
            val rawDate = match.fixture.date
            val isUndetermined = rawDate.isNullOrEmpty() || rawDate.equals("null", ignoreCase = true) ||
                (match.teams.home.name.equals("TBD", ignoreCase = true) && match.teams.away.name.equals("TBD", ignoreCase = true))
            
            val displayDate = if (isUndetermined) {
                "---,---"
            } else {
                try {
                    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    val date = parser.parse(rawDate)
                    val formatter = java.text.SimpleDateFormat("dd/MM, HH:mm", java.util.Locale.getDefault()).apply {
                        timeZone = java.util.TimeZone.getDefault()
                    }
                    if (date != null) formatter.format(date) else "---,---"
                } catch (e: Exception) {
                    "---,---"
                }
            }
            tvDate.text = displayDate

            tvName1.text = match.teams.home.name
            Glide.with(view.context)
                .load(match.teams.home.logo)
                .placeholder(R.drawable.ic_favorite_border)
                .into(ivLogo1)
            ivLogo1.imageTintList = null

            tvName2.text = match.teams.away.name
            Glide.with(view.context)
                .load(match.teams.away.logo)
                .placeholder(R.drawable.ic_favorite_border)
                .into(ivLogo2)
            ivLogo2.imageTintList = null

            val homeGoal = match.goals.home
            val awayGoal = match.goals.away
            if (homeGoal != null && awayGoal != null) {
                tvScore1.text = homeGoal.toString()
                tvScore2.text = awayGoal.toString()
                tvScore1.visibility = View.VISIBLE
                tvScore2.visibility = View.VISIBLE
            } else {
                tvScore1.visibility = View.GONE
                tvScore2.visibility = View.GONE
            }

            view.setOnClickListener {
                val intent = android.content.Intent(view.context, com.football.livescores.matchlive.ui.detail.MatchDetailActivity::class.java).apply {
                    putExtra("MATCH_ID", match.fixture.id)
                    putExtra("HOME_TEAM", match.teams.home.name)
                    putExtra("AWAY_TEAM", match.teams.away.name)
                }
                view.context.startActivity(intent)
            }
        } else {
            view.setOnClickListener(null)
            tvDate.text = defaultDate
            tvName1.text = getString(R.string.wc_tbd)
            tvName2.text = getString(R.string.wc_tbd)
            ivLogo1.setImageResource(R.drawable.ic_favorite_border)
            ivLogo1.imageTintList = ContextCompat.getColorStateList(view.context, R.color.text_muted)
            ivLogo2.setImageResource(R.drawable.ic_favorite_border)
            ivLogo2.imageTintList = ContextCompat.getColorStateList(view.context, R.color.text_muted)
            tvScore1.visibility = View.GONE
            tvScore2.visibility = View.GONE
        }
    }

    private fun setupCountdown() {
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 11)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffMs = targetCal.timeInMillis - System.currentTimeMillis()
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        
        val countdownText = if (diffDays > 0) {
            getString(R.string.wc26_days_until, diffDays)
        } else if (diffDays == 0L) {
            getString(R.string.wc26_starts_today)
        } else {
            getString(R.string.wc26_ongoing)
        }
    }

    private fun setupListeners() {
        binding.tabWcFixtures.setOnClickListener {
            selectedTab = 0
            isWcBracketFullScreen = false
            binding.wcTabsContainer.isVisible = true
            binding.btnWcZoom.setIconResource(R.drawable.ic_zoom_in)
            updateTabUI()
        }
        binding.tabWcGroups.setOnClickListener {
            selectedTab = 1
            isWcBracketFullScreen = false
            binding.wcTabsContainer.isVisible = true
            binding.btnWcZoom.setIconResource(R.drawable.ic_zoom_in)
            updateTabUI()
        }
        binding.tabWcVLTT.setOnClickListener {
            selectedTab = 2
            updateTabUI()
        }
        binding.btnWcZoom.setOnClickListener {
            isWcBracketFullScreen = !isWcBracketFullScreen
            binding.wcTabsContainer.isVisible = !isWcBracketFullScreen
            binding.btnWcZoom.setIconResource(
                if (isWcBracketFullScreen) R.drawable.ic_zoom_out else R.drawable.ic_zoom_in
            )
        }
    }

    private fun setupFixtureFilters() {
        updateFixtureFilterUI(selectedFixtureFilter)
        binding.btnWcLive.setOnClickListener { applyFixtureFilter(MatchFilter.LIVE) }
        binding.btnWcUpcoming.setOnClickListener { applyFixtureFilter(MatchFilter.UPCOMING) }
        binding.btnWcFinished.setOnClickListener { applyFixtureFilter(MatchFilter.FINISHED) }
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    private fun applyFixtureFilter(filter: MatchFilter) {
        selectedFixtureFilter = filter
        updateFixtureFilterUI(filter)
        
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.layoutLoadingOverlay.isVisible = true
            kotlinx.coroutines.delay(150) // Small delay to allow loading animation to show
            
            pendingAdLoads.removeAll { it.adViewWrapper.parent == null }
            renderWcFixtures(filter)
            binding.rvWcFixtures.scrollToPosition(0)
            checkPendingAds()
            
            binding.layoutLoadingOverlay.isVisible = false
        }
    }

        private fun updateFixtureFilterUI(selectedFilter: MatchFilter) {
        val ctx = requireContext()
        val buttons = listOf(
            Triple(binding.btnWcFinished, MatchFilter.FINISHED, R.drawable.ic_tab_finished),
            Triple(binding.btnWcLive, MatchFilter.LIVE, R.drawable.ic_tab_live),
            Triple(binding.btnWcUpcoming, MatchFilter.UPCOMING, R.drawable.ic_tab_upcoming)
        )

        buttons.forEach { (btn, filter, _) ->
            val isSelected = (selectedFilter == filter)
            btn.isSelected = isSelected
            btn.strokeWidth = 0
            if (isSelected) {
                btn.setBackgroundResource(R.drawable.bg_tab_filter_selected)
                btn.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                btn.iconTint = ContextCompat.getColorStateList(ctx, R.color.white)
            } else {
                btn.setBackgroundResource(R.drawable.bg_tab_filter_unselected)
                btn.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                btn.iconTint = ContextCompat.getColorStateList(ctx, R.color.white)
            }
        }
    }

    private fun filterFixtures(
        fixtures: List<com.football.livescores.matchlive.data.remote.model.MatchItemDto>,
        filter: MatchFilter
    ): List<com.football.livescores.matchlive.data.remote.model.MatchItemDto> {
        val currentTime = System.currentTimeMillis()
        val cutoffDuration = 2 * 60 * 60 * 1000L
        return when (filter) {
            MatchFilter.LIVE -> fixtures.filter { isLiveStatus(it.fixture.status.short) }
            MatchFilter.UPCOMING -> fixtures.filter {
                val status = it.fixture.status.short
                (status == "NS" || status == "TBD" || status == "PST") &&
                    (it.fixture.timestamp * 1000 + cutoffDuration > currentTime)
            }
            MatchFilter.FINISHED -> fixtures.filter {
                val status = it.fixture.status.short
                status == "FT" || status == "AET" || status == "PEN" || status == "CANC" || status == "ABD" ||
                    ((status == "NS" || status == "TBD" || status == "PST") &&
                        (it.fixture.timestamp * 1000 + cutoffDuration <= currentTime))
            }
        }
    }

    private fun isLiveStatus(status: String): Boolean {
        return status in listOf("1H", "2H", "HT", "ET", "BT", "P", "INT", "LIVE")
    }

    private fun renderWcFixtures(filter: MatchFilter) {
        wcFixtureAdapter.isUpcomingTab = (filter == MatchFilter.UPCOMING)
        val filteredFixtures = filterFixtures(cachedFixturesList, filter)
        val newItems = mutableListOf<WcFixtureItem>()

        if (filteredFixtures.isEmpty()) {
            if (_binding != null && !binding.swipeRefreshLayout.isRefreshing && !binding.layoutLoadingOverlay.isVisible) {
                newItems.add(WcFixtureItem.EmptyItem)
            }
            wcFixtureAdapter.submitList(newItems)
            return
        }

        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val localDateKeyFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getDefault()
        }

        val groupedMatches = filteredFixtures.groupBy { match ->
            try {
                val dateObj = parser.parse(match.fixture.date)
                if (dateObj != null) localDateKeyFormat.format(dateObj) else "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        }.entries.sortedBy { it.key }

        val locale = java.util.Locale.getDefault()
        val headerDateFormat = if (locale.language == "vi") {
            java.text.SimpleDateFormat("'Ngày' dd 'tháng' MM 'năm' yyyy", locale)
        } else {
            java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", locale)
        }.apply {
            timeZone = java.util.TimeZone.getDefault()
        }

        groupedMatches.forEach { entry ->
            val dateKeyStr = entry.key
            val matchesForDate = entry.value

            if (dateKeyStr != "Unknown") {
                val headerDateObj = localDateKeyFormat.parse(dateKeyStr)
                if (headerDateObj != null) {
                    newItems.add(WcFixtureItem.HeaderItem(headerDateFormat.format(headerDateObj)))
                }
            }

            matchesForDate.forEach { match ->
                newItems.add(WcFixtureItem.MatchItem(match))
            }
        }
        wcFixtureAdapter.submitList(newItems)
    }

    private fun updateTabUI() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_green)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_muted)
        val transparent = android.graphics.Color.TRANSPARENT
        
        // Reset all to inactive
        binding.tvWcFixtures.setTextColor(inactiveColor)
        binding.indicatorWcFixtures.setBackgroundColor(transparent)
        
        binding.tvWcGroups.setTextColor(inactiveColor)
        binding.indicatorWcGroups.setBackgroundColor(transparent)
        
        binding.tvWcVLTT.setTextColor(inactiveColor)
        binding.indicatorWcVLTT.setBackgroundColor(transparent)

        binding.layoutWcFixturesPanel.isVisible = false
        binding.scrollWcGroups.isVisible = false
        binding.scrollWcBracket.isVisible = false
        binding.btnWcZoom.isVisible = (selectedTab == 2)

        when (selectedTab) {
            0 -> {
                binding.tvWcFixtures.setTextColor(activeColor)
                binding.indicatorWcFixtures.setBackgroundColor(activeColor)
                binding.layoutWcFixturesPanel.isVisible = true
            }
            1 -> {
                binding.tvWcGroups.setTextColor(activeColor)
                binding.indicatorWcGroups.setBackgroundColor(activeColor)
                binding.scrollWcGroups.isVisible = true
            }
            2 -> {
                binding.tvWcVLTT.setTextColor(activeColor)
                binding.indicatorWcVLTT.setBackgroundColor(activeColor)
                binding.scrollWcBracket.isVisible = true
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private val pendingAdLoads = mutableListOf<PendingAdLoad>()
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private data class PendingAdLoad(
        val adViewWrapper: View,
        val adId: String
    )

    private fun setupAdScrollListeners() {
        scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
            checkPendingAds()
        }
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            checkPendingAds()
        }
        
        binding.rvWcFixtures.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        binding.rvWcFixtures.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        binding.scrollWcGroups.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        binding.scrollWcGroups.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun checkPendingAds() {
        if (pendingAdLoads.isEmpty()) return
        val iterator = pendingAdLoads.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (isViewNearScreen(pending.adViewWrapper)) {
                iterator.remove()
                triggerAdLoad(pending)
            }
        }
    }

    private fun isViewNearScreen(view: View): Boolean {
        if (!view.isShown) return false
        if (view.width == 0 || view.height == 0) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewTop = location[1]
        val screenHeight = resources.displayMetrics.heightPixels
        return viewTop < screenHeight + 500 && viewTop + view.height > -500
    }

    private fun triggerAdLoad(pending: PendingAdLoad) {
        val context = context ?: return

        Admob.getInstance().loadNativeAds(
            context,
            pending.adId,
            1,
            object : NativeCallback() {
                override fun onNativeAdLoaded(nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd?) {
                    super.onNativeAdLoaded(nativeAd)
                    if (!isAdded) return

                    pending.adViewWrapper.visibility = View.VISIBLE
                    Admob.getInstance().pushAdsToViewCustom(
                        nativeAd,
                        pending.adViewWrapper as NativeAdView
                    )
                }

                override fun onAdFailedToLoad() {
                    super.onAdFailedToLoad()
                    if (!isAdded) return
                    pending.adViewWrapper.visibility = View.GONE
                }
            }
        )
    }

    private fun loadNativeAd() {
        if (limitManager.isPremium()) {
            binding.frAdsWc.visibility = View.GONE
            return
        }

        val adId = try {
            RemoteConfigManager.getInstance().getAdId("native_all", getString(R.string.native_all))
        } catch (e: Exception) {
            getString(R.string.native_all)
        }

        if (adId.isNotEmpty()) {
            binding.frAdsWc.visibility = View.VISIBLE
            // Inflate and show shimmer layout while loading
            val shimmerView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_shimmer_no_media, binding.frAdsWc, false)
            binding.frAdsWc.removeAllViews()
            binding.frAdsWc.addView(shimmerView)

            Admob.getInstance().loadNativeAds(
                requireContext(),
                adId,
                1,
                object : NativeCallback() {
                    override fun onNativeAdLoaded(nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd?) {
                        super.onNativeAdLoaded(nativeAd)
                        if (!isAdded) return

                        val adView = LayoutInflater.from(requireContext())
                            .inflate(R.layout.layout_native_no_media, null) as NativeAdView
                        
                        binding.frAdsWc.removeAllViews()
                        binding.frAdsWc.addView(adView)
                        
                        Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    }

                    override fun onAdFailedToLoad() {
                        super.onAdFailedToLoad()
                        if (!isAdded) return
                        binding.frAdsWc.visibility = View.GONE
                    }
                }
            )
        } else {
            binding.frAdsWc.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollChangedListener?.let {
            _binding?.rvWcFixtures?.viewTreeObserver?.removeOnScrollChangedListener(it)
            _binding?.scrollWcGroups?.viewTreeObserver?.removeOnScrollChangedListener(it)
        }
        layoutListener?.let {
            _binding?.rvWcFixtures?.viewTreeObserver?.removeOnGlobalLayoutListener(it)
            _binding?.scrollWcGroups?.viewTreeObserver?.removeOnGlobalLayoutListener(it)
        }
        scrollChangedListener = null
        layoutListener = null
        pendingAdLoads.clear()
        _binding = null
    }
}
