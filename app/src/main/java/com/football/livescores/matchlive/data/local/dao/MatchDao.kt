package com.football.livescores.matchlive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.football.livescores.matchlive.data.local.entity.CachedMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM cached_matches ORDER BY apiOrder ASC")
    fun getAllCachedMatches(): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE statusShort = '1H' OR statusShort = '2H' OR statusShort = 'HT' OR statusShort = 'ET' OR statusShort = 'BT' OR statusShort = 'P' ORDER BY apiOrder ASC")
    fun getLiveCachedMatches(): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE queryDate = :dateStr ORDER BY apiOrder ASC")
    fun getCachedMatchesByQueryDate(dateStr: String): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE dateTimestamp >= :startSec AND dateTimestamp <= :endSec ORDER BY apiOrder ASC")
    fun getCachedMatchesByTimeRange(startSec: Long, endSec: Long): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE (dateTimestamp >= :startSec AND dateTimestamp <= :endSec) OR (statusShort IN ('1H', '2H', 'HT', 'ET', 'BT', 'P', 'INT', 'LIVE')) ORDER BY apiOrder ASC")
    fun getCachedMatchesForDateOrLive(startSec: Long, endSec: Long): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE id IN (:matchIds) ORDER BY apiOrder ASC")
    fun getMatchesByIds(matchIds: List<Int>): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches ORDER BY dateTimestamp DESC LIMIT :limit")
    fun getRecentCachedMatches(limit: Int = 300): Flow<List<CachedMatchEntity>>

    @Query("SELECT * FROM cached_matches WHERE id = :matchId")
    suspend fun getCachedMatchById(matchId: Int): CachedMatchEntity?

    @Query("DELETE FROM cached_matches WHERE dateTimestamp < :cutoffTimestamp")
    suspend fun pruneOldMatches(cutoffTimestamp: Long)

    @Query("DELETE FROM cached_matches WHERE statusShort IN ('1H', '2H', 'HT', 'ET', 'BT', 'P', 'INT', 'LIVE')")
    suspend fun clearLiveMatches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<CachedMatchEntity>)

    @Query("DELETE FROM cached_matches WHERE queryDate = :dateStr")
    suspend fun clearMatchesByQueryDate(dateStr: String)

    @Query("DELETE FROM cached_matches")
    suspend fun clearAllMatches()

    @Transaction
    suspend fun clearAndInsertMatches(matches: List<CachedMatchEntity>) {
        clearLiveMatches()
        insertMatches(matches)
    }

    @Transaction
    suspend fun clearAndInsertMatchesForDate(matches: List<CachedMatchEntity>, dateStr: String) {
        clearMatchesByQueryDate(dateStr)
        insertMatches(matches)
    }
}
