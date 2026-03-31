package com.wealth.manager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wealth.manager.data.entity.WeekStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekStatsDao {
    @Query("SELECT * FROM week_stats ORDER BY weekStartDate DESC")
    fun getAllWeekStats(): Flow<List<WeekStatsEntity>>

    @Query("SELECT * FROM week_stats WHERE weekStartDate = :weekStartDate")
    suspend fun getWeekStatsByStartDate(weekStartDate: Long): WeekStatsEntity?

    @Query("SELECT * FROM week_stats ORDER BY weekStartDate DESC LIMIT :limit")
    suspend fun getRecentWeekStats(limit: Int): List<WeekStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekStats(weekStats: WeekStatsEntity)

    @Update
    suspend fun updateWeekStats(weekStats: WeekStatsEntity)

    @Query("SELECT AVG(totalAmount) FROM week_stats ORDER BY weekStartDate DESC LIMIT :count")
    suspend fun getAverageLastWeeks(count: Int): Double?
}
