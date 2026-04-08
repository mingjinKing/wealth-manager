package com.wealth.manager.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旺财 Micro Agent 核心类 - 时间感知与参数优化版
 */
@Singleton
class WangcaiAgent @Inject constructor(
    private val llmClient: LLMClient,
    private val toolRegistry: ToolRegistry,
    private val agentContext: AgentContext
) {

    companion object {
        private const val TAG = "WangcaiAgent"
        private const val MAX_TOOL_CALLS = 5
    }

    private fun getSystemPrompt(): String {
        val now = SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(Date())
        return """
你是旺财，一个专业且亲切的私人财务助手。
你的职责是基于用户的真实消费数据，提供精准的财务分析和温暖的建议。

## 当前环境：
- 今天是：$now
- **重要：查询特定月份账单时，请直接在 rule_engine 工具中使用 year 和 month 参数（如 year: 2026, month: 4），严禁自行计算毫秒时间戳，以免出错。**

## 行为准则：
1. **言简意赅**：核心结论置顶。严格控制字数在 300 字以内。
2. **禁止使用表格**：使用分点列表展示数据。
3. **启发追问**：根据用户的回答意图抛出一个追问。
4. **数据导向**：引用具体数据（如总支出、大额分类）。
5. **语气亲和**：保持朋友般的口吻。

直接切入主题，不要说废话。
        """.trimIndent()
    }

    private val messages = mutableListOf<Message>()

    fun clearContext() {
        Log.d(TAG, "clearContext")
        messages.clear()
    }

    fun thinkStream(
        userMessage: String,
        systemContext: String? = null
    ): Flow<String> = flow {
        if (messages.isEmpty()) {
            val content = buildString {
                append(getSystemPrompt())
                systemContext?.let { append("\n\n补充背景：\n$it") }
            }
            messages.add(Message(role = "system", content = content))
        }
        messages.add(Message(role = "user", content = userMessage))

        var remainDepth = MAX_TOOL_CALLS
        var finalResponseText: String? = null

        // 1. 工具调用循环 (静默执行)
        while (remainDepth > 0) {
            val response = try {
                llmClient.chat(messages.toList(), toolRegistry.manifest)
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed during tool loop", e)
                throw e
            }

            if (response is LLMResponse.ToolCall) {
                val progressDetail = parseToolProgress(response.toolName, response.arguments)
                emit("[PROGRESS: $progressDetail]")
                
                messages.add(Message(role = "assistant", content = response.content, toolCalls = response.fullToolCalls))
                val result = toolRegistry.execute(response.toolName, response.arguments)
                messages.add(Message(role = "tool", content = result, toolCallId = response.id))
                remainDepth--
            } else if (response is LLMResponse.Text) {
                finalResponseText = response.content
                break
            }
        }

        // 2. 模拟流式输出最终回复
        if (!finalResponseText.isNullOrEmpty()) {
            emit("[PROGRESS: 正在为您撰写专属财务报告...]")
            val chars = finalResponseText.toCharArray()
            for (char in chars) {
                emit(char.toString())
                delay(15)
            }
            messages.add(Message(role = "assistant", content = finalResponseText))
        } else {
            emit("[PROGRESS: 正在为您撰写专属财务报告...]")
            var fullText = ""
            llmClient.chatStream(messages.toList(), toolRegistry.manifest)
                .catch { e -> 
                    Log.e(TAG, "Stream failed", e)
                    emit("\n[生成中断: ${e.message}]") 
                }
                .collect { delta ->
                    fullText += delta
                    emit(delta)
                }
            if (fullText.isNotEmpty()) {
                messages.add(Message(role = "assistant", content = fullText))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseToolProgress(toolName: String, arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val op = json.optString("operation", "")
            when (toolName) {
                "rule_engine" -> when (op) {
                    "get_summary" -> "正在统计账务数据..."
                    "structure_analysis" -> "正在分析支出结构..."
                    else -> "正在调取核心账务数据..."
                }
                "context" -> "正在同步您的个人财务背景..."
                else -> "正在处理数据..."
            }
        } catch (e: Exception) {
            "正在处理数据..."
        }
    }
}
