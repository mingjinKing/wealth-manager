package com.wealth.manager.data

import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.data.entity.MessageEntity
import com.wealth.manager.data.entity.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 短期记忆存储 - 管理会话和消息
 */
@Singleton
class ConversationStorage @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val memoryRetriever: MemoryRetriever,
    private val memoryExtractorProvider: Provider<MemoryExtractor> // 修正：使用 MemoryExtractor
) {
    
    suspend fun createSession(sessionId: String = UUID.randomUUID().toString(), title: String = ""): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = sessionId,
            title = title.ifEmpty { "新会话" },
            createdAt = now,
            updatedAt = now
        )
        sessionDao.insert(session)
        return session
    }
    
    suspend fun updateSession(session: SessionEntity) {
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun getSession(sessionId: String): SessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }
    
    fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }
    
    suspend fun getAllSessionsOnce(): List<SessionEntity> {
        return sessionDao.getAllSessionsOnce()
    }
    
    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }
    
    suspend fun addMessage(
        sessionId: String,
        content: String,
        isUser: Boolean,
        isUseful: Boolean = false,
        isLiked: Boolean = false
    ): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            isUser = isUser,
            content = content,
            createdAt = System.currentTimeMillis(),
            isUseful = isUseful,
            isLiked = isLiked
        )
        messageDao.insert(message)
        
        sessionDao.getSessionById(sessionId)?.let { session ->
            sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                memoryRetriever.indexMessageVector(message.id, content)
            } catch (e: Exception) {
                android.util.Log.e("ConversationStorage", "向量索引失败: ${e.message}")
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                memoryRetriever.indexMessage(message.id, sessionId, content, isUser, message.createdAt)
            } catch (e: Exception) {
                android.util.Log.e("ConversationStorage", "FTS 索引失败: ${e.message}")
            }
        }

        // 统一钩子：AI 消息返回后触发增量提取
        if (!isUser && content.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 修正：调用 MemoryExtractor 进行增量提取
                    memoryExtractorProvider.get().extractIncremental(sessionId)
                } catch (e: Exception) {
                    android.util.Log.e("ConversationStorage", "记忆提取失败: ${e.message}")
                }
            }
        }

        return message
    }
    
    fun getMessages(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesBySession(sessionId)
    }
    
    suspend fun getMessagesOnce(sessionId: String): List<MessageEntity> {
        return messageDao.getMessagesBySessionOnce(sessionId)
    }
    
    suspend fun getRecentUserMessages(limit: Int = 100): List<MessageEntity> {
        return messageDao.getRecentUserMessages(limit)
    }
    
    suspend fun getRecentMessages(limit: Int = 100): List<MessageEntity> {
        return messageDao.getRecentMessages(limit)
    }
    
    suspend fun updateMessage(message: MessageEntity) {
        messageDao.update(message)
    }
    
    suspend fun getMessageCount(): Int {
        return messageDao.getMessageCount()
    }

    suspend fun getMessageCountBySession(sessionId: String): Int {
        return messageDao.getMessageCountBySession(sessionId)
    }
    
    suspend fun getSessionCount(): Int {
        return sessionDao.getSessionCount()
    }
    
    suspend fun migrateHistoricalMessagesVectors(
        progressCallback: ((current: Int, total: Int) -> Unit)? = null
    ): Int {
        val allMessages = messageDao.getRecentMessages(limit = 10000)
            .filter { it.isUser && it.content.isNotBlank() }
        
        if (allMessages.isEmpty()) return 0
        
        var successCount = 0
        val total = allMessages.size
        
        for ((index, message) in allMessages.withIndex()) {
            try {
                val hasVector = memoryRetriever.hasVectorForMessage(message.id)
                if (!hasVector) {
                    val indexed = memoryRetriever.indexMessageVector(message.id, message.content)
                    if (indexed) successCount++
                }
            } catch (e: Exception) {
                android.util.Log.e("ConversationStorage", "迁移消息向量失败: ${e.message}")
            }
            progressCallback?.invoke(index + 1, total)
        }
        
        return successCount
    }
    
    fun generateSessionTitle(messages: List<MessageEntity>): String {
        return messages.firstOrNull { it.isUser }?.content
            ?.take(20)
            ?.replace("\n", " ")
            ?.ifEmpty { "新会话" }
            ?: "新会话"
    }
}
