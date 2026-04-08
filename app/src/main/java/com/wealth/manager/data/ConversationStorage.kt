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
 * 
 * 负责：
 * 1. 会话的创建、更新、查询
 * 2. 消息的读写
 * 3. 历史消息的检索（供 MemoryRetriever 使用）
 */
@Singleton
class ConversationStorage @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val memoryRetriever: MemoryRetriever,
    private val factExtractorProvider: Provider<FactExtractor>  // 懒加载，避免循环依赖
) {
    
    /**
     * 创建新会话
     */
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
        
        // 异步生成并存储向量（用于记忆检索）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                memoryRetriever.indexMessageVector(message.id, content)
            } catch (e: Exception) {
                // 向量生成失败不影响主流程
                android.util.Log.e("ConversationStorage", "向量索引失败: ${e.message}")
            }
        }
        
        // 异步索引到 FTS/fallback 表（用于关键词搜索）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                memoryRetriever.indexMessage(message.id, sessionId, content, isUser, message.createdAt)
            } catch (e: Exception) {
                android.util.Log.e("ConversationStorage", "FTS 索引失败: ${e.message}")
            }
        }

        // AI 消息写入后，触发关键信息提取（异步，不阻塞）
        if (!isUser && content.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 核心改进：统一使用更强大的 extractFromConversation 接口
                    factExtractorProvider.get().extractFromConversation(sessionId)
                } catch (e: Exception) {
                    android.util.Log.e("ConversationStorage", "事实提取触发失败: ${e.message}")
                }
            }
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
     * 迁移历史消息的向量索引
     * 一次性处理所有历史用户消息，生成并存储向量
     * @param progressCallback 每处理完一条回调一次，用于 UI 进度显示
     * @return 成功处理的条数
     */
    suspend fun migrateHistoricalMessagesVectors(
        progressCallback: ((current: Int, total: Int) -> Unit)? = null
    ): Int {
        // 获取所有历史消息（一次性）
        val allMessages = messageDao.getRecentMessages(limit = 10000)
            .filter { it.isUser && it.content.isNotBlank() }
        
        if (allMessages.isEmpty()) return 0
        
        var successCount = 0
        val total = allMessages.size
        
        for ((index, message) in allMessages.withIndex()) {
            try {
                // 检查是否已有向量（幂等）
                val hasVector = memoryRetriever.hasVectorForMessage(message.id)
                if (!hasVector) {
                    val indexed = memoryRetriever.indexMessageVector(message.id, message.content)
                    if (indexed) successCount++
                } else {
                    // 已有向量，跳过
                }
            } catch (e: Exception) {
                android.util.Log.e("ConversationStorage", "迁移消息向量失败: ${e.message}")
            }
            
            // 回调查询进度
            progressCallback?.invoke(index + 1, total)
        }
        
        android.util.Log.d("ConversationStorage", "历史消息向量迁移完成: $successCount/$total")
        return successCount
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
