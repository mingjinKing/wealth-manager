package com.wealth.manager.tool

import com.wealth.manager.rules.FrequencyRule
import com.wealth.manager.rules.Insight
import com.wealth.manager.rules.WowRule
import com.wealth.manager.rules.InsightType
import com.wealth.manager.rules.ScaleRule
import com.wealth.manager.rules.StructureRule
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则引擎工具
 *
 * LLM 可以调用此工具获取规则计算结果。
 *
 * 支持的操作：
 * - scale_analysis: 规模分析（总支出是否过大）
 * - structure_analysis: 结构偏向分析（某分类是否占比过高）
 * - frequency_analysis: 频率分析（消费笔数是否过高）
 * - wow_moment: 哇时刻判断（是否触发）
 */
@Singleton
class RuleEngineTool @Inject constructor() : Tool {

    override val name = "rule_engine"
    override val description = """
        规则引擎工具，用于计算消费数据的各项指标和触发规则。
        支持：规模分析、结构偏向分析、频率分析、哇时刻判断。
        输入参数包含操作类型和所需数据。
    """.trimIndent()

    override val parametersSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["scale_analysis", "structure_analysis", "frequency_analysis", "wow_moment"],
                    "description": "规则操作类型"
                },
                "data": {
                    "type": "object",
                    "description": "操作所需数据，根据 operation 类型不同而不同"
                }
            },
            "required": ["operation", "data"]
        }
    """.trimIndent()

    override fun execute(arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val operation = json.getString("operation")
            val data = json.getJSONObject("data")

            val result = when (operation) {
                "scale_analysis" -> scaleAnalysis(data)
                "structure_analysis" -> structureAnalysis(data)
                "frequency_analysis" -> frequencyAnalysis(data)
                "wow_moment" -> wowMoment(data)
                else -> errorResult("Unknown operation: $operation")
            }

            result.toString()
        } catch (e: Exception) {
            errorResult("RuleEngineTool execution error: ${e.message}").toString()
        }
    }

    private fun scaleAnalysis(data: JSONObject): JSONObject {
        val total = data.optDouble("total", 0.0)
        val insight = ScaleRule.buildInsight(total)
        return insightToJson(insight)
    }

    private fun structureAnalysis(data: JSONObject): JSONObject {
        val topCategoryName = data.optString("topCategoryName", "未知")
        val topPercentage = data.optDouble("topPercentage", 0.0).toFloat()
        val threshold = data.optDouble("threshold", 0.4).toFloat()

        val isBiased = topPercentage > threshold
        val insight = if (isBiased) {
            StructureRule.buildInsight(topCategoryName, topPercentage)
        } else {
            Insight(
                type = InsightType.STRUCTURE_BIAS,
                message = "${topCategoryName}占比${(topPercentage * 100).toInt()}%，结构正常。",
                metadata = mapOf("categoryName" to topCategoryName, "percentage" to topPercentage, "biased" to false)
            )
        }
        return insightToJson(insight)
    }

    private fun frequencyAnalysis(data: JSONObject): JSONObject {
        val totalCount = data.optInt("totalCount", 0)
        val insight = FrequencyRule.buildInsight(totalCount)
        return insightToJson(insight)
    }

    private fun wowMoment(data: JSONObject): JSONObject {
        val savedAmount = data.optDouble("savedAmount", 0.0)
        val avgLast4Weeks = data.optDouble("avgLast4Weeks", 0.0)
        val isTriggered = WowRule.isTriggered(savedAmount, avgLast4Weeks)
        val insight = if (isTriggered) {
            WowRule.buildInsight(savedAmount)
        } else {
            Insight(
                type = InsightType.WOW_MOMENT,
                message = "本周未触发哇时刻（节省 ¥${String.format("%.0f", savedAmount)}，阈值 ¥${String.format("%.0f", avgLast4Weeks * 0.2)}）",
                metadata = mapOf("savedAmount" to savedAmount, "avgLast4Weeks" to avgLast4Weeks, "triggered" to false)
            )
        }
        return insightToJson(insight)
    }

    private fun insightToJson(insight: Insight): JSONObject {
        val json = JSONObject()
        json.put("type", insight.type.name)
        json.put("message", insight.message)

        val metadata = JSONObject()
        insight.metadata.forEach { (key, value) ->
            metadata.put(key, value)
        }
        json.put("metadata", metadata)

        return json
    }

    private fun errorResult(message: String): JSONObject {
        val json = JSONObject()
        json.put("error", message)
        return json
    }
}
