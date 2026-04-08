package com.wealth.manager.data

import android.content.Context
import android.util.Log
import com.wealth.manager.data.dao.ExtractedFactDao
import com.wealth.manager.data.entity.ExtractedFactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关键信息提取器 - AI 自动从对话中提炼结构化事实
 *
 * 负责：
 * 1. 分析对话上下文
 * 2. 调用 AI 提取关键事实
 * 3. 写入 extracted_facts 表 + 向量
 */
@Singleton
class FactExtractor @Inject constructor(
    private val conversationStorage: ConversationStorage,
    private val extractedFactDao: ExtractedFactDao,
    private val memoryRetriever: MemoryRetriever,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FactExtractor"
        private const val MAX_CONTEXT_MESSAGES = 20  // 每次提取最多参考的消息数
        private const val MIN_CONFIDENCE = 0.6f      // 最低置信度阈值
    }

    /**
     * 提取的事实结构
     */
    data class ExtractedFact(
        val key: String,
        val value: String,
        val summary: String,
        val confidence: Float
    )

    /**
     * 从 AI 响应触发的事实提取
     * 在 AI 消息写入后异步调用，不阻塞主流程
     *
     * @param aiMessageContent AI 的回复内容
     * @param sessionId 当前会话 ID
     */
    fun extractFromAiResponse(aiMessageContent: String, sessionId: String) {
        if (aiMessageContent.isBlank()) return

        // 使用 CoroutineScope 异步执行，不阻塞主流程
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取最近对话上下文
                val messages = conversationStorage.getRecentMessages(MAX_CONTEXT_MESSAGES)
                    .filter { it.sessionId == sessionId || sessionId.isEmpty() }
                    .takeLast(MAX_CONTEXT_MESSAGES)

                if (messages.isEmpty()) {
                    Log.d(TAG, "无对话上下文，跳过提取")
                    return@launch
                }

                // 构建分析 prompt
                val prompt = buildExtractionPrompt(messages.map { it.content to it.isUser })

                // 调用 AI 提取
                val result = callAIExtract(prompt)
                if (result == null) {
                    Log.d(TAG, "AI 提取未返回结果")
                    return@launch
                }

                // 解析结果
                val facts = parseExtractionResult(result)
                if (facts.isEmpty()) {
                    Log.d(TAG, "未解析出有效事实")
                    return@launch
                }

                Log.d(TAG, "提取到 ${facts.size} 条事实")

                // 写入数据库
                val now = System.currentTimeMillis()
                val entities = facts.map { fact ->
                    ExtractedFactEntity(
                        id = UUID.randomUUID().toString(),
                        key = fact.key,
                        value = fact.value,
                        summary = fact.summary,
                        source = "auto_extract",
                        confidence = fact.confidence,
                        sourceMessageId = null,
                        expiresAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                // 批量写入并建立向量索引
                for (entity in entities) {
                    // 先写入 SQLite（insert 是 suspend 函数，但在 IO scope 内可调用）
                    extractedFactDao.insert(entity)

                    // 异步建立向量索引（launch 新协程避免阻塞）
                    launch {
                        try {
                            memoryRetriever.indexMessageVector(entity.id, entity.summary)
                        } catch (e: Exception) {
                            Log.e(TAG, "向量索引失败: ${e.message}")
                        }
                    }
                }

                Log.d(TAG, "事实写入完成: ${entities.size} 条")

            } catch (e: Exception) {
                Log.e(TAG, "事实提取失败: ${e.message}")
            }
        }
    }

    /**
     * 构建提取 prompt
     */
    private fun buildExtractionPrompt(messages: List<Pair<String, Boolean>>): String {
        val conversation = messages.joinToString("\n") { (content, isUser) ->
            val role = if (isUser) "用户" else "AI"
            "$role: $content"
        }

        return """
你是一个精准的信息提取助手。请从以下对话中提取用户透露的关键事实信息。

提取原则：
1. 只提取用户明确表达的事实，不要推测
2. key 命名格式：类型_具体内容（如 budget_clothes、income_salary、goal_house）
3. value 使用 JSON 格式，包含具体数值或内容
4. summary 是人类可读的短句，20字以内
5. confidence 0-1，表示你对这条事实的确定程度
6. 只提取与财务相关的信息：收入、支出、预算、目标、偏好、决定等

输出格式（必须是合法 JSON）：
{
  "facts": [
    {
      "key": "budget_monthly_clothes",
      "value": "{\"amount\": 1000, \"currency\": \"CNY\", \"frequency\": \"monthly\"}",
      "summary": "用户每月买衣服预算1000元",
      "confidence": 0.9
    }
  ]
}

如果对话中没有有价值的事实，返回空数组：
{"facts": []}

对话内容：
$conversation
""".trimIndent()
    }

    /**
     * 调用 AI 提取
     */
    private fun callAIExtract(prompt: String): String? {
        return try {
            val apiKey = getSecureApiKey()
            if (apiKey.isEmpty()) return null

            val jsonBody = JSONObject().apply {
                put("model", "doubao-pro-32k")
                put("max_tokens", 800)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://ark.cn-beijing.volces.com/api/v3/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            response.close()

            val responseObj = JSONObject(bodyStr)
            val choices = responseObj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI 调用失败: ${e.message}")
            null
        }
    }

    /**
     * 解析 AI 返回的提取结果
     */
    private fun parseExtractionResult(result: String): List<ExtractedFact> {
        return try {
            val jsonStr = result
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(jsonStr)
            val factsArray = json.optJSONArray("facts") ?: return emptyList()

            val facts = mutableListOf<ExtractedFact>()
            for (i in 0 until factsArray.length()) {
                val obj = factsArray.getJSONObject(i)
                val key = obj.optString("key") ?: continue
                val value = obj.optString("value", "{}")
                val summary = obj.optString("summary", "")
                val confidence = obj.optDouble("confidence", 0.0).toFloat()

                // 过滤低置信度
                if (summary.isNotEmpty() && confidence >= MIN_CONFIDENCE) {
                    facts.add(ExtractedFact(
                        key = key,
                        value = value,
                        summary = summary,
                        confidence = confidence
                    ))
                }
            }
            facts
        } catch (e: Exception) {
            Log.e(TAG, "解析失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取安全存储的 API Key
     */
    private fun getSecureApiKey(): String {
        return try {
            val securePrefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
            securePrefs.getString("llm_api_key", "") ?: ""
        } catch (e: Exception) { "" }
    }

    /**
     * 获取所有有效事实（用于注入 context）
     */
    suspend fun getValidFacts(): List<ExtractedFactEntity> {
        return extractedFactDao.getValidFacts()
    }

    /**
     * 按 key 前缀搜索事实
     */
    suspend fun searchFactsByKeyPrefix(prefix: String): List<ExtractedFactEntity> {
        return extractedFactDao.getFactsByKeyPrefix(prefix)
    }

    /**
     * 构建记忆摘要（用于 system prompt）
     */
    suspend fun buildFactsSummary(): String {
        val facts = getValidFacts()
        if (facts.isEmpty()) return ""

        return buildString {
            appendLine()
            appendLine("=== 用户事实（参考）===")
            facts.forEach { fact ->
                appendLine("- ${fact.summary}")
            }
        }
    }
}
