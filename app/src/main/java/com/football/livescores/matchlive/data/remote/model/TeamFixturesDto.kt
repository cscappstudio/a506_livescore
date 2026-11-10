package com.football.livescores.matchlive.data.remote.model

import com.google.gson.annotations.SerializedName

data class TeamFixturesResponseDto(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: TeamFixturesDataDto?
)

data class TeamFixturesDataDto(
    @SerializedName("team_id")
    val teamId: Int?,
    @SerializedName("query_key")
    val queryKey: String?,
    @SerializedName("fixtures")
    val fixtures: List<TeamFixtureItemDto>?
)

data class TeamFixtureItemDto(
    @SerializedName("fixture_id")
    val fixtureId: Int?,
    @SerializedName("id")
    val id: Int? = null,
    @SerializedName("fixture")
    val fixture: NestedFixtureIdDto? = null,
    @SerializedName("league_id")
    val leagueId: Int?,
    @SerializedName("league")
    val league: LeagueInfoDto?,
    @SerializedName("teams")
    val teams: TeamsPairDto?,
    @SerializedName("goals")
    val goals: ScorePairDto?,
    @SerializedName("score")
    val score: ScoreDetailsDto?,
    @SerializedName("status")
    val status: StatusDto?,
    @SerializedName("date")
    val date: String?,
    @SerializedName("fixture_date")
    val fixtureDate: String?,
    @SerializedName("season")
    val season: Int?,
    @SerializedName("round")
    val round: String?
) {
    val getRealFixtureId: Int
        get() = fixtureId ?: id ?: fixture?.id ?: 0
}

data class NestedFixtureIdDto(
    @SerializedName("id")
    val id: Int?
)

data class LeagueInfoDto(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("logo")
    val logo: String?,
    @SerializedName("country")
    val country: String?,
    @SerializedName("flag")
    val flag: String?
)

data class TeamsPairDto(
    @SerializedName("home")
    val home: TeamDetailDto?,
    @SerializedName("away")
    val away: TeamDetailDto?
)

data class TeamDetailDto(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("logo")
    val logo: String?,
    @SerializedName("winner")
    val winner: Boolean?
)

data class ScorePairDto(
    @SerializedName("home")
    val home: Int?,
    @SerializedName("away")
    val away: Int?
)

data class ScoreDetailsDto(
    @SerializedName("halftime")
    val halftime: ScorePairDto?,
    @SerializedName("fulltime")
    val fulltime: ScorePairDto?,
    @SerializedName("extratime")
    val extratime: ScorePairDto?,
    @SerializedName("penalty")
    val penalty: ScorePairDto?
)
