package com.football.livescores.matchlive.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.football.livescores.matchlive.data.local.RequestLimitManager
import com.football.livescores.matchlive.data.remote.model.*
import com.football.livescores.matchlive.data.repository.MatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class MatchDetailUiState(
    val detail: MatchDetailDto? = null,
    val stats: List<StatisticItemDto> = emptyList(),
    val events: List<EventItemDto> = emptyList(),
    val lineups: List<LineupItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val prediction: PredictionDataDto? = null,
    val isPredictionLoading: Boolean = false,
    val predictionError: String? = null
)

@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val limitManager: RequestLimitManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchDetailUiState())
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var simulationJob: Job? = null

    // Simulation flows for canvas views
    private val _ballPosition = MutableStateFlow(Pair(0.5f, 0.5f))
    val ballPosition: StateFlow<Pair<Float, Float>> = _ballPosition.asStateFlow()

    private val _attackState = MutableStateFlow(0) // 0 = none, 1 = Home, 2 = Away
    val attackState: StateFlow<Int> = _attackState.asStateFlow()

    private val _momentumData = MutableStateFlow<List<Float>>(emptyList())
    val momentumData: StateFlow<List<Float>> = _momentumData.asStateFlow()

    // Smooth continuous simulation state
    private var simBallX = 0.5f
    private var simBallY = 0.5f
    private var simTargetX = 0.5f
    private var simTargetY = 0.5f
    private var simAttackSide = 0
    private var simStateTicks = 0

    fun startDetailPolling(matchId: Int) {
        if (pollingJob?.isActive == true) return
        
        // Ensure simulation is in a clean, default stopped/centered state initially
        stopLocalVisualSimulation()

        pollingJob = viewModelScope.launch {
            // Load from database instantly to populate the UI (teams, logos, score, status, league)
            try {
                repository.getCachedMatchDetail(matchId)?.let { cachedDetail ->
                    _uiState.value = _uiState.value.copy(
                        detail = cachedDetail
                    )
                    updateSimulationState(cachedDetail)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var isFirstLoad = true

            while (true) {
                // Only show loading spinner on the very first load if we don't even have cached detail
                if (_uiState.value.detail == null) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }

                try {
                    // Refresh from database cache first to get the latest scores, teams, and statuses
                    repository.getCachedMatchDetail(matchId)?.let { cachedDetail ->
                        val current = _uiState.value.detail
                        _uiState.value = _uiState.value.copy(
                            detail = current?.copy(
                                fixture = cachedDetail.fixture,
                                league = cachedDetail.league,
                                teams = cachedDetail.teams,
                                goals = cachedDetail.goals,
                                score = cachedDetail.score
                            ) ?: cachedDetail
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    // 1. Get Match Detail FIRST and update UI instantly to show teams, logos, and scores
                    repository.getMatchDetail(matchId).collect { remoteDetail ->
                        if (remoteDetail != null) {
                            val current = _uiState.value.detail
                            val mergedDetail = remoteDetail.copy(
                                fixture = if (remoteDetail.fixture.status.short.isNotEmpty() && remoteDetail.fixture.status.short != "NS") remoteDetail.fixture else current?.fixture ?: remoteDetail.fixture,
                                league = if (remoteDetail.league.id != 0) remoteDetail.league else current?.league ?: remoteDetail.league,
                                teams = if (remoteDetail.teams.home.id != 0) remoteDetail.teams else current?.teams ?: remoteDetail.teams,
                                goals = if (remoteDetail.goals.home != null || remoteDetail.goals.away != null) remoteDetail.goals else current?.goals ?: remoteDetail.goals,
                                score = if (remoteDetail.score != null) remoteDetail.score else current?.score ?: remoteDetail.score
                            )
                            _uiState.value = _uiState.value.copy(
                                detail = mergedDetail,
                                stats = remoteDetail.statistics,
                                events = remoteDetail.events,
                                lineups = remoteDetail.lineups,
                                isLoading = false
                            )
                            updateSimulationState(mergedDetail)
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }

                isFirstLoad = false
                delay(limitManager.getPollingInterval())
            }
        }
    }

    private fun updateSimulationState(detail: MatchDetailDto?) {
        val statusShort = detail?.fixture?.status?.short ?: "NS"
        val isLive = statusShort in listOf("1H", "HT", "2H", "ET", "BT", "P", "INT", "LIVE")
        
        if (isLive) {
            startLocalVisualSimulation()
        } else {
            stopLocalVisualSimulation()
        }
    }

    private fun startLocalVisualSimulation() {
        if (simulationJob?.isActive == true) return
        simulationJob = viewModelScope.launch {
            if (_momentumData.value.isEmpty() || _momentumData.value.all { it == 0f }) {
                _momentumData.value = List(30) { Random.nextFloat() * 140 - 70 }
            }
            while (true) {
                simulateCanvasEvents()
                delay(150) // Smooth physics-based updates every 150 milliseconds
            }
        }
    }

    private fun stopLocalVisualSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _ballPosition.value = Pair(0.5f, 0.5f)
        _attackState.value = 0
        _momentumData.value = List(30) { 0f }
        
        // Reset continuous simulation states
        simBallX = 0.5f
        simBallY = 0.5f
        simTargetX = 0.5f
        simTargetY = 0.5f
        simAttackSide = 0
        simStateTicks = 0
    }

    fun stopDetailPolling() {
        pollingJob?.cancel()
        pollingJob = null
        stopLocalVisualSimulation()
    }

    private fun getPossessionStats(): Pair<Int, Int> {
        val statsList = _uiState.value.stats
        if (statsList.size < 2) return Pair(50, 50)
        
        val homeStats = statsList[0].statistics
        val awayStats = statsList[1].statistics
        
        fun parseVal(value: Any?): Int {
            return when (value) {
                is Double -> value.toInt()
                is Float -> value.toInt()
                is String -> value.replace("%", "").trim().toIntOrNull() ?: 0
                is Number -> value.toInt()
                else -> 0
            }
        }
        
        val homePoss = homeStats.find { it.type == "Ball Possession" }?.value?.let { parseVal(it) } ?: 50
        val awayPoss = awayStats.find { it.type == "Ball Possession" }?.value?.let { parseVal(it) } ?: 50
        
        if (homePoss == 0 && awayPoss == 0) return Pair(50, 50)
        return Pair(homePoss, awayPoss)
    }

    private fun simulateCanvasEvents() {
        simStateTicks++
        
        // Every 40 ticks (~6 seconds), dynamically transition the team possession/attack state
        if (simStateTicks >= 40 || simStateTicks == 1) {
            if (simStateTicks >= 40 || (simStateTicks == 1 && simAttackSide == 0)) {
                if (simStateTicks >= 40) {
                    simStateTicks = 0
                }
                
                // Bias based on actual possession
                val (homePoss, awayPoss) = getPossessionStats()
                val totalPoss = (homePoss + awayPoss).toFloat()
                val homeWeight = if (totalPoss > 0) homePoss / totalPoss else 0.5f
                val roll = Random.nextFloat()
                simAttackSide = when {
                    roll < homeWeight * 0.85f -> 1 // Home attacking (proportional to possession)
                    roll < 0.85f -> 2 // Away attacking
                    else -> 0 // Midfield play (15%)
                }
                _attackState.value = simAttackSide
            }
            
            // Scroll the threat pressure curve seamlessly to the right
            val currentList = _momentumData.value.toMutableList()
            if (currentList.isNotEmpty()) {
                currentList.removeAt(0)
                val threatValue = when (simAttackSide) {
                    1 -> Random.nextFloat() * 50 + 20   // Positive attack pressure for home
                    2 -> -(Random.nextFloat() * 50 + 20)  // Negative attack pressure for away
                    else -> Random.nextFloat() * 30 - 15  // Midfield balance play
                }
                currentList.add(threatValue)
                _momentumData.value = currentList
            }
        }

        // Determine if ball arrived at the target position. If yes, trigger a pass/shot (new target)
        val isClose = Math.abs(simTargetX - simBallX) < 0.08f && Math.abs(simTargetY - simBallY) < 0.08f
        if (isClose || simStateTicks == 1) {
            when (simAttackSide) {
                1 -> { // Home attacking right
                    simTargetX = Random.nextFloat() * 0.38f + 0.55f // Target home side (0.55f - 0.93f)
                    simTargetY = Random.nextFloat() * 0.7f + 0.15f  // Target height (0.15f - 0.85f)
                }
                2 -> { // Away attacking left
                    simTargetX = Random.nextFloat() * 0.38f + 0.07f // Target away side (0.07f - 0.45f)
                    simTargetY = Random.nextFloat() * 0.7f + 0.15f  // Target height (0.15f - 0.85f)
                }
                else -> { // Midfield play
                    simTargetX = Random.nextFloat() * 0.3f + 0.35f  // Target center (0.35f - 0.65f)
                    simTargetY = Random.nextFloat() * 0.6f + 0.2f   // Target height (0.20f - 0.80f)
                }
            }
        }

        // Apply smooth organic easing interpolation to glide the ball elegantly
        simBallX += (simTargetX - simBallX) * 0.12f
        simBallY += (simTargetY - simBallY) * 0.12f
        
        _ballPosition.value = Pair(simBallX, simBallY)
    }

    fun fetchAiPrediction(matchId: Int, lang: String) {
        if (_uiState.value.prediction != null || _uiState.value.isPredictionLoading) return
        _uiState.value = _uiState.value.copy(isPredictionLoading = true, predictionError = null)

        viewModelScope.launch {
            repository.getAiPrediction(matchId, lang).collect { response ->
                if (response != null && response.code == 200) {
                    _uiState.value = _uiState.value.copy(
                        prediction = response.data,
                        isPredictionLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isPredictionLoading = false,
                        predictionError = "Failed to load prediction"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDetailPolling()
    }
}
