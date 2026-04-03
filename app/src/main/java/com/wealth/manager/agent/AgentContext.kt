package com.wealth.manager.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 上下文管理器
 *
 * 负责用户上下文的内存缓存和文件持久化。
 * 使用时读内存，内存中没有则从文件加载。
 * 更新时同步更新内存和文件。
 */
@Singleton
class AgentContext @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CONTEXT_FILE = "wangcai_context.json"
    }

    // 内存中的上下文
    private var memory: JSONObject = JSONObject()

    init {
        loadFromFile()
    }

    /**
     * 从文件加载上下文到内存
     */
    private fun loadFromFile() {
        try {
            val file = File(context.filesDir, CONTEXT_FILE)
            if (file.exists()) {
                val content = file.readText()
                memory = JSONObject(content)
            }
        } catch (e: Exception) {
            // 文件不存在或损坏，使用空上下文
            memory = JSONObject()
        }
    }

    /**
     * 保存内存上下文到文件
     */
    private fun saveToFile() {
        try {
            val file = File(context.filesDir, CONTEXT_FILE)
            file.writeText(memory.toString(2))
        } catch (e: Exception) {
            // 写入失败，忽略
        }
    }

    /**
     * 读取指定 key 的上下文值
     *
     * @param key JSON path，如 "budget.monthly"
     * @return 值字符串，不存在返回空字符串
     */
    fun read(key: String): String {
        // 内存为空时尝试加载
        if (memory.length() == 0) {
            loadFromFile()
        }

        return try {
            val parts = key.split(".")
            var current: Any = memory
            for (part in parts) {
                if (current is JSONObject) {
                    current = current.get(part)
                } else {
                    return ""
                }
            }
            current.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 读取全部上下文为 JSON 字符串（供 LLM 使用）
     */
    fun readAll(): String {
        if (memory.length() == 0) {
            loadFromFile()
        }
        return memory.toString()
    }

    /**
     * 更新上下文（同步写内存 + 文件）
     *
     * @param key JSON path，如 "budget.monthly"
     * @param value 新值
     */
    fun write(key: String, value: String) {
        // 内存为空时尝试加载
        if (memory.length() == 0) {
            loadFromFile()
        }

        try {
            val parts = key.split(".")
            var current: JSONObject = memory

            // 逐层创建/导航 JSON 对象
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                if (!current.has(part)) {
                    current.put(part, JSONObject())
                }
                current = current.getJSONObject(part)
            }

            // 设置最终值
            val lastKey = parts.last()
            // 尝试作为数字解析，失败则作为字符串
            try {
                current.put(lastKey, value.toDouble())
            } catch (e: Exception) {
                current.put(lastKey, value)
            }

            // 同步写文件
            saveToFile()
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }

    /**
     * 批量更新上下文
     *
     * @param updates Map<key, value>
     */
    fun writeBatch(updates: Map<String, String>) {
        updates.forEach { (key, value) ->
            write(key, value)
        }
    }

    /**
     * 删除指定 key
     */
    fun delete(key: String) {
        if (memory.length() == 0) {
            loadFromFile()
        }

        try {
            val parts = key.split(".")
            var current: JSONObject = memory

            for (i in 0 until parts.size - 1) {
                if (!current.has(parts[i])) return
                current = current.getJSONObject(parts[i])
            }

            current.remove(parts.last())
            saveToFile()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
