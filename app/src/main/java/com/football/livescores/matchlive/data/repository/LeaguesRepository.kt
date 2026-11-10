package com.football.livescores.matchlive.data.repository

import android.util.Log
import com.football.livescores.matchlive.data.remote.ApiService
import com.football.livescores.matchlive.data.remote.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaguesRepository @Inject constructor(
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "LeaguesRepository"
    }

    fun getLeagues(): Flow<List<ServerLeagueDto>> = flow {
        Log.d(TAG, "getLeagues: Starting request")
        val response = apiService.getLeagues()
        val data = response.dataOrEmpty()
        Log.d(TAG, "getLeagues: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getLeagues: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getLeagues: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getStandings(leagueId: Int, season: Int): Flow<List<StandingRowDto>> = flow {
        Log.d(TAG, "getStandings: Starting request for leagueId = $leagueId, season = $season")
        val response = apiService.getStandings(leagueId, season)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getStandings: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            val wrapper = data.firstOrNull()
            val standingsList = wrapper?.league?.standings?.flatten() ?: emptyList()
            Log.d(TAG, "getStandings: Extracted standings list size = ${standingsList.size}")
            emit(standingsList)
        } else {
            Log.w(TAG, "getStandings: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getStandings: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getTopScorers(leagueId: Int, season: Int): Flow<List<TopPlayerItemDto>> = flow {
        Log.d(TAG, "getTopScorers: Starting request for leagueId = $leagueId, season = $season")
        val response = apiService.getTopScorers(leagueId, season)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getTopScorers: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getTopScorers: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getTopScorers: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getTopAssists(leagueId: Int, season: Int): Flow<List<TopPlayerItemDto>> = flow {
        Log.d(TAG, "getTopAssists: Starting request for leagueId = $leagueId, season = $season")
        val response = apiService.getTopAssists(leagueId, season)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getTopAssists: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getTopAssists: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getTopAssists: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getFixturesByLeague(leagueId: Int, season: Int, round: String? = null): Flow<List<MatchItemDto>> = flow {
        Log.d(TAG, "getFixturesByLeague: Starting request for leagueId = $leagueId, season = $season, round = $round")
        val response = apiService.getFixturesByLeague(leagueId, season, round)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getFixturesByLeague: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getFixturesByLeague: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getFixturesByLeague: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getFixturesRounds(leagueId: Int, season: Int): Flow<List<String>> = flow {
        Log.d(TAG, "getFixturesRounds: Starting request for leagueId = $leagueId, season = $season")
        val response = apiService.getFixturesRounds(leagueId, season)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getFixturesRounds: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getFixturesRounds: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getFixturesRounds: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    fun getWcBracket(leagueId: Int, season: Int): Flow<List<MatchItemDto>> = flow {
        Log.d(TAG, "getWcBracket: Starting request for leagueId = $leagueId, season = $season")
        val response = apiService.getWcBracket(leagueId, season)
        val data = response.dataOrEmpty()
        Log.d(TAG, "getWcBracket: Response code = ${response.code}, data size = ${data.size}")
        if (response.code == 200 && data.isNotEmpty()) {
            emit(data)
        } else {
            Log.w(TAG, "getWcBracket: Response is empty or code != 200")
            emit(emptyList())
        }
    }.catch { e ->
        Log.e(TAG, "getWcBracket: Error occurred: ${e.message}", e)
        emit(emptyList())
    }

    private fun <T> BaseResponse<T>.dataOrEmpty(): List<T> = this.data ?: emptyList()
}
