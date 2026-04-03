package com.wealth.manager.tool

import com.wealth.manager.agent.AgentContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上下文读写工具
 *
 * LLM 可以调用此工具读取和更新用户上下文。
 *
 * 支持的操作：
 * - read: 读取指定 key 的上下文值
 * - read_all: 读取全部上下文
 * - write: 更新指定 key 的上下文值
 * - write_batch: 批量更新上下文
 * - delete: 删除指定 key
 */
@Singleton
class ContextTool @Inject constructor(
    private val context: AgentContext
) : Tool {

    override val name = "context"
    override val description = """
        用户上下文读写工具。
        用于读取和更新用户的个人财务上下文（预算、目标、偏好、历史记录等）。
        读取操作不会修改数据，写入操作会同步保存到本地文件。
    """.trimIndent()

    override val parametersSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["read", "read_all", "write", "write_batch", "delete"],
                    "description": "操作类型"
                },
                "key": {
                    "type": "string",
                    "description": "上下文键，格式为点分隔路径，如 'budget.monthly'（operation 为 read/write/delete 时必填）"
                },
                "value": {
                    "type": "string",
                    "description": "要写入的值（operation 为 write 时必填）"
                },
                "updates": {
                    "type": "object",
                    "description": "批量更新的键值对（operation 为 write_batch 时必填）"
                }
            },
            "required": ["operation"]
        }
    """.trimIndent()

    override fun execute(arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val operation = json.getString("operation")

            val result = when (operation) {
                "read" -> {
                    val key = json.optString("key", "")
                    if (key.isEmpty()) {
                        return errorResult("key is required for read operation")
                    }
                    readContext(key)
                }
                "read_all" -> readAllContext()
                "write" -> {
                    val key = json.optString("key", "")
                    val value = json.optString("value", "")
                    if (key.isEmpty()) {
                        return errorResult("key is required for write operation")
                    }
                    writeContext(key, value)
                }
                "write_batch" -> {
                    val updates = json.optJSONObject("updates")
                    if (updates == null) {
                        return errorResult("updates is required for write_batch operation")
                    }
                    writeBatchContext(updates)
                }
                "delete" -> {
                    val key = json.optString("key", "")
                    if (key.isEmpty()) {
                        return errorResult("key is required for delete operation")
                    }
                    deleteContext(key)
                }
                else -> errorResult("Unknown operation: $operation")
            }

            result
        } catch (e: Exception) {
            errorResult("ContextTool execution error: ${e.message}").toString()
        }
    }

    private fun readContext(key: String): String {
        val value = context.read(key)
        val json = JSONObject()
        json.put("key", key)
        json.put("value", value)
        json.put("found", value.isNotEmpty())
        return json.toString()
    }

    private fun readAllContext(): String {
        val all = context.readAll()
        val json = JSONObject()
        json.put("context", JSONObject(all))
        return json.toString()
    }

    private fun writeContext(key: String, value: String): String {
        context.write(key, value)
        val json = JSONObject()
        json.put("success", true)
        json.put("key", key)
        json.put("value", value)
        return json.toString()
    }

    private fun writeBatchContext(updates: JSONObject): String {
        val map = mutableMapOf<String, String>()
        updates.keys().forEach { key ->
            map[key] = updates.get(key).toString()
        }
        context.writeBatch(map)
        val json = JSONObject()
        json.put("success", true)
        json.put("updated_count", map.size)
        return json.toString()
    }

    private fun deleteContext(key: String): String {
        context.delete(key)
        val json = JSONObject()
        json.put("success", true)
        json.put("key", key)
        return json.toString()
    }

    private fun errorResult(message: String): String {
        val json = JSONObject()
        json.put("error", message)
        return json.toString()
    }
}
