package com.wealth.manager.data

import android.content.Context
import com.wealth.manager.agent.LLMClient
import com.wealth.manager.agent.LLMResponse
import com.wealth.manager.agent.Message
import com.wealth.manager.data.dao.ExtractedFactDao
import com.wealth.manager.data.entity.ExtractedFactEntity
import com.wealth.manager.util.LogCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关键信息提取器 - LLMClient 复用加固版
 */
@Singleton
class FactExtractor @Inject constructor(
    private val conversationStorage: ConversationStorage,
    private val extractedFactDao: ExtractedFactDao,
    private val memoryRetriever: MemoryRetriever,
    private val llmClient: LLMClient, // 统一使用注入的 LLMClient
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FactExtractor"
        private const val DEBOUNCE_MS = 3000L 
    }

    private var extractionJob: Job? = null

    data class ExtractedFact(
        val key: String,
        val summary: String,
        val confidence: Float
    )

    fun extractFromConversation(sessionId: String) {
        extractionJob?.cancel()
        extractionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(DEBOUNCE_MS)
                
                val messages = conversationStorage.getRecentMessages(10)
                    .filter { it.sessionId == sessionId }
                
                if (messages.size < 2) return@launch

                val contextString = messages.joinToString("\n") { 
                    "${if (it.isUser) "用户" else "旺财"}: ${it.content}"
                }

                val prompt = """
                    你是一个财务数据提取助手。请从对话中提取关键的财务事实（如：目标、预算、偏好）。
                    
                    输出要求：
                    1. 描述精简（20字内）。
                    2. 每行一个事实。
                    3. 如果没发现事实，直接回复 "NONE"。
                    
                    对话内容：
                    $contextString
                """.trimIndent()

                // 复用主对话的 chat 逻辑
                val response = llmClient.chat(listOf(Message(role = "user", content = prompt)))
                
                if (response is LLMResponse.Text) {
                    val result = response.content
                    android.util.Log.i(TAG, "AI 提炼响应: $result")

                    if (result.trim().uppercase() == "NONE") return@launch

                    val facts = parseExtractionResult(result)
                    facts.forEach { fact ->
                        val stableId = UUID.nameUUIDFromBytes(fact.summary.toByteArray()).toString()
                        if (extractedFactDao.getFactById(stableId) == null) {
                            val entity = ExtractedFactEntity(
                                id = stableId,
                                key = fact.key,
                                value = "{}",
                                summary = fact.summary,
                                source = "auto_extract",
                                confidence = fact.confidence,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            extractedFactDao.insert(entity)
                            memoryRetriever.indexMessageVector(entity.id, entity.summary)
                            LogCollector.i(TAG, "【记忆沉淀成功】: ${fact.summary}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) android.util.Log.e(TAG, "提炼出错", e)
            }
        }
    }

    private fun parseExtractionResult(result: String): List<ExtractedFact> {
        return result.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("NONE") && it.length > 2 }
            .map { 
                val clean = it.replace(Regex("^(?i)FACT:\\s*|^-?\\s*"), "")
                ExtractedFact("user_fact", clean, 0.95f)
            }
    }

    suspend fun buildFactsSummary(): String {
        val facts = extractedFactDao.getValidFacts()
        if (facts.isEmpty()) return ""
        return "=== 历史事实背景 ===\n" + facts.joinToString("\n") { "- ${it.summary}" }
    }
}
