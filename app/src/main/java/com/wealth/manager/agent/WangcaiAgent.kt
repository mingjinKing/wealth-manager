package com.wealth.manager.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旺财 Micro Agent 核心类 - 进度反馈增强版
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
        private const val MODEL_VERSION = "v1.9.1"
    }

    private val systemPrompt = """
你是旺财（${MODEL_NAME} ${MODEL_VERSION}），一个专业的个人财务分析助手。
你的职责是基于用户的真实消费数据，提供温暖且专业的财务建议。

重要交互原则：
1. **排版要求**：严禁使用 Markdown 表格。请改用 "Emoji + 列表" 的形式展示分类和金额。
2. **高效分析**：先获取 `get_summary` 全量数据，然后立即生成分析报告，减少往返。
3. **言简意赅**：分析报告应简洁有力，直接指出核心问题和行动建议。避免客套话，单段文字控制在 80 字以内，整体报告不宜过长。
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
        if (messages.isEmpty()) {
            val content = buildString {
                append(systemPrompt)
                systemContext?.let { append("\n\n补充背景：\n$it") }
            }
            messages.add(Message(role = "system", content = content))
        }
        messages.add(Message(role = "user", content = userMessage))

        // 1. 工具调用循环
        var remainDepth = MAX_TOOL_CALLS
        while (remainDepth > 0) {
            val response = try {
                llmClient.chat(messages.toList(), toolRegistry.manifest)
            } catch (e: Exception) {
                Log.e(TAG, "Tool loop failed", e)
                throw e
            }

            if (response is LLMResponse.ToolCall) {
                // 根据将要执行的工具，给出用户友好的反馈
                val progressTip = try {
                    val args = JSONObject(response.arguments)
                    val op = args.optString("operation")
                    when {
                        response.toolName == "rule_engine" && op == "get_summary" -> "正在梳理您最近一个月的账单..."
                        response.toolName == "rule_engine" && op == "scale_analysis" -> "正在计算您的消费总额是否超标..."
                        response.toolName == "rule_engine" && op == "structure_analysis" -> "正在分析哪些类别占用了您的钱包..."
                        response.toolName == "context" -> "正在调取您的预算和理财目标..."
                        else -> "旺财正在深入分析数据细节..."
                    }
                } catch (e: Exception) {
                    "旺财正在处理数据..."
                }
                
                // 发送进度标记给 UI
                emit("[PROGRESS: $progressTip]")

                messages.add(Message(role = "assistant", content = null, toolCalls = response.fullToolCalls))
                val result = toolRegistry.execute(response.toolName, response.arguments)
                messages.add(Message(role = "tool", content = result, toolCallId = response.id))
                remainDepth--
            } else {
                // LLM 返回 Text，说明已经思考完成
                break
            }
        }

        // 2. 最终报告生成阶段
        emit("[PROGRESS: 正在为您撰写专属财务报告...]")
        
        var fullText = ""
        llmClient.chatStream(messages.toList())
            .catch { e -> 
                Log.e(TAG, "Stream failed", e)
                emit("\n[分析由于网络原因中断: ${e.message}]") 
            }
            .collect { delta ->
                fullText += delta
                emit(delta)
            }
        
        messages.add(Message(role = "assistant", content = fullText))
    }.flowOn(Dispatchers.IO)
}
