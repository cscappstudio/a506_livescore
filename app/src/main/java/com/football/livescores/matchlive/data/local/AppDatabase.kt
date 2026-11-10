package com.football.livescores.matchlive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.football.livescores.matchlive.data.local.dao.FavoriteDao
import com.football.livescores.matchlive.data.local.dao.MatchDao
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteLeagueEntity
import com.football.livescores.matchlive.data.local.entity.FavoriteTeamEntity

@Database(
    entities = [
        FavoriteTeamEntity::class,
        FavoriteLeagueEntity::class,
        CachedMatchEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun matchDao(): MatchDao
}
