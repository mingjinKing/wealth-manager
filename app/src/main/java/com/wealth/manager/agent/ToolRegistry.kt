package com.wealth.manager.agent

import com.wealth.manager.tool.ContextTool
import com.wealth.manager.tool.FileTool
import com.wealth.manager.tool.RuleEngineTool
import com.wealth.manager.tool.Tool
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具注册表
 *
 * 管理所有可被 LLM 调用的工具。
 * 提供工具清单（manifest）和工具执行入口。
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val ruleEngineTool: RuleEngineTool,
    private val contextTool: ContextTool,
    private val fileTool: FileTool
) {

    /**
     * 所有工具列表（用于 LLM function calling）
     */
    val tools: List<Tool> = listOf(
        ruleEngineTool,
        contextTool,
        fileTool
    )

    /**
     * 获取工具清单（用于发送给 LLM）
     */
    val manifest: List<ToolManifest> = tools.map { tool ->
        ToolManifest(
            name = tool.name,
            description = tool.description,
            parameters = JSONObject(tool.parametersSchema)
        )
    }

    /**
     * 执行工具调用
     *
     * @param toolName 工具名称
     * @param arguments JSON 格式的参数
     * @return 执行结果字符串
     */
    fun execute(toolName: String, arguments: String): String {
        val tool = tools.find { it.name == toolName }
            ?: return """{"error":"Tool not found: $toolName"}"""

        return try {
            tool.execute(arguments)
        } catch (e: Exception) {
            """{"error":"Tool execution failed: ${e.message}"}"""
        }
    }

    /**
     * 获取指定工具
     */
    fun getTool(name: String): Tool? = tools.find { it.name == name }
}

/**
 * 工具清单（LLM 可见的描述）
 */
data class ToolManifest(
    val name: String,
    val description: String,
    val parameters: JSONObject
)
