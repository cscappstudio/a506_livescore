package com.football.livescores.matchlive.data.remote.model

import com.google.gson.annotations.SerializedName

// --- STANDINGS DTOs ---


data class StandingsLeagueWrapperDto(
    val league: StandingsLeagueDto
)

data class StandingsLeagueDto(
    val id: Int,
    val name: String,
    val country: String,
    val logo: String,
    val flag: String?,
    val season: Int,
    val standings: List<List<StandingRowDto>>
)

data class StandingRowDto(
    val rank: Int,
    val team: StandingTeamDto,
    val points: Int,
    val goalsDiff: Int,
    val group: String?,
    val form: String?,
    val status: String?,
    val description: String?,
    val all: StandingStatsDto,
    val home: StandingStatsDto?,
    val away: StandingStatsDto?
)

data class StandingTeamDto(
    val id: Int,
    val name: String,
    val logo: String
)

data class StandingStatsDto(
    val played: Int,
    val win: Int,
    val draw: Int,
    val lose: Int,
    val goals: StandingGoalsDto
)

data class StandingGoalsDto(
    @SerializedName("for") val goalsFor: Int,
    val against: Int
)

// --- TOP PLAYERS DTOs ---


data class TopPlayerItemDto(
    val player: TopPlayerPlayerDto,
    val statistics: List<TopPlayerStatDto>
)

data class TopPlayerPlayerDto(
    val id: Int,
    val name: String,
    val firstname: String?,
    val lastname: String?,
    val age: Int?,
    val nationality: String?,
    val height: String?,
    val weight: String?,
    val photo: String
)

data class TopPlayerStatDto(
    val team: StandingTeamDto,
    val league: TopPlayerLeagueDto?,
    val games: TopPlayerGamesDto?,
    val goals: TopPlayerGoalsDto?,
    val penalty: TopPlayerPenaltyDto?
)

data class TopPlayerLeagueDto(
    val id: Int,
    val name: String,
    val country: String,
    val logo: String,
    val season: Int
)

data class TopPlayerGamesDto(
    val appearances: Int?,
    val lineups: Int?,
    val minutes: Int?,
    val number: Int?,
    val position: String?,
    val rating: String?,
    val captain: Boolean?
)

data class TopPlayerGoalsDto(
    val total: Int,
    val assists: Int?,
    val saves: Int?,
    val concessions: Int?
)

data class TopPlayerPenaltyDto(
    val won: Int?,
    val committed: Int?,
    val scored: Int?,
    val missed: Int?,
    val saved: Int?
)
