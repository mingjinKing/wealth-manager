package com.wealth.manager.data

import android.content.Context
import com.wealth.manager.agent.LLMClient
import com.wealth.manager.agent.LLMResponse
import com.wealth.manager.agent.Message
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.entity.MemoryEntity
import com.wealth.manager.util.LogCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 长期记忆提炼器 - 语义去重优化版
 */
@Singleton
class MemoryRefiner @Inject constructor(
    private val conversationStorage: ConversationStorage,
    private val memoryDao: MemoryDao,
    private val llmClient: LLMClient,
    private val embeddingService: EmbeddingService,
    private val memoryRetriever: MemoryRetriever, // 用于同步更新向量索引
    @ApplicationContext private val context: Context
) {
    private val TAG = "MemoryRefiner"
    private val SIMILARITY_THRESHOLD = 0.8f

    data class MemoryItem(
        val key: String,
        val summary: String,
        val confidence: Float = 0.8f
    )

    suspend fun refineMemory() {
        val recentMessages = conversationStorage.getRecentUserMessages(limit = 30)
        if (recentMessages.isEmpty()) return
        
        LogCollector.d(TAG, "开始提炼长期记忆: 找到 ${recentMessages.size} 条用户消息")
        
        val prompt = """
            你是一个专业的财务心理专家。请分析以下用户的对话，提炼出关于用户的财务画像洞察。
            
            要求：
            1. 只提炼长期稳定的特征，不要记录具体的某笔消费事实。
            2. 必须给出具体的分类 KEY（如：spending_style, risk_preference, budget_habit, focus_area）。
            3. 每条洞察格式： [KEY] 描述文字。描述精简（20字以内）。
            
            对话内容：
            ${recentMessages.reversed().joinToString("\n") { it.content }}
        """.trimIndent()

        try {
            val response = llmClient.chat(listOf(Message(role = "user", content = prompt)))
            if (response is LLMResponse.Text) {
                val items = parseAnalysisResult(response.content)
                if (items.isNotEmpty()) {
                    mergeWithSemanticCheck(items)
                    LogCollector.i(TAG, "成功处理 ${items.size} 条长期画像洞察")
                }
            }
        } catch (e: Exception) {
            LogCollector.e(TAG, "提炼调用失败: ${e.message}")
        }
    }

    private fun parseAnalysisResult(result: String): List<MemoryItem> {
        val items = mutableListOf<MemoryItem>()
        val regex = Regex("""\[(.*?)\]\s*(.*)""")
        result.lines().forEach { line ->
            regex.find(line)?.let { match ->
                val key = match.groupValues[1].trim()
                val summary = match.groupValues[2].trim()
                if (summary.isNotEmpty()) {
                    items.add(MemoryItem(key = key, summary = summary))
                }
            }
        }
        return items
    }

    private suspend fun mergeWithSemanticCheck(newItems: List<MemoryItem>) = withContext(Dispatchers.IO) {
        val existingMemories = memoryDao.getAllMemoryOnce()
        val now = System.currentTimeMillis()

        for (newItem in newItems) {
            val newItemVector = embeddingService.embed(newItem.summary) ?: continue
            
            // 寻找同 Key 且语义最相似的既有记忆
            var bestMatch: MemoryEntity? = null
            var maxSim = 0f

            existingMemories.filter { it.key == newItem.key }.forEach { existing ->
                // 这里可以通过本地缓存向量来加速，暂时直接计算
                val existingVector = memoryRetriever.getVectorForMemory(existing.id)
                if (existingVector != null) {
                    val sim = EmbeddingService.cosineSimilarity(newItemVector, existingVector)
                    if (sim > maxSim) {
                        maxSim = sim
                        bestMatch = existing
                    }
                }
            }

            if (bestMatch != null && maxSim >= SIMILARITY_THRESHOLD) {
                // 相似度达标，覆盖更新
                LogCollector.d(TAG, "发现重复记忆 (相似度 ${String.format("%.2f", maxSim)}), 执行覆盖: ${newItem.summary}")
                val updated = bestMatch!!.copy(
                    summary = newItem.summary, // 使用最新的表述
                    updatedAt = now,
                    confidence = maxOf(bestMatch!!.confidence, newItem.confidence)
                )
                memoryDao.update(updated)
                // 更新向量索引
                memoryRetriever.indexMessageVector(updated.id, updated.summary)
            } else {
                // 全新记忆，插入
                val newId = UUID.randomUUID().toString()
                val entity = MemoryEntity(
                    id = newId,
                    key = newItem.key,
                    value = "{}",
                    summary = newItem.summary,
                    source = "ai_analysis",
                    confidence = newItem.confidence,
                    createdAt = now,
                    updatedAt = now
                )
                memoryDao.insert(entity)
                memoryRetriever.indexMessageVector(newId, newItem.summary)
                LogCollector.d(TAG, "记录新记忆: ${newItem.summary}")
            }
        }
    }
}
