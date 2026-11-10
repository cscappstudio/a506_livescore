package com.football.livescores.matchlive.data.local.dao

import androidx.room.*
import com.football.livescores.matchlive.data.local.entity.FavoriteLeagueEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteTeamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_teams")
    fun getAllFavoriteTeams(): Flow<List<FavoriteTeamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteTeam(team: FavoriteTeamEntity)

    @Delete
    suspend fun deleteFavoriteTeam(team: FavoriteTeamEntity)

    @Query("SELECT COUNT(*) FROM favorite_teams WHERE id = :teamId")
    suspend fun isTeamFavorite(teamId: Int): Int

    @Query("SELECT * FROM favorite_leagues")
    fun getAllFavoriteLeagues(): Flow<List<FavoriteLeagueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteLeague(league: FavoriteLeagueEntity)

    @Delete
    suspend fun deleteFavoriteLeague(league: FavoriteLeagueEntity)

    @Query("SELECT COUNT(*) FROM favorite_leagues WHERE id = :leagueId")
    suspend fun isLeagueFavorite(leagueId: Int): Int
}
