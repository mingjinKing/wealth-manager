package com.wealth.manager.agent

import android.util.Log
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
 * LLM 客户端 - 稳定性增强版 (支持 OkHttpClient 注入)
 */
class LLMClient(
    private val apiKey: String,
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/coding/v3",
    private val model: String = "deepseek-v3.2",
    private val okHttpClient: OkHttpClient? = null
) {

    private val TAG = "LLMClient"

    // 如果没注入，则创建一个默认的
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(
        messages: List<Message>,
        tools: List<ToolManifest>? = null
    ): LLMResponse = withContext(Dispatchers.IO) {
        val payload = buildJsonPayload(messages, tools, stream = false)
        Log.d(TAG, "Chat request payload: $payload")
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw LLMException("Empty response")
            Log.d(TAG, "Chat response body: $body")
            
            if (!response.isSuccessful) throw LLMException("LLM error: ${response.code} - $body")
            parseResponse(body)
        }
    }

    fun chatStream(
        messages: List<Message>,
        tools: List<ToolManifest>? = null
    ): Flow<String> = flow {
        val payload = buildJsonPayload(messages, tools, stream = true)
        Log.d(TAG, "Stream request payload: $payload")
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Stream HTTP failed: ${response.code}, body: $errorBody")
                throw LLMException("Stream HTTP failed: ${response.code}")
            }
            
            val source = response.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        Log.d(TAG, "Stream [DONE]")
                        break
                    }
                    
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            
                            // 优先解析正文内容
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                emit(content)
                            } else {
                                // 兼容某些模型可能把内容放在 reasoning_content 中
                                val reasoning = delta?.optString("reasoning_content", "") ?: ""
                                if (reasoning.isNotEmpty()) {
                                    // 可以在这里特殊处理思考过程，目前暂不输出到 UI 以保持简洁
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse stream data: $data", e)
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
        val jsonBody = JSONObject(body)
        val choice = jsonBody.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")
        val content = message.optString("content", "")

        return if (message.has("tool_calls")) {
            val toolCalls = message.getJSONArray("tool_calls")
            val firstCall = toolCalls.getJSONObject(0)
            val function = firstCall.getJSONObject("function")
            LLMResponse.ToolCall(
                id = firstCall.getString("id"),
                toolName = function.getString("name"),
                arguments = function.getString("arguments"),
                fullToolCalls = toolCalls,
                content = content // 保存中间引导语
            )
        } else {
            LLMResponse.Text(content)
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
        val fullToolCalls: JSONArray,
        val content: String? = null // 新增可选内容字段
    ) : LLMResponse()
}

class LLMException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
