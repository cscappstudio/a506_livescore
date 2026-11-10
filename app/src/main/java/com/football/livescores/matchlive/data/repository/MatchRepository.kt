package com.football.livescores.matchlive.data.repository

import android.util.Log
import com.football.livescores.matchlive.data.local.dao.FavoriteDao
import com.football.livescores.matchlive.data.local.dao.MatchDao
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteLeagueEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteTeamEntity
import com.football.livescores.matchlive.data.remote.ApiService
import com.football.livescores.matchlive.data.remote.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRepository @Inject constructor(
    private val apiService: ApiService,
    private val matchDao: MatchDao,
    private val favoriteDao: FavoriteDao
) {
    companion object {
        private const val TAG = "MatchRepository"
    }

    // --- Cached Matches Flow ---
    val allCachedMatches: Flow<List<CachedMatchEntity>> = matchDao.getAllCachedMatches()
    val liveCachedMatches: Flow<List<CachedMatchEntity>> = matchDao.getLiveCachedMatches()

    fun getCachedMatchesByQueryDate(dateStr: String): Flow<List<CachedMatchEntity>> {
        return matchDao.getCachedMatchesByQueryDate(dateStr)
    }

    fun getCachedMatchesByTimeRange(startSec: Long, endSec: Long): Flow<List<CachedMatchEntity>> {
        return matchDao.getCachedMatchesByTimeRange(startSec, endSec)
    }

    fun getCachedMatchesForDateOrLive(startSec: Long, endSec: Long): Flow<List<CachedMatchEntity>> {
        return matchDao.getCachedMatchesForDateOrLive(startSec, endSec)
    }

    fun getMatchesByIds(matchIds: List<Int>): Flow<List<CachedMatchEntity>> {
        if (matchIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return matchDao.getMatchesByIds(matchIds)
    }

    fun getRecentCachedMatches(limit: Int = 300): Flow<List<CachedMatchEntity>> {
        return matchDao.getRecentCachedMatches(limit)
    }

    suspend fun refreshLiveMatches() {
        Log.d(TAG, "refreshLiveMatches: Starting request")
        try {
            val response = apiService.getLiveMatches()
            Log.d(TAG, "refreshLiveMatches: Response code = ${response.code}, data size = ${response.data?.size ?: 0}")
            if (response.code == 200 && response.data.isNotEmpty()) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }.format(Calendar.getInstance().time)
                val entities = response.data.mapIndexed { index, dto ->
                    CachedMatchEntity(
                        id = dto.fixture.id,
                        leagueId = dto.league.id,
                        leagueName = dto.league.name,
                        leagueLogo = dto.league.logo,
                        homeTeamId = dto.teams.home.id,
                        homeTeamName = dto.teams.home.name,
                        homeTeamLogo = dto.teams.home.logo,
                        awayTeamId = dto.teams.away.id,
                        awayTeamName = dto.teams.away.name,
                        awayTeamLogo = dto.teams.away.logo,
                        statusShort = dto.fixture.status.short,
                        elapsed = dto.fixture.status.elapsed,
                        goalsHome = dto.goals.home,
                        goalsAway = dto.goals.away,
                        dateTimestamp = dto.fixture.timestamp,
                        statusLong = dto.fixture.status.long,
                        queryDate = todayStr,
                        apiOrder = index
                    )
                }
                matchDao.clearAndInsertMatches(entities)
            } else {
                Log.w(TAG, "refreshLiveMatches: Response empty or code != 200, clearing live matches")
                matchDao.clearLiveMatches()
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshLiveMatches: Error occurred: ${e.message}", e)
            try {
                matchDao.clearLiveMatches()
            } catch (ex: Exception) {
                Log.e(TAG, "refreshLiveMatches: Nested error clearing matches: ${ex.message}", ex)
            }
        } finally {
            try {
                val cutoffTimestamp = (System.currentTimeMillis() - 7 * 24 * 3600 * 1000L) / 1000
                matchDao.pruneOldMatches(cutoffTimestamp)
            } catch (e: Exception) {
                Log.e(TAG, "refreshLiveMatches: Error pruning old matches: ${e.message}")
            }
        }
    }

    suspend fun refreshMatchesByDate(dateStr: String) {
        Log.d(TAG, "refreshMatchesByDate: Starting request for date = $dateStr")
        try {
            val response = apiService.getMatchesByDate(dateStr)
            Log.d(TAG, "refreshMatchesByDate: Response code = ${response.code}, data size = ${response.data?.size ?: 0}")
            if (response.code == 200 && response.data.isNotEmpty()) {
                val entities = response.data.mapIndexed { index, dto ->
                    CachedMatchEntity(
                        id = dto.fixture.id,
                        leagueId = dto.league.id,
                        leagueName = dto.league.name,
                        leagueLogo = dto.league.logo,
                        homeTeamId = dto.teams.home.id,
                        homeTeamName = dto.teams.home.name,
                        homeTeamLogo = dto.teams.home.logo,
                        awayTeamId = dto.teams.away.id,
                        awayTeamName = dto.teams.away.name,
                        awayTeamLogo = dto.teams.away.logo,
                        statusShort = dto.fixture.status.short,
                        elapsed = dto.fixture.status.elapsed,
                        goalsHome = dto.goals.home,
                        goalsAway = dto.goals.away,
                        dateTimestamp = dto.fixture.timestamp,
                        statusLong = dto.fixture.status.long,
                        queryDate = dateStr,
                        apiOrder = index
                    )
                }
                matchDao.clearAndInsertMatchesForDate(entities, dateStr)
            } else {
                Log.w(TAG, "refreshMatchesByDate: Response empty or code != 200, clearing matches for $dateStr")
                matchDao.clearMatchesByQueryDate(dateStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshMatchesByDate: Error occurred: ${e.message}", e)
            try {
                matchDao.clearMatchesByQueryDate(dateStr)
            } catch (ex: Exception) {
                Log.e(TAG, "refreshMatchesByDate: Nested error clearing matches for $dateStr: ${ex.message}", ex)
            }
        } finally {
            try {
                val cutoffTimestamp = (System.currentTimeMillis() - 7 * 24 * 3600 * 1000L) / 1000
                matchDao.pruneOldMatches(cutoffTimestamp)
            } catch (e: Exception) {
                Log.e(TAG, "refreshMatchesByDate: Error pruning old matches: ${e.message}")
            }
        }
    }

    // --- Detail Api Calls with Zero Mock Fallbacks ---
    fun getMatchDetail(id: Int): Flow<MatchDetailDto?> = flow {
        Log.d(TAG, "getMatchDetail: Starting request for matchId = $id")
        val response = apiService.getMatchDetail(id)
        val data = response.data ?: emptyList()
        Log.d(TAG, "getMatchDetail: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data.firstOrNull())
        } else {
            Log.w(TAG, "getMatchDetail: Response empty or code != 200")
            emit(null)
        }
    }.catch { e ->
        Log.e(TAG, "getMatchDetail: Error occurred: ${e.message}", e)
        emit(null)
    }

    fun getAiPrediction(matchId: Int, lang: String): Flow<PredictionResponse?> = flow {
        Log.d(TAG, "getAiPrediction: Fetching prediction for matchId = $matchId, lang = $lang")
        val response = apiService.getAiPrediction(matchId, lang)
        if (response.code == 200) {
            emit(response)
        } else {
            emit(null)
        }
    }.catch { e ->
        Log.e(TAG, "getAiPrediction: Error occurred: ${e.message}", e)
        emit(null)
    }



    // --- Favorites ---
    val favoriteTeams: Flow<List<FavoriteTeamEntity>> = favoriteDao.getAllFavoriteTeams()
    val favoriteLeagues: Flow<List<FavoriteLeagueEntity>> = favoriteDao.getAllFavoriteLeagues()

    suspend fun toggleFavoriteTeam(id: Int, name: String, logo: String) {
        val exists = favoriteDao.isTeamFavorite(id)
        if (exists > 0) {
            favoriteDao.deleteFavoriteTeam(FavoriteTeamEntity(id, name, logo))
        } else {
            favoriteDao.insertFavoriteTeam(FavoriteTeamEntity(id, name, logo))
        }
    }

    suspend fun toggleFavoriteLeague(id: Int, name: String, logo: String, country: String) {
        val exists = favoriteDao.isLeagueFavorite(id)
        if (exists > 0) {
            favoriteDao.deleteFavoriteLeague(FavoriteLeagueEntity(id, name, logo, country))
        } else {
            favoriteDao.insertFavoriteLeague(FavoriteLeagueEntity(id, name, logo, country))
        }
    }

    suspend fun getCachedMatchById(id: Int): CachedMatchEntity? {
        return matchDao.getCachedMatchById(id)
    }

    suspend fun getCachedMatchDetail(id: Int): MatchDetailDto? {
        val item = getCachedMatchById(id)?.toMapDetailDto() ?: return null
        return MatchDetailDto(
            fixture = item.fixture,
            league = item.league,
            teams = item.teams,
            goals = item.goals,
            score = item.score,
            statistics = emptyList(),
            events = emptyList(),
            lineups = emptyList()
        )
    }
}

fun CachedMatchEntity.toMapDetailDto(): MatchItemDto {
    return MatchItemDto(
        fixture = FixtureDto(
            id = id,
            referee = null,
            timezone = "UTC",
            date = "",
            timestamp = dateTimestamp,
            periods = null,
            venue = null,
            status = StatusDto(
                long = statusLong,
                short = statusShort,
                elapsed = elapsed
            )
        ),
        league = LeagueDto(
            id = leagueId,
            name = leagueName,
            country = "",
            logo = leagueLogo,
            flag = null,
            season = 2026,
            round = null
        ),
        teams = TeamsContainerDto(
            home = TeamDto(id = homeTeamId, name = homeTeamName, logo = homeTeamLogo, winner = null),
            away = TeamDto(id = awayTeamId, name = awayTeamName, logo = awayTeamLogo, winner = null)
        ),
        goals = GoalsDto(
            home = goalsHome,
            away = goalsAway
        ),
        score = null
    )
}
