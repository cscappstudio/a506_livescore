package com.football.livescores.matchlive.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.local.MatchReminderManager
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.repository.MatchRepository
import com.football.livescores.matchlive.ui.home.MatchListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteManager: FavoriteManager,
    private val matchRepository: MatchRepository,
    private val reminderManager: MatchReminderManager
) : ViewModel() {

    private val _favoriteMatches = MutableStateFlow<List<MatchListItem>>(emptyList())
    val favoriteMatches: StateFlow<List<MatchListItem>> = _favoriteMatches.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadFavoriteMatches()
    }

    fun loadFavoriteMatches() {
        val favIds = favoriteManager.getFavoriteFixtureIds()
        val favIntIds = favIds.mapNotNull { it.toIntOrNull() }.toSet()
        val remindIds = reminderManager.getAllReminderIds()
        
        viewModelScope.launch {
            if (favIntIds.isEmpty()) {
                _favoriteMatches.value = emptyList()
                return@launch
            }
            matchRepository.getMatchesByIds(favIntIds.toList()).map { matches ->
                // Filter matches that are in the user's favorite set
                val filtered = matches.filter { it.id in favIntIds }
                
                // Group matches by league and map to MatchListItem
                val items = mutableListOf<MatchListItem>()
                val grouped = filtered.groupBy { it.leagueId }
                for ((leagueId, matchGroup) in grouped) {
                    val firstMatch = matchGroup.first()
                    items.add(MatchListItem.LeagueHeader(leagueId, firstMatch.leagueName, firstMatch.leagueLogo))
                    for (match in matchGroup) {
                        val isRemind = remindIds.contains(match.id)
                        items.add(MatchListItem.MatchItem(match, isFavorite = true, isReminderSet = isRemind))
                    }
                }
                items
            }.collect { items ->
                _favoriteMatches.value = items
            }
        }
    }

    fun refreshFavoriteMatchesFromServer() {
        val favIds = favoriteManager.getFavoriteFixtureIds()
        val favIntIds = favIds.mapNotNull { it.toIntOrNull() }.toSet()
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (favIntIds.isNotEmpty()) {
                    val currentMatches = matchRepository.getMatchesByIds(favIntIds.toList()).first()
                    val favoriteMatches = currentMatches.filter { it.id in favIntIds }
                    val uniqueDates = favoriteMatches.mapNotNull { it.queryDate }.filter { it.isNotEmpty() }.toSet()
                    
                    val datesToRefresh = if (uniqueDates.isNotEmpty()) {
                        uniqueDates
                    } else {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getDefault()
                        }
                        val todayStr = sdf.format(java.util.Calendar.getInstance().time)
                        setOf(todayStr)
                    }
                    
                    for (date in datesToRefresh) {
                        try {
                            matchRepository.refreshMatchesByDate(date)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadFavoriteMatches()
                _isRefreshing.value = false
            }
        }
    }

    fun toggleFixtureFavorite(match: CachedMatchEntity) {
        viewModelScope.launch {
            favoriteManager.toggleFixtureFavorite(match.id)
            loadFavoriteMatches()
        }
    }
}
