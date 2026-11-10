package com.football.livescores.matchlive.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.remote.model.TeamFixtureItemDto
import com.football.livescores.matchlive.data.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.football.livescores.matchlive.utils.SystemUtil
import dagger.hilt.android.qualifiers.ApplicationContext

sealed class TeamFixturesUiState {
    object Loading : TeamFixturesUiState()
    data class Success(
        val teamId: Int,
        val teamName: String,
        val currentLast: Int,
        val fixtures: List<TeamFixtureItemDto>,
        val wins: Int,
        val draws: Int,
        val losses: Int
    ) : TeamFixturesUiState()
    data class Error(val message: String) : TeamFixturesUiState()
}

@HiltViewModel
class TeamFixturesViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<TeamFixturesUiState>(TeamFixturesUiState.Loading)
    val uiState: StateFlow<TeamFixturesUiState> = _uiState.asStateFlow()

    private var currentTeamId: Int = -1
    private var currentTeamName: String = ""
    private var currentLast: Int = 5

    fun loadTeamFixtures(teamId: Int, teamName: String, last: Int = 5) {
        this.currentTeamId = teamId
        this.currentTeamName = teamName
        this.currentLast = last

        viewModelScope.launch {
            _uiState.value = TeamFixturesUiState.Loading
            val userLang = SystemUtil.getPreLanguage(context).ifEmpty { "en" }
            val result = teamRepository.getTeamFixtures(teamId = teamId, last = last, lang = userLang)
            result.onSuccess { fixtures ->
                var wins = 0
                var draws = 0
                var losses = 0

                fixtures.forEach { fixture ->
                    val homeTeamId = fixture.teams?.home?.id
                    val awayTeamId = fixture.teams?.away?.id
                    val homeGoals = fixture.goals?.home ?: fixture.score?.fulltime?.home ?: 0
                    val awayGoals = fixture.goals?.away ?: fixture.score?.fulltime?.away ?: 0

                    if (homeGoals == awayGoals) {
                        draws++
                    } else if (teamId == homeTeamId) {
                        if (homeGoals > awayGoals) wins++ else losses++
                    } else if (teamId == awayTeamId) {
                        if (awayGoals > homeGoals) wins++ else losses++
                    }
                }

                _uiState.value = TeamFixturesUiState.Success(
                    teamId = teamId,
                    teamName = teamName,
                    currentLast = last,
                    fixtures = fixtures,
                    wins = wins,
                    draws = draws,
                    losses = losses
                )
            }.onFailure { exception ->
                _uiState.value = TeamFixturesUiState.Error(
                    exception.message ?: "Failed to load team fixtures. Please try again."
                )
            }
        }
    }

    fun selectLastFilter(lastCount: Int) {
        if (currentTeamId > 0 && currentLast != lastCount) {
            loadTeamFixtures(currentTeamId, currentTeamName, lastCount)
        }
    }
}
