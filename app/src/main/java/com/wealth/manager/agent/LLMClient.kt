package com.wealth.manager.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLM 客户端 - OpenAI 兼容接口（稳定网络流式版）
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
     * 同步/非流式对话（已自动切换到 IO 线程）
     */
    suspend fun chat(
        messages: List<Message>,
        tools: List<ToolManifest>? = null
    ): LLMResponse = withContext(Dispatchers.IO) {
        val payload = buildJsonPayload(messages, tools, stream = false)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw LLMException("Empty response")

        if (!response.isSuccessful) {
            throw LLMException("LLM error: ${response.code} - $body")
        }

        parseResponse(body)
    }

    /**
     * 流式对话（已自动切换到 IO 线程）
     */
    fun chatStream(
        messages: List<Message>,
        tools: List<ToolManifest>? = null
    ): Flow<String> = flow {
        val payload = buildJsonPayload(messages, tools, stream = true)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw LLMException("Stream HTTP failed: ${response.code}")
            
            val reader = response.body?.source() ?: return@flow
            while (!reader.exhausted()) {
                val line = reader.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                emit(content)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildJsonPayload(messages: List<Message>, tools: List<ToolManifest>?, stream: Boolean): String {
        val root = JSONObject()
        root.put("model", model)
        root.put("stream", stream)

        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            if (msg.content != null) msgObj.put("content", msg.content)
            if (msg.toolCallId != null) msgObj.put("tool_call_id", msg.toolCallId)
            if (msg.toolCalls != null) msgObj.put("tool_calls", msg.toolCalls)
            messagesArray.put(msgObj)
        }
        root.put("messages", messagesArray)

        tools?.takeIf { it.isNotEmpty() }?.let { toolList ->
            val toolsArray = JSONArray()
            toolList.forEach { tool ->
                val toolObj = JSONObject()
                toolObj.put("type", "function")
                val functionObj = JSONObject()
                functionObj.put("name", tool.name)
                functionObj.put("description", tool.description)
                functionObj.put("parameters", tool.parameters)
                toolObj.put("function", functionObj)
                toolsArray.put(toolObj)
            }
            root.put("tools", toolsArray)
        }

        return root.toString()
    }

    private fun parseResponse(body: String): LLMResponse {
        return try {
            val jsonBody = JSONObject(body)
            val choice = jsonBody.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")

            if (message.has("tool_calls")) {
                val toolCalls = message.getJSONArray("tool_calls")
                val firstCall = toolCalls.getJSONObject(0)
                val function = firstCall.getJSONObject("function")
                LLMResponse.ToolCall(
                    id = firstCall.getString("id"),
                    toolName = function.getString("name"),
                    arguments = function.getString("arguments"),
                    fullToolCalls = toolCalls
                )
            } else {
                LLMResponse.Text(message.optString("content", ""))
            }
        } catch (e: Exception) {
            throw LLMException("Parse error: $body", e)
        }
    }
}

data class Message(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: JSONArray? = null
)

sealed class LLMResponse {
    data class Text(val content: String) : LLMResponse()
    data class ToolCall(
        val id: String,
        val toolName: String,
        val arguments: String,
        val fullToolCalls: JSONArray
    ) : LLMResponse()
}

class LLMException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
