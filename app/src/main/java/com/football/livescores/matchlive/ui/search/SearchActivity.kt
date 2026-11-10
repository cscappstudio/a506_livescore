package com.football.livescores.matchlive.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import com.football.livescores.matchlive.base.BaseActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.football.livescores.matchlive.R
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.repository.MatchRepository
import com.football.livescores.matchlive.databinding.ActivitySearchBinding
import com.football.livescores.matchlive.ui.detail.MatchDetailActivity
import com.football.livescores.matchlive.utils.AdsConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding

    @Inject
    lateinit var favoriteManager: FavoriteManager

    @Inject
    lateinit var matchRepository: MatchRepository

    private lateinit var resultsAdapter: SearchResultsAdapter

    private val searchQuery = MutableStateFlow("")
    private val allMatches = MutableStateFlow<List<CachedMatchEntity>>(emptyList())
    private val favoriteMatchIds = MutableStateFlow<Set<String>>(emptySet())
    private val favoriteTeamIds = MutableStateFlow<Set<Int>>(emptySet())
    private val favoriteLeagueIds = MutableStateFlow<Set<Int>>(emptySet())

    // Defined static high-quality clubs list for autocomplete matching
    private val standardClubs = listOf(
        Pair(42, Pair("Arsenal", "https://media.api-sports.io/football/teams/42.png")),
        Pair(49, Pair("Chelsea", "https://media.api-sports.io/football/teams/49.png")),
        Pair(541, Pair("Real Madrid", "https://media.api-sports.io/football/teams/541.png")),
        Pair(529, Pair("Barcelona", "https://media.api-sports.io/football/teams/529.png")),
        Pair(33, Pair("Manchester United", "https://media.api-sports.io/football/teams/33.png")),
        Pair(47, Pair("Tottenham", "https://media.api-sports.io/football/teams/47.png")),
        Pair(40, Pair("Liverpool", "https://media.api-sports.io/football/teams/40.png")),
        Pair(66, Pair("Aston Villa", "https://media.api-sports.io/football/teams/66.png")),
        Pair(157, Pair("Bayern Munich", "https://media.api-sports.io/football/teams/157.png")),
        Pair(165, Pair("Dortmund", "https://media.api-sports.io/football/teams/165.png")),
        Pair(1111, Pair("Vietnam 🇻🇳", "https://media.api-sports.io/football/teams/1111.png")),
        Pair(85, Pair("Paris Saint Germain", "https://media.api-sports.io/football/teams/85.png")),
        Pair(50, Pair("Man City", "https://media.api-sports.io/football/teams/50.png"))
    )

    // Defined static high-quality leagues list for autocomplete matching
    private val standardLeagues = listOf(
        Triple(39, "Premier League", Pair("https://media.api-sports.io/football/leagues/39.png", "England")),
        Triple(140, "La Liga", Pair("https://media.api-sports.io/football/leagues/140.png", "Spain")),
        Triple(135, "Serie A", Pair("https://media.api-sports.io/football/leagues/135.png", "Italy")),
        Triple(78, "Bundesliga", Pair("https://media.api-sports.io/football/leagues/78.png", "Germany")),
        Triple(61, "Ligue 1", Pair("https://media.api-sports.io/football/leagues/61.png", "France")),
        Triple(2, "UEFA Champions League", Pair("https://media.api-sports.io/football/leagues/2.png", "Europe")),
        Triple(1, "World Cup 2026", Pair("https://media.api-sports.io/football/leagues/1.png", "World"))
    )

    override fun bind() {
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize favorite match IDs
        favoriteMatchIds.value = favoriteManager.getFavoriteFixtureIds()

        // Setup onBackPressed frequency capped interstitial ad (35s)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsConfig.showInterClickAd(this@SearchActivity) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setupUI()
        setupRecyclerView()
        observeData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                searchQuery.value = query
                binding.btnClear.isVisible = query.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = SearchResultsAdapter(
            onMatchClick = { match ->
                val intent = Intent(this, MatchDetailActivity::class.java).apply {
                    putExtra("MATCH_ID", match.id)
                    putExtra("HOME_TEAM", match.homeTeamName)
                    putExtra("AWAY_TEAM", match.awayTeamName)
                }
                startActivity(intent)
            },
            onMatchFavToggle = { match ->
                favoriteManager.toggleFixtureFavorite(match.id)
                favoriteMatchIds.value = favoriteManager.getFavoriteFixtureIds()
            },
            onTeamFavToggle = { id, name, logo ->
                lifecycleScope.launch {
                    favoriteManager.toggleTeamFavorite(id, name, logo)
                }
            },
            onLeagueFavToggle = { id, name, logo, country ->
                lifecycleScope.launch {
                    favoriteManager.toggleLeagueFavorite(id, name, logo, country)
                }
            }
        )

        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = resultsAdapter
    }

    @OptIn(FlowPreview::class)
    private fun observeData() {
        // Collect Matches from database
        lifecycleScope.launch {
            matchRepository.getRecentCachedMatches(300).collect { matches ->
                allMatches.value = matches
            }
        }

        // Collect Real-time favorite states in parallel coroutines to avoid block suspending flow collections
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoriteManager.getAllFavoriteTeams().collect { list ->
                    favoriteTeamIds.value = list.map { it.id }.toSet()
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoriteManager.getAllFavoriteLeagues().collect { list ->
                    favoriteLeagueIds.value = list.map { it.id }.toSet()
                }
            }
        }

        // Combine search query (debounced), matches, and favorite states to produce SearchResultItems
        lifecycleScope.launch {
            combine(
                searchQuery.debounce(250),
                allMatches,
                favoriteMatchIds,
                favoriteTeamIds,
                favoriteLeagueIds
            ) { query, matches, favMatches, favTeams, favLeagues ->
                if (query.length < 2) {
                    return@combine emptyList<SearchResultItem>()
                }

                val list = mutableListOf<SearchResultItem>()

                // 1. Matches Search
                val matchedMatches = matches.filter {
                    it.homeTeamName.contains(query, ignoreCase = true) ||
                            it.awayTeamName.contains(query, ignoreCase = true) ||
                            it.leagueName.contains(query, ignoreCase = true)
                }
                if (matchedMatches.isNotEmpty()) {
                    list.add(SearchResultItem.Header(getString(R.string.search_header_matches)))
                    list.addAll(matchedMatches.map { SearchResultItem.Match(it, favMatches.contains(it.id.toString())) })
                }

                // 2. Teams Search
                val matchedTeams = standardClubs.filter { it.second.first.contains(query, ignoreCase = true) }
                if (matchedTeams.isNotEmpty()) {
                    list.add(SearchResultItem.Header(getString(R.string.search_header_teams)))
                    list.addAll(matchedTeams.map { SearchResultItem.Team(it.first, it.second.first, it.second.second, favTeams.contains(it.first)) })
                }

                // 3. Leagues Search
                val matchedLeagues = standardLeagues.filter { it.second.contains(query, ignoreCase = true) || it.third.second.contains(query, ignoreCase = true) }
                if (matchedLeagues.isNotEmpty()) {
                    list.add(SearchResultItem.Header(getString(R.string.search_header_leagues)))
                    list.addAll(matchedLeagues.map { SearchResultItem.League(it.first, it.second, it.third.first, it.third.second, favLeagues.contains(it.first)) })
                }

                list
            }.collect { results ->
                resultsAdapter.submitList(results)
                binding.emptyStateLayout.isVisible = results.isEmpty()
                binding.rvSearchResults.isVisible = results.isNotEmpty()

                // Custom text message adjustments using localized strings
                val currentQuery = searchQuery.value
                if (currentQuery.length >= 2 && results.isEmpty()) {
                    binding.tvEmptyTitle.text = getString(R.string.search_no_results)
                    binding.tvEmptyDescription.text = getString(R.string.search_no_results_desc, currentQuery)
                } else if (currentQuery.length < 2) {
                    binding.tvEmptyTitle.text = getString(R.string.search_empty_title)
                    binding.tvEmptyDescription.text = getString(R.string.search_empty_desc)
                }
            }
        }
    }
}
