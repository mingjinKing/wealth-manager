package com.wealth.manager.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * LLM 客户端 - OpenAI 兼容接口（对接火山引擎 Ark）
 *
 * base_url: https://ark.cn-beijing.volces.com/api/coding/v3
 * model: deepseek-v3.2
 */
class LLMClient(
    private val apiKey: String,
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/coding/v3",
    private val model: String = "deepseek-v3.2"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 发送对话请求
     *
     * @param messages 对话消息列表
     * @param tools 工具清单（可选，支持 function calling）
     * @return LLM 回复文本
     */
    suspend fun chat(
        messages: List<Message>,
        tools: List<ToolManifest>? = null
    ): LLMResponse {
        val payload = buildJsonPayload(messages, tools)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw LLMException("Empty response from LLM")

        if (!response.isSuccessful) {
            throw LLMException("LLM request failed: ${response.code} - $body")
        }

        return parseResponse(body)
    }

    private fun buildJsonPayload(messages: List<Message>, tools: List<ToolManifest>?): String {
        val sb = StringBuilder()
        sb.append("""{"model":"$model","messages":[""")
        messages.forEachIndexed { index, msg ->
            sb.append("""{"role":"${msg.role}","content":"${msg.content.replace("\"", "\\\"")}"}""")
            if (index < messages.size - 1) sb.append(",")
        }
        sb.append("]")

        tools?.let { toolList ->
            if (toolList.isNotEmpty()) {
                sb.append(""","tools":[""")
                toolList.forEachIndexed { index, tool ->
                    sb.append("""{"type":"function","function":{"name":"${tool.name}","description":"${tool.description}","parameters":${tool.parameters}}}""")
                    if (index < toolList.size - 1) sb.append(",")
                }
                sb.append("]")
            }
        }

        sb.append("}")
        return sb.toString()
    }

    private fun parseResponse(body: String): LLMResponse {
        // 简单解析：判断是否有 tool_calls 或直接 content
        return try {
            val toolCallMatch = Regex(""""tool_calls"\s*:\s*\[(\{[^]]+\})]""").find(body)
            if (toolCallMatch != null) {
                val tc = toolCallMatch.groupValues[1]
                val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(tc)
                val argsMatch = Regex(""""arguments"\s*:\s*"(\{[^}]+\})"""").find(tc)
                if (nameMatch != null && argsMatch != null) {
                    LLMResponse.ToolCall(
                        toolName = nameMatch.groupValues[1],
                        arguments = argsMatch.groupValues[1]
                    )
                } else {
                    LLMResponse.Text(extractContent(body))
                }
            } else {
                LLMResponse.Text(extractContent(body))
            }
        } catch (e: Exception) {
            throw LLMException("Failed to parse LLM response: $body", e)
        }
    }

    private fun extractContent(body: String): String {
        val match = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(body)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
    }
}

/**
 * 对话消息
 */
data class Message(
    val role: String,  // "system" | "user" | "assistant" | "tool"
    val content: String
)

/**
 * 工具定义
 */
data class Tool(
    val name: String,
    val description: String,
    val parameters: String  // JSON Schema 字符串
)

/**
 * LLM 响应
 */
sealed class LLMResponse {
    data class Text(val content: String) : LLMResponse()
    data class ToolCall(val toolName: String, val arguments: String) : LLMResponse()
}

/**
 * LLM 异常
 */
class LLMException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
