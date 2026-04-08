package com.wealth.manager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wealth.manager.data.entity.ExtractedFactEntity
import kotlinx.coroutines.flow.Flow

/**
 * 关键信息提取 DAO
 */
@Dao
interface ExtractedFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: ExtractedFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facts: List<ExtractedFactEntity>)

    @Update
    suspend fun update(fact: ExtractedFactEntity)

    /**
     * 获取所有事实
     */
    @Query("SELECT * FROM extracted_facts ORDER BY updated_at DESC")
    fun getAllFacts(): Flow<List<ExtractedFactEntity>>

    /**
     * 获取所有事实（一次性）
     */
    @Query("SELECT * FROM extracted_facts ORDER BY updated_at DESC")
    suspend fun getAllFactsOnce(): List<ExtractedFactEntity>

    /**
     * 按 key 获取单条事实
     */
    @Query("SELECT * FROM extracted_facts WHERE fact_key = :key LIMIT 1")
    suspend fun getFactByKey(key: String): ExtractedFactEntity?

    /**
     * 按 key 前缀搜索事实（用于模糊匹配）
     */
    @Query("SELECT * FROM extracted_facts WHERE fact_key LIKE :prefix || '%' ORDER BY updated_at DESC")
    suspend fun getFactsByKeyPrefix(prefix: String): List<ExtractedFactEntity>

    /**
     * 按 ID 获取
     */
    @Query("SELECT * FROM extracted_facts WHERE id = :id LIMIT 1")
    suspend fun getFactById(id: String): ExtractedFactEntity?

    /**
     * 删除单条
     */
    @Query("DELETE FROM extracted_facts WHERE id = :id")
    suspend fun deleteFact(id: String)

    /**
     * 清空所有
     */
    @Query("DELETE FROM extracted_facts")
    suspend fun clearAll()

    /**
     * 获取总数
     */
    @Query("SELECT COUNT(*) FROM extracted_facts")
    suspend fun getFactCount(): Int

    /**
     * 获取所有 key（用于去重判断）
     */
    @Query("SELECT DISTINCT fact_key FROM extracted_facts")
    suspend fun getAllFactKeys(): List<String>

    /**
     * 获取未过期的有效事实
     */
    @Query("SELECT * FROM extracted_facts WHERE (expires_at IS NULL OR expires_at > :now) ORDER BY updated_at DESC")
    suspend fun getValidFacts(now: Long = System.currentTimeMillis()): List<ExtractedFactEntity>

    /**
     * 删除已过期的事实
     */
    @Query("DELETE FROM extracted_facts WHERE expires_at IS NOT NULL AND expires_at < :now")
    suspend fun deleteExpiredFacts(now: Long = System.currentTimeMillis()): Int
}
