package com.football.livescores.matchlive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_matches")
data class CachedMatchEntity(
    @PrimaryKey val id: Int,
    val leagueId: Int,
    val leagueName: String,
    val leagueLogo: String,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String,
    val statusShort: String,
    val elapsed: Int?,
    val goalsHome: Int?,
    val goalsAway: Int?,
    val dateTimestamp: Long,
    val statusLong: String,
    val queryDate: String,
    val apiOrder: Int = 0
)
