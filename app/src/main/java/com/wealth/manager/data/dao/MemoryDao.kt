package com.wealth.manager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wealth.manager.data.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)
    
    @Update
    suspend fun update(memory: MemoryEntity)
    
    @Query("SELECT * FROM memory ORDER BY updated_at DESC")
    fun getAllMemory(): Flow<List<MemoryEntity>>
    
    @Query("SELECT * FROM memory ORDER BY updated_at DESC")
    suspend fun getAllMemoryOnce(): List<MemoryEntity>
    
    @Query("SELECT * FROM memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?
    
    @Query("SELECT * FROM memory WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): MemoryEntity?
    
    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun deleteMemory(id: String)
    
    @Query("DELETE FROM memory")
    suspend fun clearAllMemory()
    
    @Query("SELECT COUNT(*) FROM memory")
    suspend fun getMemoryCount(): Int
    
    /**
     * 获取所有记忆键（用于去重）
     */
    @Query("SELECT DISTINCT `key` FROM memory")
    suspend fun getAllMemoryKeys(): List<String>
}
