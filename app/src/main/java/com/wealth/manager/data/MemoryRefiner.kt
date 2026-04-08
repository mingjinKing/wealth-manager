package com.wealth.manager.data

import android.util.Log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 长期记忆提炼器 - AI 分析对话历史，提取结构化洞察
 * 
 * 负责：
 * 1. 分析用户对话历史
 * 2. 提取结构化画像（消费习惯、财务偏好等）
 * 3. 存储到 memory 表
 */
@Singleton
class MemoryRefiner @Inject constructor(
    private val conversationStorage: ConversationStorage,
    private val memoryDao: com.wealth.manager.data.dao.MemoryDao,
    @ApplicationContext private val context: Context
) {
    
    /**
     * 记忆项
     */
    data class MemoryItem(
        val key: String,
        val value: String,
        val summary: String,
        val source: String = "ai_analysis",
        val confidence: Float = 0.8f
    )
    
    /**
     * 执行记忆提炼
     * 分析最近的对话，提取新的洞察，更新 memory 表
     * 
     * @param forceRefine 是否强制重新分析（忽略缓存）
     */
    suspend fun refineMemory(forceRefine: Boolean = false) {
        // 获取最近的对话
        val recentMessages = conversationStorage.getRecentUserMessages(limit = 50)
        android.util.Log.d("MemoryRefiner", "提炼记忆: 找到 ${recentMessages.size} 条用户消息")
        if (recentMessages.isEmpty()) {
            android.util.Log.d("MemoryRefiner", "提炼失败: 没有用户消息")
            return
        }
        
        // 构建分析 prompt
        val prompt = buildAnalysisPrompt(recentMessages.map { it.content })
        
        // 调用 AI 分析
        val analysisResult = callAIAnalysis(prompt)
        if (analysisResult == null) {
            android.util.Log.d("MemoryRefiner", "提炼失败: AI 未返回结果")
            return
        }
        android.util.Log.d("MemoryRefiner", "AI 返回: ${analysisResult.take(200)}")
        
        // 解析 AI 返回的结构化洞察
        val memories = parseAnalysisResult(analysisResult)
        android.util.Log.d("MemoryRefiner", "解析出 ${memories.size} 条记忆")
        if (memories.isEmpty()) return
        
        // 合并到 memory 表
        mergeMemories(memories)
        android.util.Log.d("MemoryRefiner", "提炼成功: ${memories.size} 条记忆已合并")
    }
    
    /**
     * 获取当前所有记忆（用于注入到 system prompt）
     */
    suspend fun getAllMemories(): List<MemoryItem> {
        return memoryDao.getAllMemoryOnce().map { entity ->
            MemoryItem(
                key = entity.key,
                value = entity.value,
                summary = entity.summary,
                source = entity.source,
                confidence = entity.confidence
            )
        }
    }
    
    /**
     * 构建记忆摘要（用于 system prompt）
     */
    suspend fun buildMemorySummary(): String {
        val memories = getAllMemories()
        if (memories.isEmpty()) return ""
        
        return buildString {
            appendLine()
            appendLine("=== 用户画像（参考）===")
            memories.forEach { mem ->
                appendLine("- ${mem.summary}")
            }
        }
    }
    
    /**
     * 构建分析 prompt
     */
    private fun buildAnalysisPrompt(messages: List<String>): String {
        return """
你是一个专业的财务助手。请分析以下用户对话，提取结构化的用户画像信息。

分析维度：
1. 消费习惯：用户关注哪些消费领域（投资/大件/日常/旅行/教育等）
2. 财务成熟度：用户对财务规划的理解水平
3. 决策风格：用户在消费决策时的特点（谨慎型/冲动型/计划型）
4. 关注重点：用户最关心财务哪些方面
5. 消费态度：用户对消费的态度（节约/享受/平衡）

请以 JSON 格式返回分析结果：
{
  "memories": [
    {
      "key": "spending_habit",
      "value": "{\"domains\": [\"投资\", \"大件消费\"], \"frequency\": \"中等\"}",
      "summary": "用户主要关注投资和大件消费领域",
      "confidence": 0.85
    }
  ]
}

注意：
- 只提取对话中明确体现的特质，不要过度推断
- confidence 表示置信度，0-1 之间
- summary 要是人类可读的短句，20字以内
- 如果没有足够信息判断某项，请跳过该项

用户对话：
${messages.take(30).joinToString("\n")}
""".trimIndent()
    }
    
    /**
     * 调用 AI 进行分析
     */
    private suspend fun callAIAnalysis(prompt: String): String? {
        return try {
            val apiKey = getSecureApiKey()
            if (apiKey.isEmpty()) return null
            
            val jsonBody = JSONObject().apply {
                put("model", "doubao-pro-32k")
                put("max_tokens", 500)
                put("messages", JSONArray().apply {
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
            null
        }
    }
    
    /**
     * 解析 AI 分析结果
     */
    private fun parseAnalysisResult(result: String): List<MemoryItem> {
        return try {
            // 提取 JSON 部分（可能包含在 markdown 代码块中）
            val jsonStr = result
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            val json = JSONObject(jsonStr)
            val memoriesArray = json.optJSONArray("memories") ?: return emptyList()
            
            val memories = mutableListOf<MemoryItem>()
            for (i in 0 until memoriesArray.length()) {
                val memObj = memoriesArray.getJSONObject(i)
                val key = memObj.optString("key") ?: continue
                val value = memObj.optString("value", "{}")
                val summary = memObj.optString("summary", "")
                val confidence = memObj.optDouble("confidence", 0.5).toFloat()
                
                if (summary.isNotEmpty()) {
                    memories.add(MemoryItem(
                        key = key,
                        value = value,
                        summary = summary,
                        confidence = confidence
                    ))
                }
            }
            memories
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 合并记忆到数据库（更新已存在的，插入新的）
     */
    private suspend fun mergeMemories(newMemories: List<MemoryItem>) {
        val now = System.currentTimeMillis()
        
        newMemories.forEach { mem ->
            val existing = memoryDao.getMemoryByKey(mem.key)
            if (existing != null) {
                // 更新已存在的记忆（取置信度更高的）
                if (mem.confidence > existing.confidence) {
                    memoryDao.update(existing.copy(
                        value = mem.value,
                        summary = mem.summary,
                        confidence = mem.confidence,
                        updatedAt = now
                    ))
                }
            } else {
                // 插入新记忆
                memoryDao.insert(com.wealth.manager.data.entity.MemoryEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    key = mem.key,
                    value = mem.value,
                    summary = mem.summary,
                    source = mem.source,
                    confidence = mem.confidence,
                    createdAt = now,
                    updatedAt = now
                ))
            }
        }
    }
    
    /**
     * 获取安全的 API Key
     */
    private fun getSecureApiKey(): String {
        return try {
            val securePrefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
            securePrefs.getString("llm_api_key", "") ?: ""
        } catch (e: Exception) { "" }
    }
}
