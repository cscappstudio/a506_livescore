package com.football.livescores.matchlive.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.repository.MatchRepository
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.local.MatchReminderManager
import com.football.livescores.matchlive.data.local.RequestLimitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class MatchFilter {
    LIVE, UPCOMING, FINISHED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val favoriteManager: FavoriteManager,
    private val reminderManager: MatchReminderManager,
    private val limitManager: RequestLimitManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow<Date>(Calendar.getInstance().time)
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()

    private val _currentFilter = MutableStateFlow(MatchFilter.LIVE)
    val currentFilter: StateFlow<MatchFilter> = _currentFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _favoriteFixtureIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteFixtureIds: StateFlow<Set<String>> = _favoriteFixtureIds.asStateFlow()

    private val _reminderFixtureIds = MutableStateFlow<Set<Int>>(emptySet())
    val reminderFixtureIds: StateFlow<Set<Int>> = _reminderFixtureIds.asStateFlow()

    private var pollingJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val matches: StateFlow<List<MatchListItem>> = combine(
        _selectedDate.flatMapLatest { date ->
            val calStart = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val startSec = calStart.timeInMillis / 1000
            val endSec = calEnd.timeInMillis / 1000
            val matchFlow = if (isToday(date)) {
                repository.getCachedMatchesForDateOrLive(startSec, endSec)
            } else {
                repository.getCachedMatchesByTimeRange(startSec, endSec)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = java.util.TimeZone.getDefault()
            }
            val dateStr = sdf.format(date)
            matchFlow.map { matchesList ->
                matchesList.filter { match ->
                    val matchLocalDay = sdf.format(Date(match.dateTimestamp * 1000))
                    matchLocalDay == dateStr || isLiveStatus(match.statusShort)
                }
            }
        }.combine(_currentFilter) { filteredMatches, filter ->
            val currentTime = System.currentTimeMillis()
            val cutoffDuration = 2 * 60 * 60 * 1000L // 2 hours
            when (filter) {
                MatchFilter.LIVE -> filteredMatches.filter { 
                    isLiveStatus(it.statusShort)
                }
                MatchFilter.UPCOMING -> filteredMatches.filter { 
                    (it.statusShort == "NS" || it.statusShort == "TBD" || it.statusShort == "PST") &&
                    ((it.dateTimestamp * 1000 + cutoffDuration) > currentTime)
                }
                MatchFilter.FINISHED -> filteredMatches.filter { 
                    it.statusShort == "FT" || it.statusShort == "AET" || it.statusShort == "PEN" || it.statusShort == "CANC" || it.statusShort == "ABD" ||
                    ((it.statusShort == "NS" || it.statusShort == "TBD" || it.statusShort == "PST") &&
                     ((it.dateTimestamp * 1000 + cutoffDuration) <= currentTime))
                }
            }
        },
        _favoriteFixtureIds,
        _reminderFixtureIds,
        limitManager.isPremiumFlow
    ) { filteredMatches, favIds, remindIds, isPremium ->
        val items = mutableListOf<MatchListItem>()
        val grouped = filteredMatches.groupBy { it.leagueId }

        for ((leagueId, matchGroup) in grouped) {
            val firstMatch = matchGroup.first()
            items.add(MatchListItem.LeagueHeader(leagueId, firstMatch.leagueName, firstMatch.leagueLogo))
            for (match in matchGroup) {
                val isFav = favIds.contains(match.id.toString())
                val isRemind = remindIds.contains(match.id)
                items.add(MatchListItem.MatchItem(match, isFav, isRemind))
            }
        }
        items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshFavorites()
        refreshReminders()
        setSelectedDate(Calendar.getInstance().time)
    }

    fun refreshFavorites() {
        _favoriteFixtureIds.value = favoriteManager.getFavoriteFixtureIds()
    }

    fun refreshReminders() {
        _reminderFixtureIds.value = reminderManager.getAllReminderIds()
    }

    fun setSelectedDate(date: Date) {
        _selectedDate.value = date
        if (isToday(date)) {
            _currentFilter.value = MatchFilter.LIVE
            startLivePolling()
        } else if (isBeforeToday(date)) {
            _currentFilter.value = MatchFilter.FINISHED
            stopLivePolling()
            refreshMatchesForDate(date)
        } else {
            _currentFilter.value = MatchFilter.UPCOMING
            stopLivePolling()
            refreshMatchesForDate(date)
        }
    }

    fun setFilter(filter: MatchFilter) {
        _currentFilter.value = filter
        val date = _selectedDate.value
        if (filter == MatchFilter.LIVE && isToday(date)) {
            startLivePolling()
        } else {
            stopLivePolling()
            refreshMatchesForDate(date)
        }
    }

    fun startLivePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                _isLoading.value = true
                try {
                    // 1. Refresh currently live matches globally to get live scores for all live matches
                    repository.refreshLiveMatches()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    // 2. Also refresh the overlapping UTC dates for today to keep scheduled and finished matches updated
                    val today = Calendar.getInstance().time
                    val utcDates = getOverlappingUtcDates(today)
                    for (utcDate in utcDates) {
                        try {
                            repository.refreshMatchesByDate(utcDate)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                _isLoading.value = false
                delay(limitManager.getPollingInterval())
            }
        }
    }

    fun stopLivePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshMatchesForDate(date: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            val utcDates = getOverlappingUtcDates(date)
            for (utcDate in utcDates) {
                try {
                    repository.refreshMatchesByDate(utcDate)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val date = _selectedDate.value
            try {
                if (_currentFilter.value == MatchFilter.LIVE && isToday(date)) {
                    repository.refreshLiveMatches()
                }
                val utcDates = getOverlappingUtcDates(date)
                for (utcDate in utcDates) {
                    repository.refreshMatchesByDate(utcDate)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isToday(date: Date): Boolean {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        return today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
     }

    private fun isBeforeToday(date: Date): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.before(today)
    }

    private fun getOverlappingUtcDates(localDate: Date): List<String> {
        val sdfUtc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        
        val calStart = Calendar.getInstance().apply {
            time = localDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateStartUtc = sdfUtc.format(calStart.time)
        
        val calEnd = Calendar.getInstance().apply {
            time = localDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val dateEndUtc = sdfUtc.format(calEnd.time)
        
        return if (dateStartUtc == dateEndUtc) {
            listOf(dateStartUtc)
        } else {
            listOf(dateStartUtc, dateEndUtc)
        }
    }

    private fun isLiveStatus(status: String): Boolean {
        return status in listOf("1H", "2H", "HT", "ET", "BT", "P", "INT", "LIVE")
    }

    fun toggleFavorite(match: CachedMatchEntity) {
        viewModelScope.launch {
            favoriteManager.toggleFixtureFavorite(match.id)
            refreshFavorites()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLivePolling()
    }
}
