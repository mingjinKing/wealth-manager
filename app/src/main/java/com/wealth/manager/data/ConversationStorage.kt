package com.wealth.manager.data

import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.data.entity.MessageEntity
import com.wealth.manager.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 短期记忆存储 - 管理会话和消息
 * 
 * 负责：
 * 1. 会话的创建、更新、查询
 * 2. 消息的读写
 * 3. 历史消息的检索（供 MemoryRetriever 使用）
 */
@Singleton
class ConversationStorage @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    
    /**
     * 创建新会话
     */
    suspend fun createSession(title: String = ""): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifEmpty { "新会话" },
            createdAt = now,
            updatedAt = now
        )
        sessionDao.insert(session)
        return session
    }
    
    /**
     * 更新会话（更新标题、更新时间）
     */
    suspend fun updateSession(session: SessionEntity) {
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
    }
    
    /**
     * 获取会话
     */
    suspend fun getSession(sessionId: String): SessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }
    
    /**
     * 获取所有会话（一次性）
     */
    suspend fun getAllSessionsOnce(): List<SessionEntity> {
        return sessionDao.getAllSessionsOnce()
    }
    
    /**
     * 删除会话（级联删除消息）
     */
    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }
    
    /**
     * 添加消息到会话
     */
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
        
        // 更新会话的更新时间
        sessionDao.getSessionById(sessionId)?.let { session ->
            sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
        }
        
        return message
    }
    
    /**
     * 获取会话的所有消息
     */
    fun getMessages(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesBySession(sessionId)
    }
    
    /**
     * 获取会话的所有消息（一次性）
     */
    suspend fun getMessagesOnce(sessionId: String): List<MessageEntity> {
        return messageDao.getMessagesBySessionOnce(sessionId)
    }
    
    /**
     * 获取最近的用户消息（用于检索）
     */
    suspend fun getRecentUserMessages(limit: Int = 100): List<MessageEntity> {
        return messageDao.getRecentUserMessages(limit)
    }
    
    /**
     * 获取最近的所有消息（用于检索）
     */
    suspend fun getRecentMessages(limit: Int = 100): List<MessageEntity> {
        return messageDao.getRecentMessages(limit)
    }
    
    /**
     * 更新消息状态
     */
    suspend fun updateMessage(message: MessageEntity) {
        messageDao.update(message)
    }
    
    /**
     * 获取消息总数
     */
    suspend fun getMessageCount(): Int {
        return messageDao.getMessageCount()
    }
    
    /**
     * 获取会话总数
     */
    suspend fun getSessionCount(): Int {
        return sessionDao.getSessionCount()
    }
    
    /**
     * 生成会话标题（取第一条用户消息的前20字符）
     */
    fun generateSessionTitle(messages: List<MessageEntity>): String {
        return messages.firstOrNull { it.isUser }?.content
            ?.take(20)
            ?.replace("\n", " ")
            ?.ifEmpty { "新会话" }
            ?: "新会话"
    }
}
