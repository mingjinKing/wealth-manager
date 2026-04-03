package com.wealth.manager.agent

import com.wealth.manager.tool.ContextTool
import com.wealth.manager.tool.FileTool
import com.wealth.manager.tool.RuleEngineTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旺财 Micro Agent 核心类
 *
 * 负责：
 * 1. 管理对话上下文（messages）
 * 2. 调用 LLM 并处理工具调用循环
 * 3. 工具执行结果的返回
 *
 * 使用方式：
 * ```kotlin
 * val agent = WangcaiAgent(llmClient, toolRegistry, agentContext)
 * val reply = agent.think("这个月我花了多少钱？")
 * ```
 */
@Singleton
class WangcaiAgent @Inject constructor(
    private val llmClient: LLMClient,
    private val toolRegistry: ToolRegistry,
    private val agentContext: AgentContext
) {

    companion object {
        private const val MAX_TOOL_CALLS = 3  // 限制最大工具调用深度
        private const val MODEL_NAME = "wangcai"
        private const val MODEL_VERSION = "v1.0"
    }

    /**
     * 旺财的系统提示（人设）
     */
    private val systemPrompt = """
你是旺财（${MODEL_NAME} ${MODEL_VERSION}），一个专业的个人财务分析助手。

你的职责是基于用户的消费数据，提供个性化的财务洞察和建议。

你有以下工具可以使用：
- rule_engine: 规则引擎，计算消费指标和触发规则
- context: 用户上下文读写，存储用户偏好、预算、目标等
- file: 本地文件读写，读取账单数据等

重要原则：
1. 先理解用户问题，决定是否需要调用工具
2. 如果需要计算或获取数据，优先使用工具
3. 解读要结合用户上下文（预算、目标、历史）才有意义
4. 回答要简洁、有洞察、接地气
5. 不要臆测数据，所有数据必须来自工具调用
    """.trimIndent()

    /**
     * 短期对话上下文（内存）
     */
    private val messages = mutableListOf<Message>()

    /**
     * 发起一次思考（对话）
     *
     * @param userMessage 用户输入
     * @param systemContext 附加的系统上下文（可选，如账单数据摘要）
     * @return LLM 回复文本
     */
    suspend fun think(
        userMessage: String,
        systemContext: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 构建系统消息（含旺财人设 + 附加上下文）
        buildSystemMessage(systemContext)?.let { messages.add(it) }

        // 添加用户消息
        messages.add(Message(role = "user", content = userMessage))

        // 调用 LLM（单轮，支持工具调用）
        val response = llmClient.chat(
            messages = messages.toList(),
            tools = toolRegistry.manifest
        )

        // 处理响应
        when (response) {
            is LLMResponse.Text -> {
                // 直接回复，添加到上下文并返回
                messages.add(Message(role = "assistant", content = response.content))
                response.content
            }

            is LLMResponse.ToolCall -> {
                // 工具调用，执行并循环
                executeToolCalls(response, maxDepth = MAX_TOOL_CALLS)
            }
        }
    }

    /**
     * 清除对话上下文
     */
    fun clearContext() {
        messages.clear()
    }

    /**
     * 执行工具调用循环
     *
     * LLM 返回 tool_call → 执行工具 → 把结果塞回 messages → 再次调用 LLM
     * 直到 LLM 输出文本或达到最大深度
     */
    private suspend fun executeToolCalls(
        toolCall: LLMResponse.ToolCall,
        maxDepth: Int
    ): String {
        if (maxDepth <= 0) {
            return "（已达最大推理深度，请简化问题）"
        }

        // 执行工具
        val toolResult = toolRegistry.execute(toolCall.toolName, toolCall.arguments)

        // 记录 assistant 的 tool_call 消息
        messages.add(Message(
            role = "assistant",
            content = """{"tool_calls":[{"name":"${toolCall.toolName}","arguments":${toolCall.arguments}}]}"""
        ))

        // 记录工具执行结果
        messages.add(Message(
            role = "tool",
            content = toolResult
        ))

        // 再次调用 LLM
        val nextResponse = llmClient.chat(
            messages = messages.toList(),
            tools = toolRegistry.manifest
        )

        return when (nextResponse) {
            is LLMResponse.Text -> {
                messages.add(Message(role = "assistant", content = nextResponse.content))
                nextResponse.content
            }

            is LLMResponse.ToolCall -> {
                // 继续循环（深度减一）
                executeToolCalls(nextResponse, maxDepth - 1)
            }
        }
    }

    /**
     * 构建系统消息
     */
    private fun buildSystemContext(systemContext: String?): String {
        return buildString {
            append(systemPrompt)
            systemContext?.let {
                append("\n\n附加上下文：\n")
                append(it)
            }
        }
    }

    private fun buildSystemMessage(context: String?): Message? {
        val content = buildSystemContext(context)
        return if (content.isNotEmpty()) Message(role = "system", content = content) else null
    }
}
