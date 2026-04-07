package com.wealth.manager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wealth.manager.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)
    
    @Update
    suspend fun update(session: SessionEntity)
    
    @Query("SELECT * FROM sessions ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions ORDER BY updated_at DESC")
    suspend fun getAllSessionsOnce(): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int
}
