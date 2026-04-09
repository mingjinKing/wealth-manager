package com.wealth.manager.data

import android.util.Log
import com.wealth.manager.agent.LLMClient
import com.wealth.manager.agent.Message
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.entity.MemoryEntity
import com.wealth.manager.util.LogCollector
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一记忆提取器 - 重构版：严格区分长短期记忆
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val llmClient: LLMClient,
    private val memoryDao: MemoryDao,
    private val messageDao: MessageDao,
    private val embeddingService: EmbeddingService,
    private val memoryRetriever: MemoryRetriever
) {
    private val TAG = "MemoryExtractor"

    // 重新定义更清晰的字典
    val keyDictionary = mapOf(
        "personality" to "性格特征",
        "habit" to "长期习惯",
        "spending_pref" to "消费偏好",
        "investment_style" to "投资风格",
        "financial_status" to "当前财务状态",
        "life_goal" to "长期目标",
        "family_career" to "家庭背景",
        "current_plan" to "近期计划",
        "budget_limit" to "预算设定"
    )

    suspend fun extractIncremental(sessionId: String) {
        val messages = messageDao.getMessagesBySessionOnce(sessionId)
        if (messages.size < 2) return
        val lastTwo = messages.takeLast(2)
        val contextText = lastTwo.joinToString("\n") { 
            "${if (it.isUser) "用户" else "AI"}: ${it.content}" 
        }
        processContext(contextText)
    }

    suspend fun extractFull(sessionId: String) {
        val messages = messageDao.getMessagesBySessionOnce(sessionId)
        if (messages.isEmpty()) return
        val contextText = messages.joinToString("\n") { 
            "${if (it.isUser) "用户" else "AI"}: ${it.content}" 
        }
        processContext(contextText)
    }

    private suspend fun processContext(contextText: String) {
        val prompt = """
            你是一个专业的用户画像分析师。请分析对话内容，提取关于用户的记忆，并严格区分“长期记忆”与“短期记忆”。
            
            【定义】
            - 长期记忆 (is_long_term: true): 用户的性格、核心价值观、长期生活目标、根深蒂固的消费习惯、家庭/职业背景。这些信息通常在半年以上保持稳定。
            - 短期记忆 (is_long_term: false): 用户近期的具体打算、当月的财务数值、临时的情绪波动、针对某个特定事件的观点。
            
            【输出要求】
            返回 JSON 数组，格式如下：
            [
              {
                "key": "必须从这些中选择: personality, habit, spending_pref, investment_style, financial_status, life_goal, family_career, current_plan, budget_limit",
                "summary": "简练的中文描述",
                "is_long_term": true/false
              }
            ]
            
            对话内容：
            $contextText
            
            仅返回 JSON。
        """.trimIndent()

        try {
            val response = llmClient.chat(listOf(Message("user", prompt)))
            val content = when (response) {
                is com.wealth.manager.agent.LLMResponse.Text -> response.content
                else -> ""
            }

            if (content.isBlank()) return

            val jsonArray = try {
                val cleanJson = content.trim().removeSurrounding("```json", "```").trim()
                JSONArray(cleanJson)
            } catch (e: Exception) {
                Log.e(TAG, "解析 JSON 失败: ${e.message}")
                return
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val key = obj.getString("key")
                val summary = obj.getString("summary")
                val isLongTerm = obj.getBoolean("is_long_term")
                
                upsertMemory(key, summary, isLongTerm)
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取出错: ${e.message}")
        }
    }

    private suspend fun upsertMemory(key: String, summary: String, isLongTerm: Boolean) {
        val existingMemories = memoryDao.getAllMemoryOnce()
        val vector = embeddingService.embed(summary) ?: return

        var matchedId: String? = null
        for (mem in existingMemories) {
            val memVector = memoryRetriever.getVectorForMemory(mem.id)
            if (memVector != null) {
                val similarity = EmbeddingService.cosineSimilarity(vector, memVector)
                if (similarity > 0.85) {
                    matchedId = mem.id
                    break
                }
            }
        }

        val now = System.currentTimeMillis()
        val id = matchedId ?: UUID.randomUUID().toString()
        
        // 存储时明确写入 is_long_term 标记到 value 字段
        val entity = MemoryEntity(
            id = id,
            key = key,
            value = JSONObject().put("is_long_term", isLongTerm).toString(),
            summary = summary,
            source = "ai_analysis",
            confidence = 1.0f,
            createdAt = now,
            updatedAt = now
        )

        memoryDao.insert(entity)
        memoryRetriever.indexMessageVector(id, summary)
    }
}
