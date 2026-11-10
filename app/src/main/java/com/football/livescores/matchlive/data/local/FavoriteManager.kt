package com.football.livescores.matchlive.data.local

import android.content.Context
import android.content.SharedPreferences
import com.football.livescores.matchlive.data.local.dao.FavoriteDao
import com.football.livescores.matchlive.data.local.entity.FavoriteLeagueEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteTeamEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val limitManager: RequestLimitManager
) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("livescore_favorites_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITE_FIXTURES = "favorite_fixtures"
        private const val KEY_FAVORITE_PLAYERS = "favorite_players"
    }

    // --- Matches/Fixtures (using SharedPreferences for lightweight sync state) ---
    
    fun getFavoritePlayerIds(): Set<String> {
        return sharedPrefs.getStringSet(KEY_FAVORITE_PLAYERS, emptySet()) ?: emptySet()
    }

    fun togglePlayerFavorite(playerId: Int): Boolean {
        val favorites = getFavoritePlayerIds().toMutableSet()
        val idString = playerId.toString()
        val isNowFavorite = if (favorites.contains(idString)) {
            favorites.remove(idString)
            false
        } else {
            favorites.add(idString)
            true
        }
        sharedPrefs.edit().putStringSet(KEY_FAVORITE_PLAYERS, favorites).apply()
        return isNowFavorite
    }

    fun isFixtureFavorite(fixtureId: Int): Boolean {
        val favorites = getFavoriteFixtureIds()
        return favorites.contains(fixtureId.toString())
    }

    fun canAddFavoriteFixture(fixtureId: Int): Boolean {
        if (limitManager.isPremium()) return true
        val favorites = getFavoriteFixtureIds()
        // If already in favorites, toggling it will remove it, so it's always allowed
        if (favorites.contains(fixtureId.toString())) return true
        // Free user can only follow up to 7 fixtures
        return favorites.size < 7
    }

    fun toggleFixtureFavorite(fixtureId: Int): Boolean {
        val favorites = getFavoriteFixtureIds().toMutableSet()
        val idString = fixtureId.toString()
        val isNowFavorite = if (favorites.contains(idString)) {
            favorites.remove(idString)
            false
        } else {
            favorites.add(idString)
            true
        }
        sharedPrefs.edit().putStringSet(KEY_FAVORITE_FIXTURES, favorites).apply()
        return isNowFavorite
    }

    fun getFavoriteFixtureIds(): Set<String> {
        return sharedPrefs.getStringSet(KEY_FAVORITE_FIXTURES, emptySet()) ?: emptySet()
    }

    // --- Teams (using Room FavoriteDao) ---

    fun getAllFavoriteTeams(): Flow<List<FavoriteTeamEntity>> {
        return favoriteDao.getAllFavoriteTeams()
    }

    suspend fun isTeamFavorite(teamId: Int): Boolean {
        return favoriteDao.isTeamFavorite(teamId) > 0
    }

    suspend fun toggleTeamFavorite(id: Int, name: String, logo: String): Boolean {
        val isFav = favoriteDao.isTeamFavorite(id) > 0
        if (isFav) {
            favoriteDao.deleteFavoriteTeam(FavoriteTeamEntity(id, name, logo))
            return false
        } else {
            favoriteDao.insertFavoriteTeam(FavoriteTeamEntity(id, name, logo))
            return true
        }
    }

    // --- Leagues (using Room FavoriteDao) ---

    fun getAllFavoriteLeagues(): Flow<List<FavoriteLeagueEntity>> {
        return favoriteDao.getAllFavoriteLeagues()
    }

    suspend fun isLeagueFavorite(leagueId: Int): Boolean {
        return favoriteDao.isLeagueFavorite(leagueId) > 0
    }

    suspend fun toggleLeagueFavorite(id: Int, name: String, logo: String, country: String): Boolean {
        val isFav = favoriteDao.isLeagueFavorite(id) > 0
        if (isFav) {
            favoriteDao.deleteFavoriteLeague(FavoriteLeagueEntity(id, name, logo, country))
            return false
        } else {
            favoriteDao.insertFavoriteLeague(FavoriteLeagueEntity(id, name, logo, country))
            return true
        }
    }
}
