package com.wealth.manager.data

import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.util.LogCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆重建结果
 */
data class RebuildResult(
    val sessionsProcessed: Int,
    val memoryCount: Int
)

/**
 * 记忆重建器 - 整合优化版
 */
@Singleton
class MemoryRebuilder @Inject constructor(
    private val memoryDao: MemoryDao,
    private val sessionDao: SessionDao,
    private val memoryExtractor: MemoryExtractor,
    private val memoryRetriever: MemoryRetriever // 注入检索器以清空索引
) {
    private val TAG = "MemoryRebuilder"

    suspend fun rebuild(): RebuildResult = withContext(Dispatchers.IO) {
        LogCollector.i(TAG, "===== 开始记忆全量重建 =====")

        // 1. 清空主数据库 memory 表
        memoryDao.clearAllMemory()
        
        // 2. 同步清空辅助数据库（FTS 索引和向量库）
        // 这解决了“主表空了但索引还在”导致的存储不一致问题
        memoryRetriever.clearAllIndex()
        
        LogCollector.i(TAG, "已重置所有记忆存储（Room + FTS + Vectors）")

        // 3. 获取所有历史会话
        val sessions = sessionDao.getAllSessionsOnce()
        LogCollector.i(TAG, "找到 ${sessions.size} 个历史会话，开始全量扫描...")

        // 4. 逐个会话提取记忆
        sessions.forEach { session ->
            try {
                memoryExtractor.extractFull(session.id)
                LogCollector.d(TAG, "完成会话 [${session.title}] 的记忆重建")
            } catch (e: Exception) {
                LogCollector.e(TAG, "会话 [${session.id}] 重建失败: ${e.message}")
            }
        }

        val memoryCount = memoryDao.getMemoryCount()
        LogCollector.i(TAG, "===== 记忆重建完成: 处理${sessions.size}个会话, 生成记忆条数=${memoryCount} =====")

        RebuildResult(
            sessionsProcessed = sessions.size,
            memoryCount = memoryCount
        )
    }
}
