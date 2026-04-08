package com.wealth.manager.tool

import com.wealth.manager.agent.AgentContext
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.rules.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则引擎工具 - 增强版 (支持年月查询，防止 AI 时间戳计算错误)
 */
@Singleton
class RuleEngineTool @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val agentContext: AgentContext
) : Tool {

    override val name = "rule_engine"
    override val description = """
        核心财务数据工具。
        - get_summary: 获取指定范围的账单统计。支持通过 year 和 month 准确定位。
        - scale_analysis: 消费规模分析。
        - structure_analysis: 消费结构分析。
    """.trimIndent()

    override val parametersSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["get_summary", "scale_analysis", "structure_analysis", "frequency_analysis", "record_user_explanation"]
                },
                "year": {
                    "type": "integer",
                    "description": "年份，如 2026"
                },
                "month": {
                    "type": "integer",
                    "description": "月份，1-12"
                },
                "startTime": {
                    "type": "integer",
                    "description": "开始时间戳（毫秒），若有 year/month 则优先使用它们"
                },
                "endTime": {
                    "type": "integer",
                    "description": "结束时间戳（毫秒）"
                },
                "explanation": {
                    "type": "string"
                }
            },
            "required": ["operation"]
        }
    """.trimIndent()

    override fun execute(arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val operation = json.getString("operation")
            
            // 优先处理年月参数
            val (computedStart, computedEnd) = if (json.has("year") && json.has("month")) {
                val y = json.getInt("year")
                val m = json.getInt("month")
                calculateRange(y, m)
            } else {
                val s = if (json.has("startTime")) json.getLong("startTime") else null
                val e = if (json.has("endTime")) json.getLong("endTime") else null
                Pair(s, e)
            }

            val result = when (operation) {
                "get_summary" -> getSummary(computedStart, computedEnd)
                "scale_analysis" -> scaleAnalysis(null, computedStart, computedEnd)
                "structure_analysis" -> structureAnalysis(null, computedStart, computedEnd)
                "record_user_explanation" -> recordUserExplanation(json.optString("explanation", ""))
                else -> errorResult("Unknown operation")
            }
            result.toString()
        } catch (e: Exception) {
            errorResult("Error: ${e.message}").toString()
        }
    }

    private fun calculateRange(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    private fun recordUserExplanation(explanation: String): JSONObject {
        if (explanation.isNotBlank()) {
            val existing = agentContext.read("user_memories")
            val memoriesArray = if (existing.isEmpty()) JSONArray() else JSONArray(existing)
            val entry = JSONObject().apply {
                put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()))
                put("content", explanation)
            }
            memoriesArray.put(entry)
            agentContext.write("user_memories", memoriesArray.toString())
        }
        return JSONObject().put("status", "success")
    }

    private fun getSummary(customStart: Long?, customEnd: Long?): JSONObject {
        val start: Long
        val end: Long

        if (customStart != null && customEnd != null) {
            start = customStart
            end = customEnd
        } else {
            val calendar = Calendar.getInstance()
            end = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -29)
            start = calendar.timeInMillis
        }

        val (expenses, categories) = runBlocking {
            val ex = expenseDao.getExpensesByDateRange(start, end).first()
            val cat = categoryDao.getAllCategories().first()
            ex to cat
        }

        val catNameMap = categories.associate { it.id to "${it.icon} ${it.name}" }
        val total = expenses.sumOf { it.amount }
        
        val categoryStats = expenses.groupBy { it.categoryId }
            .mapValues { it.value.sumOf { m -> m.amount } }
            .toList().sortedByDescending { it.second }

        val categoryArray = JSONArray()
        categoryStats.take(10).forEach { (catId, amount) ->
            categoryArray.put(JSONObject().apply {
                put("category", catNameMap[catId] ?: "未知")
                put("amount", amount)
                put("percentage", if (total > 0) amount / total else 0.0)
            })
        }

        return JSONObject().apply {
            put("period_desc", if (customStart != null) "custom_range" else "last_30_days")
            put("total_expense", total)
            put("transaction_count", expenses.size)
            put("top_categories", categoryArray)
            put("status", "success")
        }
    }

    private fun scaleAnalysis(data: JSONObject?, start: Long?, end: Long?): JSONObject {
        val total = getSummaryTotal(start, end)
        return insightToJson(ScaleRule.buildInsight(total))
    }

    private fun structureAnalysis(data: JSONObject?, start: Long?, end: Long?): JSONObject {
        val summary = getSummary(start, end)
        val tops = summary.optJSONArray("top_categories")
        if (tops == null || tops.length() == 0) return JSONObject().put("message", "无数据")
        
        val first = tops.getJSONObject(0)
        val name = first.getString("category")
        val percentage = first.getDouble("percentage").toFloat()
        
        return insightToJson(StructureRule.buildInsight(name, percentage))
    }

    private fun getSummaryTotal(start: Long?, end: Long?): Double = getSummary(start, end).optDouble("total_expense", 0.0)

    private fun insightToJson(insight: Insight): JSONObject {
        val json = JSONObject()
        json.put("type", insight.type.name)
        json.put("message", insight.message)
        val metadata = JSONObject()
        insight.metadata.forEach { (k, v) -> metadata.put(k, v) }
        json.put("metadata", metadata)
        return json
    }

    private fun errorResult(message: String): JSONObject = JSONObject().put("error", message)
}
