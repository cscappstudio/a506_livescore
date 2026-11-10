package com.football.livescores.matchlive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_leagues")
data class FavoriteLeagueEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val logo: String,
    val country: String
)
