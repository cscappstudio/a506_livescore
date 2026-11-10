package com.football.livescores.matchlive.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.local.FavoriteManager
import com.football.livescores.matchlive.data.repository.LeaguesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingItem(
    val id: Int,
    val name: String,
    val subtitle: String,
    val logo: String,
    val type: String, // "language", "league", "team", "player"
    val isHot: Boolean = false,
    val parentId: Int = 0, // e.g. team maps to leagueId, player maps to teamId
    val flagEmoji: String = "",
    val isSelected: Boolean = false
)

private data class SelectionState(
    val step: Int,
    val query: String,
    val leagues: Set<Int>,
    val teams: Set<Int>,
    val language: String,
    val dynamicLeagues: List<OnboardingItem>,
    val dynamicTeams: List<OnboardingItem>
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteManager: FavoriteManager,
    private val leaguesRepository: LeaguesRepository
) : ViewModel() {

    private val onboardingPrefs: SharedPreferences =
        context.getSharedPreferences("livescore_onboarding_prefs", Context.MODE_PRIVATE)

    // Current screen: 1 = Select Leagues, 2 = Select Teams
    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selections
    private val _selectedLanguage = MutableStateFlow("English")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedLeagues = MutableStateFlow<Set<Int>>(emptySet())
    val selectedLeagues: StateFlow<Set<Int>> = _selectedLeagues.asStateFlow()

    private val _selectedTeams = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTeams: StateFlow<Set<Int>> = _selectedTeams.asStateFlow()

    // Event flow to notify Activity to redirect to MainActivity when completed
    private val _onboardingCompleted = MutableSharedFlow<Boolean>()
    val onboardingCompleted: SharedFlow<Boolean> = _onboardingCompleted.asSharedFlow()

    private val _dynamicLeagues = MutableStateFlow<List<OnboardingItem>>(emptyList())
    private val _dynamicTeams = MutableStateFlow<List<OnboardingItem>>(emptyList())

    init {
        loadLeagues()

        viewModelScope.launch {
            _selectedLeagues.collect {
                loadTeamsForSelectedLeagues()
            }
        }
    }

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

    private fun loadLeagues() {
        viewModelScope.launch {
            leaguesRepository.getLeagues()
                .catch { e -> Log.e("OnboardingViewModel", "Error loading leagues", e) }
                .collect { list ->
                    val items = list.map { league ->
                        OnboardingItem(
                            id = league.league_id,
                            name = league.name.orEmpty(),
                            subtitle = league.country?.name ?: "World",
                            logo = league.logo.orEmpty(),
                            type = "league",
                            isHot = league.is_popular
                        )
                    }
                    _dynamicLeagues.value = items
                }
        }
    }

    private fun loadTeamsForSelectedLeagues() {
        val selectedLeaguesList = _selectedLeagues.value.toList()
        if (selectedLeaguesList.isEmpty()) {
            _dynamicTeams.value = emptyList()
            return
        }
        viewModelScope.launch {
            val allTeams = mutableListOf<OnboardingItem>()
            selectedLeaguesList.forEach { leagueId ->
                val season = if (leagueId == 1) 2026 else calculateCurrentSeason()
                leaguesRepository.getStandings(leagueId, season)
                    .catch { e -> Log.e("OnboardingViewModel", "Error loading standings for league $leagueId", e) }
                    .collect { standingRows ->
                        val teams = standingRows.map { row ->
                            OnboardingItem(
                                id = row.team.id,
                                name = row.team.name.orEmpty(),
                                subtitle = _dynamicLeagues.value.find { it.id == leagueId }?.name ?: "",
                                logo = row.team.logo.orEmpty(),
                                type = "team",
                                parentId = leagueId
                            )
                        }
                        allTeams.addAll(teams)
                    }
            }
            val distinctTeams = allTeams.distinctBy { it.id }
            _dynamicTeams.value = distinctTeams

            // Filter out any selected teams that are no longer in the dynamic list
            val validTeamIds = distinctTeams.map { it.id }.toSet()
            val currentSelectedTeams = _selectedTeams.value
            val updatedSelectedTeams = currentSelectedTeams.intersect(validTeamIds)
            if (updatedSelectedTeams.size != currentSelectedTeams.size) {
                _selectedTeams.value = updatedSelectedTeams
            }
        }
    }

    // Master Static lists
    private val masterLanguages = listOf(
        "Arabic", "English", "French", "German", "Hindi",
        "Indonesian", "Italian", "Japanese", "Portuguese", "Russian",
        "Spanish", "Thai", "Turkish", "Urdu", "Vietnamese"
    ).mapIndexed { idx, name ->
        OnboardingItem(idx, name, "", "", "language")
    }

    // Highly optimized combining logic that produces real-time filtered and prioritized lists
    val uiItems: StateFlow<List<OnboardingItem>> = combine(
        _currentStep,
        _searchQuery,
        combine(_selectedLeagues, _selectedTeams) { l, t -> Pair(l, t) },
        _dynamicLeagues,
        _dynamicTeams
    ) { step, query, selections, dynamicLeagues, dynamicTeams ->
        SelectionState(
            step = step,
            query = query,
            leagues = selections.first,
            teams = selections.second,
            language = _selectedLanguage.value,
            dynamicLeagues = dynamicLeagues,
            dynamicTeams = dynamicTeams
        )
    }.map { state ->
        val step = state.step
        val query = state.query
        val selLeagues = state.leagues
        val selTeams = state.teams
        val selLang = state.language
        val dynamicLeagues = state.dynamicLeagues
        val dynamicTeams = state.dynamicTeams

        val rawList = when (step) {
            0 -> {
                if (query.isEmpty()) masterLanguages
                else masterLanguages.filter { it.name.contains(query, ignoreCase = true) }
            }
            1 -> {
                val baseList = dynamicLeagues.sortedWith(
                    compareByDescending<OnboardingItem> { it.isHot }
                        .thenBy { it.name }
                )
                if (query.isEmpty()) baseList
                else baseList.filter { it.name.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
            }
            2 -> {
                val baseList = dynamicTeams.sortedWith(
                    compareByDescending<OnboardingItem> { selLeagues.contains(it.parentId) }
                        .thenBy { it.name }
                )
                if (query.isEmpty()) baseList
                else baseList.filter { it.name.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
            }
            else -> emptyList()
        }

        rawList.map { item ->
            val isSel = when (step) {
                0 -> selLang == item.name
                1 -> selLeagues.contains(item.id)
                2 -> selTeams.contains(item.id)
                else -> false
            }
            item.copy(isSelected = isSel)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(itemId: Int) {
        when (_currentStep.value) {
            0 -> {
                val langName = masterLanguages.find { it.id == itemId }?.name ?: "English"
                _selectedLanguage.value = langName
            }
            1 -> {
                val current = _selectedLeagues.value.toMutableSet()
                if (current.contains(itemId)) current.remove(itemId)
                else current.add(itemId)
                _selectedLeagues.value = current
            }
            2 -> {
                val current = _selectedTeams.value.toMutableSet()
                if (current.contains(itemId)) current.remove(itemId)
                else current.add(itemId)
                _selectedTeams.value = current
            }
        }
    }

    fun isSelected(itemId: Int, name: String = ""): Boolean {
        return when (_currentStep.value) {
            0 -> _selectedLanguage.value == name
            1 -> _selectedLeagues.value.contains(itemId)
            2 -> _selectedTeams.value.contains(itemId)
            else -> false
        }
    }

    fun nextStep() {
        val next = _currentStep.value + 1
        if (next > 2) {
            finishOnboarding()
        } else {
            _currentStep.value = next
            _searchQuery.value = "" // Reset search bar text on step transition
        }
    }

    fun prevStep() {
        val prev = _currentStep.value - 1
        if (prev >= 1) {
            _currentStep.value = prev
            _searchQuery.value = "" // Reset search bar text on step transition
        }
    }

    fun skipOnboarding() {
        // Skip from any screen: Save language (use whatever is selected or English), and exit
        viewModelScope.launch {
            // Apply language preference
            onboardingPrefs.edit()
                .putString("selected_language", _selectedLanguage.value)
                .putBoolean("onboarding_completed", true)
                .apply()
            
            _onboardingCompleted.emit(true)
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            // 1. Persist Selected Language
            onboardingPrefs.edit()
                .putString("selected_language", _selectedLanguage.value)
                .putBoolean("onboarding_completed", true)
                .apply()

            // 2. Persist Favorite Leagues
            _selectedLeagues.value.forEach { id ->
                val item = _dynamicLeagues.value.find { it.id == id }
                if (item != null) {
                    favoriteManager.toggleLeagueFavorite(item.id, item.name, item.logo, item.subtitle)
                }
            }

            // 3. Persist Favorite Teams
            _selectedTeams.value.forEach { id ->
                val item = _dynamicTeams.value.find { it.id == id }
                if (item != null) {
                    favoriteManager.toggleTeamFavorite(item.id, item.name, item.logo)
                }
            }

            // Notify activity of completion
            _onboardingCompleted.emit(true)
        }
    }
}
