package com.wealth.manager.data

import com.wealth.manager.data.dao.ExtractedFactDao
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
    val memoryCount: Int,
    val factCount: Int
)

/**
 * 记忆重建器
 *
 * 清空 memory 表和 extracted_facts 表，
 * 从历史对话会话中重新提炼记忆，
 * 写入 memory 表（长期画像）和 extracted_facts 表（关键事实）。
 */
@Singleton
class MemoryRebuilder @Inject constructor(
    private val memoryDao: MemoryDao,
    private val extractedFactDao: ExtractedFactDao,
    private val sessionDao: SessionDao,
    private val factExtractor: FactExtractor,
    private val memoryRefiner: MemoryRefiner
) {
    private val TAG = "MemoryRebuilder"

    /**
     * 执行记忆重建
     * @return RebuildResult 重建结果
     */
    suspend fun rebuild(): RebuildResult = withContext(Dispatchers.IO) {
        LogCollector.i(TAG, "===== 开始记忆重建 =====")

        // 1. 清空所有记忆
        memoryDao.clearAllMemory()
        extractedFactDao.clearAll()
        LogCollector.i(TAG, "已清空 memory 表和 extracted_facts 表")

        // 2. 获取所有历史会话
        val sessions = sessionDao.getAllSessionsOnce()
        LogCollector.i(TAG, "找到 ${sessions.size} 个历史会话，开始重建...")

        // 3. 对每个会话重新提炼事实
        sessions.forEach { session ->
            try {
                factExtractor.extractFromConversation(session.id)
                LogCollector.d(TAG, "完成会话事实提取: ${session.title}")
            } catch (e: Exception) {
                LogCollector.e(TAG, "提取会话失败: ${session.id}")
            }
        }

        // 4. 提炼长期记忆画像
        try {
            memoryRefiner.refineMemory()
            LogCollector.i(TAG, "长期记忆画像提炼完成")
        } catch (e: Exception) {
            LogCollector.e(TAG, "长期记忆提炼失败")
        }

        // 5. 返回结果
        val memoryCount = memoryDao.getMemoryCount()
        val factCount = extractedFactDao.getFactCount()

        LogCollector.i(TAG, "===== 记忆重建完成: 处理${sessions.size}个会话, memory=${memoryCount}, facts=${factCount} =====")

        RebuildResult(
            sessionsProcessed = sessions.size,
            memoryCount = memoryCount,
            factCount = factCount
        )
    }
}
