package com.wealth.manager.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旺财 Micro Agent 核心类 - 极致用户体验版
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
        private const val MODEL_NAME = "wangcai"
        private const val MODEL_VERSION = "v1.6"
    }

    private val systemPrompt = """
你是旺财（${MODEL_NAME} ${MODEL_VERSION}），一个专业的个人财务分析助手。
你的职责是基于用户的真实消费数据，提供温暖且专业的财务建议。

重要交互原则：
1. **排版要求**：严禁使用 Markdown 表格（|---|），因为在手机端展示非常不友好。请改用 "Emoji + 列表" 的形式展示分类和金额（例如：- 🍗 餐饮：¥100）。
2. **结构清晰**：使用 ## 标题引导段落，使用 **加粗** 强调核心结论。
3. **数据来源**：必须调用 `rule_engine.get_summary` 获取最近31天的精确数据。
4. **口吻**：亲切、像老朋友一样聊天，少用术语，多给具体可行的建议。
    """.trimIndent()

    private val messages = mutableListOf<Message>()

    fun clearContext() {
        Log.d(TAG, "clearContext")
        messages.clear()
    }

    fun thinkStream(
        userMessage: String,
        systemContext: String? = null
    ): Flow<String> = flow {
        Log.d(TAG, "thinkStream: $userMessage")
        
        if (messages.isEmpty()) {
            val content = buildString {
                append(systemPrompt)
                systemContext?.let { append("\n\n当前实时背景：\n$it") }
            }
            messages.add(Message(role = "system", content = content))
        }
        messages.add(Message(role = "user", content = userMessage))

        // 1. 同步处理工具逻辑
        var remainDepth = MAX_TOOL_CALLS
        while (remainDepth > 0) {
            val response = try {
                llmClient.chat(messages.toList(), toolRegistry.manifest)
            } catch (e: Exception) {
                throw e
            }

            if (response is LLMResponse.ToolCall) {
                messages.add(Message(role = "assistant", content = null, toolCalls = response.fullToolCalls))
                val result = toolRegistry.execute(response.toolName, response.arguments)
                messages.add(Message(role = "tool", content = result, toolCallId = response.id))
                remainDepth--
            } else {
                break
            }
        }

        // 2. 最终流式输出
        var fullText = ""
        llmClient.chatStream(messages.toList())
            .catch { e -> emit("\n[分析暂不可用: ${e.message}]") }
            .collect { delta ->
                fullText += delta
                emit(delta)
            }
        
        messages.add(Message(role = "assistant", content = fullText))
    }.flowOn(Dispatchers.IO)
}
