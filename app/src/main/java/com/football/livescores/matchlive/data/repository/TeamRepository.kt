package com.football.livescores.matchlive.data.repository

import com.football.livescores.matchlive.data.remote.ApiService
import com.football.livescores.matchlive.data.remote.model.TeamFixtureItemDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getTeamFixtures(teamId: Int, last: Int = 5, lang: String = "en"): Result<List<TeamFixtureItemDto>> {
        return try {
            val response = apiService.getTeamFixtures(teamId = teamId, last = last, lang = lang)
            if (response.code == 200 && response.data?.fixtures != null) {
                Result.success(response.data.fixtures)
            } else {
                Result.failure(Exception(if (!response.message.isNullOrEmpty()) response.message else "Failed to fetch team fixtures"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
