package com.wealth.manager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wealth.manager.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getMessagesBySessionOnce(sessionId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 100): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE is_user = 1 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentUserMessages(limit: Int = 100): List<MessageEntity>
    
    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
    
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun getMessageCountBySession(sessionId: String): Int
}
