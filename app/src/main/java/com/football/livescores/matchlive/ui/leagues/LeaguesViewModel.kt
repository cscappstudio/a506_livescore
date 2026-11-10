package com.football.livescores.matchlive.ui.leagues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.remote.model.StandingRowDto
import com.football.livescores.matchlive.data.remote.model.TopPlayerItemDto
import com.football.livescores.matchlive.data.repository.LeaguesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.football.livescores.matchlive.data.remote.model.MatchItemDto
import java.text.SimpleDateFormat
import java.util.Locale

sealed class StandingsUiState {
    object Loading : StandingsUiState()
    data class Success(val list: List<StandingRowDto>) : StandingsUiState()
    data class Error(val message: String) : StandingsUiState()
}

sealed class TopPlayersUiState {
    object Loading : TopPlayersUiState()
    data class Success(val list: List<TopPlayerItemDto>) : TopPlayersUiState()
    data class Error(val message: String) : TopPlayersUiState()
}

sealed class LeagueMatchesUiState {
    object Loading : LeagueMatchesUiState()
    data class Success(
        val items: List<LeagueMatchListItem>,
        val scrollToPosition: Int,
        val hasMore: Boolean = false
    ) : LeagueMatchesUiState()
    data class Error(val message: String) : LeagueMatchesUiState()
}

@HiltViewModel
class LeaguesViewModel @Inject constructor(
    private val repository: LeaguesRepository
) : ViewModel() {

    private val _leagues = MutableStateFlow<List<LeagueSelectorItem>>(emptyList())
    val leagues: StateFlow<List<LeagueSelectorItem>> = _leagues.asStateFlow()

    private val _selectedLeagueId = MutableStateFlow(-1)
    val selectedLeagueId: StateFlow<Int> = _selectedLeagueId.asStateFlow()

    private val _selectedSeason = MutableStateFlow(calculateCurrentSeason())
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Standings, 1: Matches, 2: Top Scorers, 3: Top Assists
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _standingsState = MutableStateFlow<StandingsUiState>(StandingsUiState.Loading)
    val standingsState: StateFlow<StandingsUiState> = _standingsState.asStateFlow()

    private val _matchesState = MutableStateFlow<LeagueMatchesUiState>(LeagueMatchesUiState.Loading)
    val matchesState: StateFlow<LeagueMatchesUiState> = _matchesState.asStateFlow()

    private var fullMatchesList: List<LeagueMatchListItem> = emptyList()
    private var initialScrollPos: Int = 0
    private var currentMatchLimit: Int = 30
    private var isLoadingMoreMatches: Boolean = false

    private val _topScorersState = MutableStateFlow<TopPlayersUiState>(TopPlayersUiState.Loading)
    val topScorersState: StateFlow<TopPlayersUiState> = _topScorersState.asStateFlow()

    private val _topAssistsState = MutableStateFlow<TopPlayersUiState>(TopPlayersUiState.Loading)
    val topAssistsState: StateFlow<TopPlayersUiState> = _topAssistsState.asStateFlow()

    private fun calculateCurrentSeason(): Int {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        return if (currentMonth < java.util.Calendar.AUGUST) {
            currentYear - 1
        } else {
            currentYear
        }
    }

    init {
        fetchLeagues()
    }

    private fun fetchLeagues() {
        viewModelScope.launch {
            repository.getLeagues()
                .catch { e -> e.printStackTrace() }
                .collect { list ->
                    val items = list.map { league ->
                        LeagueSelectorItem(
                            id = league.league_id,
                            name = league.name,
                            logo = league.logo,
                            country = league.country?.name ?: "World"
                        )
                    }
                    _leagues.value = items
                    if (items.isNotEmpty()) {
                        if (_selectedLeagueId.value == -1 || items.none { it.id == _selectedLeagueId.value }) {
                            selectLeague(items.first().id)
                        }
                    }
                }
        }
    }

    fun selectLeague(leagueId: Int) {
        _selectedLeagueId.value = leagueId
        val season = if (leagueId == 1) 2026 else calculateCurrentSeason()
        _selectedSeason.value = season
        fetchAllLeagueData(leagueId, season)
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun refreshCurrentData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchAllLeagueDataInternal(_selectedLeagueId.value, _selectedSeason.value)
            _isRefreshing.value = false
        }
    }

    fun loadMoreMatches() {
        if (isLoadingMoreMatches || fullMatchesList.isEmpty()) return
        if (currentMatchLimit >= fullMatchesList.size) return

        isLoadingMoreMatches = true
        currentMatchLimit = (currentMatchLimit + 30).coerceAtMost(fullMatchesList.size)
        val displayed = fullMatchesList.take(currentMatchLimit)
        val hasMore = currentMatchLimit < fullMatchesList.size

        _matchesState.value = LeagueMatchesUiState.Success(displayed, -1, hasMore)
        isLoadingMoreMatches = false
    }

    private fun fetchAllLeagueData(leagueId: Int, season: Int) {
        viewModelScope.launch {
            fetchAllLeagueDataInternal(leagueId, season)
        }
    }

    private suspend fun fetchAllLeagueDataInternal(leagueId: Int, season: Int) {
        _standingsState.value = StandingsUiState.Loading
        _matchesState.value = LeagueMatchesUiState.Loading
        _topScorersState.value = TopPlayersUiState.Loading
        _topAssistsState.value = TopPlayersUiState.Loading

        try {
            var standings = emptyList<StandingRowDto>()
            repository.getStandings(leagueId, season)
                .catch { e -> e.printStackTrace() }
                .collect { list -> standings = list }
            _standingsState.value = StandingsUiState.Success(standings)

            var matches = emptyList<MatchItemDto>()
            repository.getFixturesByLeague(leagueId, season)
                .catch { e -> e.printStackTrace() }
                .collect { list -> matches = list }
            
            val (matchItems, targetPos) = processAndGroupMatches(matches)
            fullMatchesList = matchItems
            initialScrollPos = targetPos
            currentMatchLimit = (targetPos + 25).coerceAtLeast(30).coerceAtMost(fullMatchesList.size)
            
            val displayed = fullMatchesList.take(currentMatchLimit)
            val hasMore = currentMatchLimit < fullMatchesList.size
            _matchesState.value = LeagueMatchesUiState.Success(displayed, initialScrollPos, hasMore)

            delay(1000)

            var topScorers = emptyList<TopPlayerItemDto>()
            repository.getTopScorers(leagueId, season)
                .catch { e -> e.printStackTrace() }
                .collect { list -> topScorers = list }
            _topScorersState.value = TopPlayersUiState.Success(topScorers)

            delay(1000)

            var topAssists = emptyList<TopPlayerItemDto>()
            repository.getTopAssists(leagueId, season)
                .catch { e -> e.printStackTrace() }
                .collect { list -> topAssists = list }
            _topAssistsState.value = TopPlayersUiState.Success(topAssists)

        } catch (e: Exception) {
            e.printStackTrace()
            _standingsState.value = StandingsUiState.Error(e.message ?: "Failed to load standings")
            _matchesState.value = LeagueMatchesUiState.Error(e.message ?: "Failed to load matches")
            _topScorersState.value = TopPlayersUiState.Error(e.message ?: "Failed to load top players")
            _topAssistsState.value = TopPlayersUiState.Error(e.message ?: "Failed to load top players")
        }
    }

    private fun processAndGroupMatches(matches: List<MatchItemDto>): Pair<List<LeagueMatchListItem>, Int> {
        if (matches.isEmpty()) return Pair(emptyList(), 0)

        val inputIsoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val headerDisplayFormat = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
        val todayStr = inputIsoFormat.format(java.util.Date())

        val groupedByDate = matches.groupBy { match ->
            try {
                match.fixture.date.substring(0, 10)
            } catch (e: Exception) {
                "Unknown"
            }
        }.toSortedMap()

        val listItems = mutableListOf<LeagueMatchListItem>()
        var scrollToPos = 0
        var foundToday = false
        var closestDiff = Long.MAX_VALUE
        var closestPos = 0

        val todayTime = try {
            inputIsoFormat.parse(todayStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        groupedByDate.forEach { (dateRaw, matchGroup) ->
            val dateHeaderDisplay = try {
                val parsed = inputIsoFormat.parse(dateRaw)
                if (parsed != null) headerDisplayFormat.format(parsed) else dateRaw
            } catch (e: Exception) {
                dateRaw
            }

            val currentHeaderIndex = listItems.size
            listItems.add(LeagueMatchListItem.DateHeader(dateHeaderDisplay, dateRaw))

            val dateGroupTime = try {
                inputIsoFormat.parse(dateRaw)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }

            if (dateRaw == todayStr) {
                scrollToPos = currentHeaderIndex
                foundToday = true
            } else if (!foundToday && dateGroupTime > 0) {
                val diff = Math.abs(dateGroupTime - todayTime)
                if (diff < closestDiff) {
                    closestDiff = diff
                    closestPos = currentHeaderIndex
                }
            }

            matchGroup.forEach { matchItem ->
                listItems.add(LeagueMatchListItem.Match(matchItem))
            }
        }

        val finalScrollPos = if (foundToday) scrollToPos else closestPos
        return Pair(listItems, finalScrollPos)
    }
}
