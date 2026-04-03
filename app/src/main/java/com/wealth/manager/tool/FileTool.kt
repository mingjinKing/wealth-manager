package com.wealth.manager.tool

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件读写工具
 *
 * LLM 可以调用此工具读取和写入本地文件。
 *
 * 支持的操作：
 * - read: 读取指定文件内容
 * - write: 写入内容到文件
 * - list: 列出目录下的文件
 */
@Singleton
class FileTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {

    override val name = "file"
    override val description = """
        本地文件读写工具。
        用于读取或写入应用私有目录下的文件。
        文件路径相对于应用私有目录。
    """.trimIndent()

    override val parametersSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["read", "write", "list"],
                    "description": "操作类型"
                },
                "path": {
                    "type": "string",
                    "description": "文件路径（相对于应用私有目录）"
                },
                "content": {
                    "type": "string",
                    "description": "要写入的内容（operation 为 write 时必填）"
                }
            },
            "required": ["operation", "path"]
        }
    """.trimIndent()

    override fun execute(arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val operation = json.getString("operation")
            val path = json.getString("path")

            val result = when (operation) {
                "read" -> readFile(path)
                "write" -> {
                    val content = json.optString("content", "")
                    writeFile(path, content)
                }
                "list" -> listFiles(path)
                else -> errorResult("Unknown operation: $operation")
            }

            result
        } catch (e: Exception) {
            errorResult("FileTool execution error: ${e.message}").toString()
        }
    }

    private fun resolvePath(relativePath: String): File {
        return File(context.filesDir, relativePath)
    }

    private fun readFile(path: String): String {
        val file = resolvePath(path)
        if (!file.exists()) {
            return errorResult("File not found: $path")
        }

        return try {
            val content = file.readText()
            val json = JSONObject()
            json.put("path", path)
            json.put("content", content)
            json.put("size", content.length)
            json.toString()
        } catch (e: Exception) {
            errorResult("Failed to read file: ${e.message}")
        }
    }

    private fun writeFile(path: String, content: String): String {
        val file = resolvePath(path)

        // 确保父目录存在
        file.parentFile?.mkdirs()

        return try {
            file.writeText(content)
            val json = JSONObject()
            json.put("success", true)
            json.put("path", path)
            json.put("size", content.length)
            json.toString()
        } catch (e: Exception) {
            errorResult("Failed to write file: ${e.message}")
        }
    }

    private fun listFiles(path: String): String {
        val dir = resolvePath(path)
        if (!dir.exists() || !dir.isDirectory) {
            return errorResult("Directory not found: $path")
        }

        return try {
            val files = dir.listFiles() ?: emptyArray()
            val json = JSONObject()
            val fileList = files.map { file ->
                JSONObject().apply {
                    put("name", file.name)
                    put("isDirectory", file.isDirectory)
                    put("size", if (file.isFile) file.length() else 0)
                }
            }
            json.put("path", path)
            json.put("files", fileList)
            json.put("count", files.size)
            json.toString()
        } catch (e: Exception) {
            errorResult("Failed to list directory: ${e.message}")
        }
    }

    private fun errorResult(message: String): String {
        val json = JSONObject()
        json.put("error", message)
        return json.toString()
    }
}
