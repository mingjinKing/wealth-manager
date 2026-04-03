package com.wealth.manager.tool

/**
 * 工具接口
 *
 * 所有工具（规则引擎、上下文读写、文件读写）都实现此接口。
 */
interface Tool {
    val name: String
    val description: String
    val parametersSchema: String

    /**
     * 执行工具
     *
     * @param arguments JSON 格式的参数（来自 LLM）
     * @return 执行结果字符串
     */
    fun execute(arguments: String): String
}
